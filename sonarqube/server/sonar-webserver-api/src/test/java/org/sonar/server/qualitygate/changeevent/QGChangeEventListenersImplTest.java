/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.qualitygate.changeevent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.RandomStringUtils;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.sonar.api.issue.Issue;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.component.BranchDto;
import org.sonar.server.qualitygate.changeevent.QGChangeEventListener.ChangedIssue;
import org.sonar.server.qualitygate.changeevent.QGChangeEventListenersImpl.ChangedIssueImpl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class QGChangeEventListenersImplTest {
  @Rule
  public LogTester logTester = new LogTester();

  private QGChangeEventListener listener1 = mock(QGChangeEventListener.class);
  private QGChangeEventListener listener2 = mock(QGChangeEventListener.class);
  private QGChangeEventListener listener3 = mock(QGChangeEventListener.class);
  private List<QGChangeEventListener> listeners = Arrays.asList(listener1, listener2, listener3);

  private String project1Uuid = RandomStringUtils.randomAlphabetic(6);
  private BranchDto project1 = newBranchDto(project1Uuid);
  private DefaultIssue component1Issue = newDefaultIssue(project1Uuid);
  private List<DefaultIssue> oneIssueOnComponent1 = singletonList(component1Issue);
  private QGChangeEvent component1QGChangeEvent = newQGChangeEvent(project1);

  private InOrder inOrder = Mockito.inOrder(listener1, listener2, listener3);

  private QGChangeEventListenersImpl underTest = new QGChangeEventListenersImpl(new QGChangeEventListener[] {listener1, listener2, listener3});

  @Test
  public void broadcastOnIssueChange_has_no_effect_when_issues_are_empty() {
    underTest.broadcastOnIssueChange(emptyList(), singletonList(component1QGChangeEvent));

    verifyZeroInteractions(listener1, listener2, listener3);
  }

  @Test
  public void broadcastOnIssueChange_has_no_effect_when_no_changeEvent() {
    underTest.broadcastOnIssueChange(oneIssueOnComponent1, emptySet());

    verifyZeroInteractions(listener1, listener2, listener3);
  }

  @Test
  public void broadcastOnIssueChange_passes_same_arguments_to_all_listeners_in_order_of_addition_to_constructor() {
    underTest.broadcastOnIssueChange(oneIssueOnComponent1, singletonList(component1QGChangeEvent));

    ArgumentCaptor<Set<ChangedIssue>> changedIssuesCaptor = newSetCaptor();
    inOrder.verify(listener1).onIssueChanges(same(component1QGChangeEvent), changedIssuesCaptor.capture());
    Set<ChangedIssue> changedIssues = changedIssuesCaptor.getValue();
    inOrder.verify(listener2).onIssueChanges(same(component1QGChangeEvent), same(changedIssues));
    inOrder.verify(listener3).onIssueChanges(same(component1QGChangeEvent), same(changedIssues));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void broadcastOnIssueChange_calls_all_listeners_even_if_one_throws_an_exception() {
    QGChangeEventListener failingListener = new QGChangeEventListener[] {listener1, listener2, listener3}[new Random().nextInt(3)];
    doThrow(new RuntimeException("Faking an exception thrown by onChanges"))
      .when(failingListener)
      .onIssueChanges(any(), any());

    underTest.broadcastOnIssueChange(oneIssueOnComponent1, singletonList(component1QGChangeEvent));

    ArgumentCaptor<Set<ChangedIssue>> changedIssuesCaptor = newSetCaptor();
    inOrder.verify(listener1).onIssueChanges(same(component1QGChangeEvent), changedIssuesCaptor.capture());
    Set<ChangedIssue> changedIssues = changedIssuesCaptor.getValue();
    inOrder.verify(listener2).onIssueChanges(same(component1QGChangeEvent), same(changedIssues));
    inOrder.verify(listener3).onIssueChanges(same(component1QGChangeEvent), same(changedIssues));
    inOrder.verifyNoMoreInteractions();
    assertThat(logTester.logs()).hasSize(4);
    assertThat(logTester.logs(LoggerLevel.WARN)).hasSize(1);
  }

  @Test
  public void broadcastOnIssueChange_stops_calling_listeners_when_one_throws_an_ERROR() {
    doThrow(new Error("Faking an error thrown by a listener"))
      .when(listener2)
      .onIssueChanges(any(), any());

    underTest.broadcastOnIssueChange(oneIssueOnComponent1, singletonList(component1QGChangeEvent));

    ArgumentCaptor<Set<ChangedIssue>> changedIssuesCaptor = newSetCaptor();
    inOrder.verify(listener1).onIssueChanges(same(component1QGChangeEvent), changedIssuesCaptor.capture());
    Set<ChangedIssue> changedIssues = changedIssuesCaptor.getValue();
    inOrder.verify(listener2).onIssueChanges(same(component1QGChangeEvent), same(changedIssues));
    inOrder.verifyNoMoreInteractions();
    assertThat(logTester.logs()).hasSize(3);
    assertThat(logTester.logs(LoggerLevel.WARN)).hasSize(1);
  }

  @Test
  public void broadcastOnIssueChange_logs_each_listener_call_at_TRACE_level() {
    underTest.broadcastOnIssueChange(oneIssueOnComponent1, singletonList(component1QGChangeEvent));

    assertThat(logTester.logs()).hasSize(3);
    List<String> traceLogs = logTester.logs(LoggerLevel.TRACE);
    assertThat(traceLogs).hasSize(3)
      .containsOnly(
        "calling onChange() on listener " + listener1.getClass().getName() + " for events " + component1QGChangeEvent.toString() + "...",
        "calling onChange() on listener " + listener2.getClass().getName() + " for events " + component1QGChangeEvent.toString() + "...",
        "calling onChange() on listener " + listener3.getClass().getName() + " for events " + component1QGChangeEvent.toString() + "...");
  }

  @Test
  public void broadcastOnIssueChange_passes_immutable_set_of_ChangedIssues() {
    QGChangeEventListenersImpl underTest = new QGChangeEventListenersImpl(new QGChangeEventListener[] {listener1});

    underTest.broadcastOnIssueChange(oneIssueOnComponent1, singletonList(component1QGChangeEvent));

    ArgumentCaptor<Set<ChangedIssue>> changedIssuesCaptor = newSetCaptor();
    inOrder.verify(listener1).onIssueChanges(same(component1QGChangeEvent), changedIssuesCaptor.capture());
    assertThat(changedIssuesCaptor.getValue()).isInstanceOf(ImmutableSet.class);
  }

  @Test
  public void broadcastOnIssueChange_has_no_effect_when_no_listener() {
    QGChangeEventListenersImpl underTest = new QGChangeEventListenersImpl();

    underTest.broadcastOnIssueChange(oneIssueOnComponent1, singletonList(component1QGChangeEvent));

    verifyZeroInteractions(listener1, listener2, listener3);
  }

  @Test
  public void broadcastOnIssueChange_calls_listener_for_each_component_uuid_with_at_least_one_QGChangeEvent() {
    // branch has multiple issues
    BranchDto component2 = newBranchDto(project1Uuid + "2");
    DefaultIssue[] component2Issues = {newDefaultIssue(component2.getUuid()), newDefaultIssue(component2.getUuid())};
    QGChangeEvent component2QGChangeEvent = newQGChangeEvent(component2);

    // branch 3 has multiple QGChangeEvent and only one issue
    BranchDto component3 = newBranchDto(project1Uuid + "3");
    DefaultIssue component3Issue = newDefaultIssue(component3.getUuid());
    QGChangeEvent[] component3QGChangeEvents = {newQGChangeEvent(component3), newQGChangeEvent(component3)};

    // branch 4 has multiple QGChangeEvent and multiples issues
    BranchDto component4 = newBranchDto(project1Uuid + "4");
    DefaultIssue[] component4Issues = {newDefaultIssue(component4.getUuid()), newDefaultIssue(component4.getUuid())};
    QGChangeEvent[] component4QGChangeEvents = {newQGChangeEvent(component4), newQGChangeEvent(component4)};

    // branch 5 has no QGChangeEvent but one issue
    BranchDto component5 = newBranchDto(project1Uuid + "5");
    DefaultIssue component5Issue = newDefaultIssue(component5.getUuid());

    List<DefaultIssue> issues = Stream.of(
      Stream.of(component1Issue),
      Arrays.stream(component2Issues),
      Stream.of(component3Issue),
      Arrays.stream(component4Issues),
      Stream.of(component5Issue))
      .flatMap(s -> s)
      .collect(Collectors.toList());

    List<DefaultIssue> changedIssues = randomizedList(issues);
    List<QGChangeEvent> qgChangeEvents = Stream.of(
      Stream.of(component1QGChangeEvent),
      Stream.of(component2QGChangeEvent),
      Arrays.stream(component3QGChangeEvents),
      Arrays.stream(component4QGChangeEvents))
      .flatMap(s -> s)
      .collect(Collectors.toList());

    underTest.broadcastOnIssueChange(changedIssues, randomizedList(qgChangeEvents));

    listeners.forEach(listener -> {
      verifyListenerCalled(listener, component1QGChangeEvent, component1Issue);
      verifyListenerCalled(listener, component2QGChangeEvent, component2Issues);
      Arrays.stream(component3QGChangeEvents)
        .forEach(component3QGChangeEvent -> verifyListenerCalled(listener, component3QGChangeEvent, component3Issue));
      Arrays.stream(component4QGChangeEvents)
        .forEach(component4QGChangeEvent -> verifyListenerCalled(listener, component4QGChangeEvent, component4Issues));
    });
    verifyNoMoreInteractions(listener1, listener2, listener3);
  }

  @Test
  public void isNotClosed_returns_true_if_issue_in_one_of_opened_states() {
    DefaultIssue defaultIssue = new DefaultIssue();
    defaultIssue.setStatus(Issue.STATUS_REOPENED);
    defaultIssue.setKey("abc");
    defaultIssue.setType(RuleType.BUG);
    defaultIssue.setSeverity("BLOCKER");

    ChangedIssue changedIssue = new ChangedIssueImpl(defaultIssue);

    assertThat(changedIssue.isNotClosed()).isTrue();
  }

  @Test
  public void isNotClosed_returns_false_if_issue_in_one_of_closed_states() {
    DefaultIssue defaultIssue = new DefaultIssue();
    defaultIssue.setStatus(Issue.STATUS_CONFIRMED);
    defaultIssue.setKey("abc");
    defaultIssue.setType(RuleType.BUG);
    defaultIssue.setSeverity("BLOCKER");

    ChangedIssue changedIssue = new ChangedIssueImpl(defaultIssue);

    assertThat(changedIssue.isNotClosed()).isFalse();
  }

  @Test
  public void test_status_mapping() {
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_OPEN))).isEqualTo(QGChangeEventListener.Status.OPEN);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_REOPENED))).isEqualTo(QGChangeEventListener.Status.REOPENED);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_CONFIRMED))).isEqualTo(QGChangeEventListener.Status.CONFIRMED);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_RESOLVED).setResolution(Issue.RESOLUTION_FALSE_POSITIVE)))
      .isEqualTo(QGChangeEventListener.Status.RESOLVED_FP);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_RESOLVED).setResolution(Issue.RESOLUTION_WONT_FIX)))
      .isEqualTo(QGChangeEventListener.Status.RESOLVED_WF);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_RESOLVED).setResolution(Issue.RESOLUTION_FIXED)))
      .isEqualTo(QGChangeEventListener.Status.RESOLVED_FIXED);
    try {
      ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_CLOSED));
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Unexpected status: CLOSED");
    }
    try {
      ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_RESOLVED));
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("A resolved issue should have a resolution");
    }
    try {
      ChangedIssueImpl.statusOf(new DefaultIssue().setStatus(Issue.STATUS_RESOLVED).setResolution(Issue.RESOLUTION_REMOVED));
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Unexpected resolution for a resolved issue: REMOVED");
    }
  }

  @Test
  public void test_status_mapping_on_security_hotspots() {
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setType(RuleType.SECURITY_HOTSPOT).setStatus(Issue.STATUS_TO_REVIEW)))
      .isEqualTo(QGChangeEventListener.Status.TO_REVIEW);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setType(RuleType.SECURITY_HOTSPOT).setStatus(Issue.STATUS_IN_REVIEW)))
      .isEqualTo(QGChangeEventListener.Status.IN_REVIEW);
    assertThat(ChangedIssueImpl.statusOf(new DefaultIssue().setType(RuleType.SECURITY_HOTSPOT).setStatus(Issue.STATUS_REVIEWED)))
      .isEqualTo(QGChangeEventListener.Status.REVIEWED);
  }

  private void verifyListenerCalled(QGChangeEventListener listener, QGChangeEvent changeEvent, DefaultIssue... issues) {
    ArgumentCaptor<Set<ChangedIssue>> changedIssuesCaptor = newSetCaptor();
    verify(listener).onIssueChanges(same(changeEvent), changedIssuesCaptor.capture());
    Set<ChangedIssue> changedIssues = changedIssuesCaptor.getValue();
    Tuple[] expected = Arrays.stream(issues)
      .map(issue -> tuple(issue.key(), ChangedIssueImpl.statusOf(issue), issue.type()))
      .toArray(Tuple[]::new);
    assertThat(changedIssues)
      .hasSize(issues.length)
      .extracting(ChangedIssue::getKey, ChangedIssue::getStatus, ChangedIssue::getType)
      .containsOnly(expected);
  }

  private static final String[] POSSIBLE_STATUSES = Stream.of(Issue.STATUS_CONFIRMED, Issue.STATUS_REOPENED, Issue.STATUS_RESOLVED).toArray(String[]::new);
  private static int issueIdCounter = 0;

  private static DefaultIssue newDefaultIssue(String projectUuid) {
    DefaultIssue defaultIssue = new DefaultIssue();
    defaultIssue.setKey("issue_" + issueIdCounter++);
    defaultIssue.setProjectUuid(projectUuid);
    defaultIssue.setType(RuleType.values()[new Random().nextInt(RuleType.values().length)]);
    defaultIssue.setStatus(POSSIBLE_STATUSES[new Random().nextInt(POSSIBLE_STATUSES.length)]);
    String[] possibleResolutions = possibleResolutions(defaultIssue.getStatus());
    if (possibleResolutions.length > 0) {
      defaultIssue.setResolution(possibleResolutions[new Random().nextInt(possibleResolutions.length)]);
    }
    return defaultIssue;
  }

  private static String[] possibleResolutions(String status) {
    switch (status) {
      case Issue.STATUS_RESOLVED:
        return new String[] {Issue.RESOLUTION_FALSE_POSITIVE, Issue.RESOLUTION_WONT_FIX};
      default:
        return new String[0];
    }
  }

  private static BranchDto newBranchDto(String uuid) {
    BranchDto branchDto = new BranchDto();
    branchDto.setUuid(uuid);
    return branchDto;
  }

  private static QGChangeEvent newQGChangeEvent(BranchDto branch) {
    QGChangeEvent res = mock(QGChangeEvent.class);
    when(res.getBranch()).thenReturn(branch);
    return res;
  }

  private static <T> ArgumentCaptor<Set<T>> newSetCaptor() {
    Class<Set<T>> clazz = (Class<Set<T>>) (Class) Set.class;
    return ArgumentCaptor.forClass(clazz);
  }

  private static <T> List<T> randomizedList(List<T> issues) {
    ArrayList<T> res = new ArrayList<>(issues);
    Collections.shuffle(res);
    return ImmutableList.copyOf(res);
  }

}
