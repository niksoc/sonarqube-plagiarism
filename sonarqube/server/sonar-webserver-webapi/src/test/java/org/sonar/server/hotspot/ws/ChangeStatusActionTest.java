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
package org.sonar.server.hotspot.ws;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.issue.IssueTesting;
import org.sonar.db.rule.RuleDefinitionDto;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.issue.IssueFieldsSetter;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.issue.ws.IssueUpdater;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.RESOLUTION_SAFE;
import static org.sonar.api.issue.Issue.STATUS_CLOSED;
import static org.sonar.api.issue.Issue.STATUS_REVIEWED;
import static org.sonar.api.issue.Issue.STATUS_TO_REVIEW;
import static org.sonar.api.rules.RuleType.SECURITY_HOTSPOT;
import static org.sonar.db.component.ComponentTesting.newFileDto;

@RunWith(DataProviderRunner.class)
public class ChangeStatusActionTest {
  private static final Random RANDOM = new Random();
  private static final String NO_COMMENT = null;

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();

  private DbClient dbClient = dbTester.getDbClient();
  private TransitionService transitionService = mock(TransitionService.class);
  private IssueUpdater issueUpdater = mock(IssueUpdater.class);
  private System2 system2 = mock(System2.class);
  private IssueFieldsSetter issueFieldsSetter = mock(IssueFieldsSetter.class);
  private HotspotWsSupport hotspotWsSupport = new HotspotWsSupport(dbClient, userSessionRule, system2);
  private ChangeStatusAction underTest = new ChangeStatusAction(dbClient, hotspotWsSupport, transitionService, issueFieldsSetter, issueUpdater);
  private WsActionTester actionTester = new WsActionTester(underTest);

  @Test
  public void ws_is_internal() {
    assertThat(actionTester.getDef().isInternal()).isTrue();
  }

  @Test
  public void fails_with_UnauthorizedException_if_user_is_anonymous() {
    userSessionRule.anonymous();

    TestRequest request = actionTester.newRequest();

    assertThatThrownBy(request::execute)
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Authentication is required");
  }

  @Test
  public void fails_with_IAE_if_parameter_hotspot_is_missing() {
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest();

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("The 'hotspot' parameter is missing");
  }

  @Test
  public void fails_with_IAE_if_parameter_status_is_missing() {
    String key = randomAlphabetic(12);
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", key);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("The 'status' parameter is missing");
  }

  @Test
  @UseDataProvider("badStatuses")
  public void fail_with_IAE_if_status_value_is_neither_REVIEWED_nor_TO_REVIEW(String badStatus) {
    String key = randomAlphabetic(12);
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", key)
      .setParam("status", badStatus);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Value of parameter 'status' (" + badStatus + ") must be one of: [TO_REVIEW, REVIEWED]");
  }

  @DataProvider
  public static Object[][] badStatuses() {
    return Stream.concat(
      Issue.STATUSES.stream()
        .filter(t -> !t.equals(STATUS_TO_REVIEW))
        .filter(t -> !t.equals(STATUS_REVIEWED)),
      Stream.of(randomAlphabetic(22), ""))
      .map(t -> new Object[] {t})
      .toArray(Object[][]::new);
  }

  @Test
  @UseDataProvider("badResolutions")
  public void fail_with_IAE_if_resolution_value_is_neither_FIXED_nor_SAFE(String validStatus, String badResolution) {
    String key = randomAlphabetic(12);
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", key)
      .setParam("status", validStatus)
      .setParam("resolution", badResolution);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Value of parameter 'resolution' (" + badResolution + ") must be one of: [FIXED, SAFE]");
  }

  @DataProvider
  public static Object[][] badResolutions() {
    return Stream.of(STATUS_TO_REVIEW, STATUS_REVIEWED)
      .flatMap(t -> Stream.concat(Issue.RESOLUTIONS.stream(), Issue.SECURITY_HOTSPOT_RESOLUTIONS.stream())
        .filter(r -> !r.equals(RESOLUTION_FIXED))
        .filter(r -> !r.equals(RESOLUTION_SAFE))
        .map(r -> new Object[] {t, r}))
      .toArray(Object[][]::new);
  }

  @Test
  @UseDataProvider("validResolutions")
  public void fail_with_IAE_if_status_is_TO_REVIEW_and_resolution_is_set(String resolution) {
    String key = randomAlphabetic(12);
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", key)
      .setParam("status", STATUS_TO_REVIEW)
      .setParam("resolution", resolution);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Parameter 'resolution' must not be specified when Parameter 'status' has value 'TO_REVIEW'");
  }

  @DataProvider
  public static Object[][] validResolutions() {
    return new Object[][] {
      {RESOLUTION_FIXED},
      {RESOLUTION_SAFE}
    };
  }

  public void fail_with_IAE_if_status_is_RESOLVED_and_resolution_is_not_set() {
    String key = randomAlphabetic(12);
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", key)
      .setParam("status", Issue.STATUS_RESOLVED);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Parameter 'resolution' must not be specified when Parameter 'status' has value 'TO_REVIEW'");
  }

  @Test
  @UseDataProvider("validStatusAndResolutions")
  public void fails_with_NotFoundException_if_hotspot_does_not_exist(String status, @Nullable String resolution) {
    String key = randomAlphabetic(12);
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", key)
      .setParam("status", status);
    if (resolution != null) {
      request.setParam("resolution", resolution);
    }

    assertThatThrownBy(request::execute)
      .isInstanceOf(NotFoundException.class)
      .hasMessage("Hotspot '%s' does not exist", key);
  }

  @Test
  @UseDataProvider("validStatusAndResolutions")
  public void fails_with_NotFoundException_if_hotspot_is_closed(String status, @Nullable String resolution) {
    ComponentDto project = dbTester.components().insertPublicProject();
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    RuleDefinitionDto rule = newRule(SECURITY_HOTSPOT);
    IssueDto closedHotspot = dbTester.issues().insertHotspot(rule, project, file, t -> t.setStatus(STATUS_CLOSED));
    userSessionRule.logIn();
    TestRequest request = actionTester.newRequest()
      .setParam("hotspot", closedHotspot.getKey())
      .setParam("status", status);
    if (resolution != null) {
      request.setParam("resolution", resolution);
    }

    assertThatThrownBy(request::execute)
      .isInstanceOf(NotFoundException.class)
      .hasMessage("Hotspot '%s' does not exist", closedHotspot.getKey());
  }

  @DataProvider
  public static Object[][] validStatusAndResolutions() {
    return new Object[][] {
      {STATUS_TO_REVIEW, null},
      {STATUS_REVIEWED, RESOLUTION_FIXED},
      {STATUS_REVIEWED, RESOLUTION_SAFE}
    };
  }

  @Test
  @UseDataProvider("ruleTypesButHotspot")
  public void fails_with_NotFoundException_if_issue_is_not_a_hotspot(String status, @Nullable String resolution, RuleType ruleType) {
    ComponentDto project = dbTester.components().insertPublicProject();
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    RuleDefinitionDto rule = newRule(ruleType);
    IssueDto notAHotspot = dbTester.issues().insert(IssueTesting.newIssue(rule, project, file).setType(ruleType));
    userSessionRule.logIn();
    TestRequest request = newRequest(notAHotspot, status, resolution, NO_COMMENT);

    assertThatThrownBy(request::execute)
      .isInstanceOf(NotFoundException.class)
      .hasMessage("Hotspot '%s' does not exist", notAHotspot.getKey());
  }

  @DataProvider
  public static Object[][] ruleTypesButHotspot() {
    return Arrays.stream(RuleType.values())
      .filter(t -> t != SECURITY_HOTSPOT)
      .flatMap(t -> Arrays.stream(validStatusAndResolutions()).map(u -> new Object[] {u[0], u[1], t}))
      .toArray(Object[][]::new);
  }

  @Test
  @UseDataProvider("anyPublicProjectPermissionButHotspotAdmin")
  public void fails_with_ForbiddenException_if_project_is_public_and_user_has_no_HotspotAdmin_permission_on_it(String permission) {
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(permission, project);
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file);

    Arrays.stream(validStatusAndResolutions())
      .forEach(o -> {
        String status = (String) o[0];
        String resolution = (String) o[1];

        TestRequest request = newRequest(hotspot, status, resolution, NO_COMMENT);
        assertThatThrownBy(request::execute)
          .isInstanceOf(ForbiddenException.class)
          .hasMessage("Insufficient privileges");
      });
  }

  @DataProvider
  public static Object[][] anyPublicProjectPermissionButHotspotAdmin() {
    return new Object[][] {
      {UserRole.ADMIN},
      {UserRole.ISSUE_ADMIN},
      {UserRole.SCAN}
    };
  }

  @Test
  @UseDataProvider("anyPrivateProjectPermissionButHotspotAdmin")
  public void fails_with_ForbiddenException_if_project_is_private_and_has_no_IssueAdmin_permission_on_it(String permission) {
    ComponentDto project = dbTester.components().insertPrivateProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(permission, project);
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file);

    Arrays.stream(validStatusAndResolutions())
      .forEach(o -> {
        String status = (String) o[0];
        String resolution = (String) o[1];

        TestRequest request = newRequest(hotspot, status, resolution, NO_COMMENT);
        assertThatThrownBy(request::execute)
          .isInstanceOf(ForbiddenException.class)
          .hasMessage("Insufficient privileges");
      });
  }

  @DataProvider
  public static Object[][] anyPrivateProjectPermissionButHotspotAdmin() {
    return new Object[][] {
      {UserRole.USER},
      {UserRole.ADMIN},
      {UserRole.ISSUE_ADMIN},
      {UserRole.CODEVIEWER},
      {UserRole.SCAN}
    };
  }

  @Test
  @UseDataProvider("validStatusAndResolutions")
  public void succeeds_on_public_project_with_HotspotAdmin_permission(String status, @Nullable String resolution) {
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file);

    newRequest(hotspot, status, resolution, NO_COMMENT).execute().assertNoContent();
  }

  @Test
  @UseDataProvider("validStatusAndResolutions")
  public void succeeds_on_private_project_with_HotspotAdmin_permission(String status, @Nullable String resolution) {
    ComponentDto project = dbTester.components().insertPrivateProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file);

    newRequest(hotspot, status, resolution, NO_COMMENT).execute().assertNoContent();
  }

  @Test
  @UseDataProvider("validStatusAndResolutions")
  public void no_effect_and_success_if_hotspot_already_has_specified_status_and_resolution(String status, @Nullable String resolution) {
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file, h -> h.setStatus(status).setResolution(resolution));

    newRequest(hotspot, status, resolution, NO_COMMENT).execute().assertNoContent();

    verifyNoInteractions(transitionService, issueUpdater, issueFieldsSetter);
  }

  @Test
  @UseDataProvider("reviewedResolutionsAndExpectedTransitionKey")
  public void success_to_change_hostpot_to_review_into_reviewed_status(String resolution, String expectedTransitionKey, boolean transitionDone) {
    long now = RANDOM.nextInt(232_323);
    when(system2.now()).thenReturn(now);
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);
    ;
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file, h -> h.setStatus(STATUS_TO_REVIEW).setResolution(null));
    when(transitionService.doTransition(any(), any(), any())).thenReturn(transitionDone);

    newRequest(hotspot, STATUS_REVIEWED, resolution, NO_COMMENT).execute().assertNoContent();

    IssueChangeContext issueChangeContext = IssueChangeContext.createUser(new Date(now), userSessionRule.getUuid());
    ArgumentCaptor<DefaultIssue> defaultIssueCaptor = ArgumentCaptor.forClass(DefaultIssue.class);
    verify(transitionService).checkTransitionPermission(eq(expectedTransitionKey), defaultIssueCaptor.capture());
    verify(transitionService).doTransition(
      defaultIssueCaptor.capture(),
      eq(issueChangeContext),
      eq(expectedTransitionKey));
    if (transitionDone) {
      verify(issueUpdater).saveIssueAndPreloadSearchResponseData(
        any(DbSession.class),
        defaultIssueCaptor.capture(),
        eq(issueChangeContext),
        eq(true));

      // because it is mutated by FieldSetter and IssueUpdater, the same object must be passed to all methods
      verifyAllSame3Objects(defaultIssueCaptor.getAllValues());
      verifyNoInteractions(issueFieldsSetter);
    } else {
      verifyNoInteractions(issueUpdater, issueFieldsSetter);
    }
  }

  @DataProvider
  public static Object[][] reviewedResolutionsAndExpectedTransitionKey() {
    return new Object[][] {
      {RESOLUTION_FIXED, DefaultTransitions.RESOLVE_AS_REVIEWED, true},
      {RESOLUTION_FIXED, DefaultTransitions.RESOLVE_AS_REVIEWED, false},
      {RESOLUTION_SAFE, DefaultTransitions.RESOLVE_AS_SAFE, true},
      {RESOLUTION_SAFE, DefaultTransitions.RESOLVE_AS_SAFE, false}
    };
  }

  @Test
  @UseDataProvider("reviewedResolutions")
  public void success_to_change_reviewed_hotspot_back_to_to_review(String resolution, boolean transitionDone) {
    long now = RANDOM.nextInt(232_323);
    when(system2.now()).thenReturn(now);
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);

    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file, h -> h.setStatus(STATUS_REVIEWED).setResolution(resolution));
    when(transitionService.doTransition(any(), any(), any())).thenReturn(transitionDone);

    newRequest(hotspot, STATUS_TO_REVIEW, null, NO_COMMENT).execute().assertNoContent();

    IssueChangeContext issueChangeContext = IssueChangeContext.createUser(new Date(now), userSessionRule.getUuid());
    ArgumentCaptor<DefaultIssue> defaultIssueCaptor = ArgumentCaptor.forClass(DefaultIssue.class);
    verify(transitionService).checkTransitionPermission(eq(DefaultTransitions.RESET_AS_TO_REVIEW), defaultIssueCaptor.capture());
    verify(transitionService).doTransition(
      defaultIssueCaptor.capture(),
      eq(issueChangeContext),
      eq(DefaultTransitions.RESET_AS_TO_REVIEW));
    if (transitionDone) {
      verify(issueUpdater).saveIssueAndPreloadSearchResponseData(
        any(DbSession.class),
        defaultIssueCaptor.capture(),
        eq(issueChangeContext),
        eq(true));

      // because it is mutated by FieldSetter and IssueUpdater, the same object must be passed to all methods
      verifyAllSame3Objects(defaultIssueCaptor.getAllValues());
      verifyNoInteractions(issueFieldsSetter);
    } else {
      verifyNoInteractions(issueUpdater, issueFieldsSetter);
    }
  }

  @DataProvider
  public static Object[][] reviewedResolutions() {
    return new Object[][] {
      {RESOLUTION_FIXED, true},
      {RESOLUTION_FIXED, false},
      {RESOLUTION_SAFE, true},
      {RESOLUTION_SAFE, false}
    };
  }

  @Test
  @UseDataProvider("changingStatusAndTransitionFlag")
  public void persists_comment_if_hotspot_status_changes_and_transition_done(String currentStatus, @Nullable String currentResolution,
    String newStatus, @Nullable String newResolution, boolean transitionDone) {
    long now = RANDOM.nextInt(232_323);
    when(system2.now()).thenReturn(now);
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);

    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file, h -> h.setStatus(currentStatus).setResolution(currentResolution));
    when(transitionService.doTransition(any(), any(), any())).thenReturn(transitionDone);
    String comment = randomAlphabetic(12);

    newRequest(hotspot, newStatus, newResolution, comment).execute().assertNoContent();

    IssueChangeContext issueChangeContext = IssueChangeContext.createUser(new Date(now), userSessionRule.getUuid());
    ArgumentCaptor<DefaultIssue> defaultIssueCaptor = ArgumentCaptor.forClass(DefaultIssue.class);
    verify(transitionService).doTransition(defaultIssueCaptor.capture(), eq(issueChangeContext), anyString());
    if (transitionDone) {
      verify(issueFieldsSetter).addComment(defaultIssueCaptor.capture(), eq(comment), eq(issueChangeContext));
      verify(issueUpdater).saveIssueAndPreloadSearchResponseData(
        any(DbSession.class),
        defaultIssueCaptor.capture(),
        eq(issueChangeContext),
        eq(true));

      // because it is mutated by FieldSetter and IssueUpdater, the same object must be passed to all methods
      verifyAllSame3Objects(defaultIssueCaptor.getAllValues());
    } else {
      verifyNoInteractions(issueUpdater, issueFieldsSetter);
    }
  }

  @DataProvider
  public static Object[][] changingStatusAndTransitionFlag() {
    Object[][] changingStatuses = {
      {STATUS_TO_REVIEW, null, STATUS_REVIEWED, RESOLUTION_FIXED},
      {STATUS_TO_REVIEW, null, STATUS_REVIEWED, RESOLUTION_FIXED},
      {STATUS_TO_REVIEW, null, STATUS_REVIEWED, RESOLUTION_SAFE},
      {STATUS_REVIEWED, RESOLUTION_FIXED, STATUS_REVIEWED, RESOLUTION_SAFE},
      {STATUS_REVIEWED, RESOLUTION_FIXED, STATUS_TO_REVIEW, null},
      {STATUS_REVIEWED, RESOLUTION_SAFE, STATUS_REVIEWED, RESOLUTION_FIXED},
      {STATUS_REVIEWED, RESOLUTION_SAFE, STATUS_TO_REVIEW, null}
    };
    return Arrays.stream(changingStatuses)
      .flatMap(b -> Stream.of(
        new Object[] {b[0], b[1], b[2], b[3], true},
        new Object[] {b[0], b[1], b[2], b[3], false}))
      .toArray(Object[][]::new);
  }

  @Test
  @UseDataProvider("validStatusAndResolutions")
  public void do_not_persist_comment_if_no_status_change(String status, @Nullable String resolution) {
    long now = RANDOM.nextInt(232_323);
    when(system2.now()).thenReturn(now);
    ComponentDto project = dbTester.components().insertPublicProject();
    userSessionRule.logIn().registerComponents(project)
      .addProjectPermission(UserRole.SECURITYHOTSPOT_ADMIN, project);
    ;
    ComponentDto file = dbTester.components().insertComponent(newFileDto(project));
    IssueDto hotspot = dbTester.issues().insertHotspot(project, file, h -> h.setStatus(status).setResolution(resolution));
    String comment = randomAlphabetic(12);

    newRequest(hotspot, status, resolution, comment).execute().assertNoContent();

    verifyNoInteractions(transitionService, issueUpdater, issueFieldsSetter);
  }

  private TestRequest newRequest(IssueDto hotspot, String newStatus, @Nullable String newResolution, @Nullable String comment) {
    TestRequest res = actionTester.newRequest()
      .setParam("hotspot", hotspot.getKey())
      .setParam("status", newStatus);
    if (newResolution != null) {
      res.setParam("resolution", newResolution);
    }
    if (comment != null) {
      res.setParam("comment", comment);
    }
    return res;
  }

  private RuleDefinitionDto newRule(RuleType ruleType) {
    return newRule(ruleType, t -> {
    });
  }

  private RuleDefinitionDto newRule(RuleType ruleType, Consumer<RuleDefinitionDto> populate) {
    RuleDefinitionDto ruleDefinition = RuleTesting.newRule()
      .setType(ruleType);
    populate.accept(ruleDefinition);
    dbTester.rules().insert(ruleDefinition);
    return ruleDefinition;
  }

  private static void verifyAllSame3Objects(List<DefaultIssue> allValues) {
    assertThat(allValues).hasSize(3);
    assertThat(allValues.get(0))
      .isSameAs(allValues.get(1))
      .isSameAs(allValues.get(2));
  }

}
