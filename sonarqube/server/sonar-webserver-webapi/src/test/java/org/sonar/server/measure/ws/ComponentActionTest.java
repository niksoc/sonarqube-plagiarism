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
package org.sonar.server.measure.ws;

import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.component.TestComponentFinder;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Measures;
import org.sonarqube.ws.Measures.Component;
import org.sonarqube.ws.Measures.ComponentWsResponse;

import static java.lang.Double.parseDouble;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.api.utils.DateUtils.parseDateTime;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.BranchType.PULL_REQUEST;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newProjectCopy;
import static org.sonar.server.component.ws.MeasuresWsParameters.PARAM_ADDITIONAL_FIELDS;
import static org.sonar.server.component.ws.MeasuresWsParameters.PARAM_BRANCH;
import static org.sonar.server.component.ws.MeasuresWsParameters.PARAM_COMPONENT;
import static org.sonar.server.component.ws.MeasuresWsParameters.PARAM_METRIC_KEYS;
import static org.sonar.server.component.ws.MeasuresWsParameters.PARAM_PULL_REQUEST;
import static org.sonar.test.JsonAssert.assertJson;

public class ComponentActionTest {

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private WsActionTester ws = new WsActionTester(new ComponentAction(db.getDbClient(), TestComponentFinder.from(db), userSession));

  @Test
  public void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.since()).isEqualTo("5.4");
    assertThat(def.params()).extracting(Param::key)
      .containsExactlyInAnyOrder("component", "branch", "pullRequest", "metricKeys", "additionalFields");

    WebService.Param component = def.param(PARAM_COMPONENT);
    assertThat(component.isRequired()).isTrue();

    WebService.Param branch = def.param("branch");
    assertThat(branch.since()).isEqualTo("6.6");
    assertThat(branch.isInternal()).isTrue();
    assertThat(branch.isRequired()).isFalse();
  }

  @Test
  public void provided_project() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    ComponentWsResponse response = newRequest(project.getKey(), metric.getKey());

    assertThat(response.getMetrics().getMetricsCount()).isEqualTo(1);
    assertThat(response.hasPeriod()).isFalse();
    assertThat(response.getPeriods().getPeriodsCount()).isEqualTo(0);
    assertThat(response.getComponent().getKey()).isEqualTo(project.getDbKey());
  }

  @Test
  public void without_additional_fields() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    db.components().insertSnapshot(project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    String response = ws.newRequest()
      .setParam(PARAM_COMPONENT, project.getKey())
      .setParam(PARAM_METRIC_KEYS, metric.getKey())
      .execute().getInput();

    assertThat(response)
      .doesNotContain("period")
      .doesNotContain("metrics");
  }

  @Test
  public void branch() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setKey("my_branch"));
    SnapshotDto analysis = db.components().insertSnapshot(branch);
    ComponentDto file = db.components().insertComponent(newFileDto(branch));
    MetricDto complexity = db.measures().insertMetric(m1 -> m1.setKey("complexity").setValueType("INT"));
    LiveMeasureDto measure = db.measures().insertLiveMeasure(file, complexity, m -> m.setValue(12.0d).setVariation(2.0d));

    ComponentWsResponse response = ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_BRANCH, file.getBranch())
      .setParam(PARAM_METRIC_KEYS, complexity.getKey())
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent()).extracting(Component::getKey, Component::getBranch)
      .containsExactlyInAnyOrder(file.getKey(), file.getBranch());
    assertThat(response.getComponent().getMeasuresList())
      .extracting(Measures.Measure::getMetric, m -> parseDouble(m.getValue()))
      .containsExactlyInAnyOrder(tuple(complexity.getKey(), measure.getValue()));
  }

  @Test
  public void pull_request() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setKey("pr-123").setBranchType(PULL_REQUEST));
    SnapshotDto analysis = db.components().insertSnapshot(branch);
    ComponentDto file = db.components().insertComponent(newFileDto(branch));
    MetricDto complexity = db.measures().insertMetric(m1 -> m1.setKey("complexity").setValueType("INT"));
    LiveMeasureDto measure = db.measures().insertLiveMeasure(file, complexity, m -> m.setValue(12.0d).setVariation(2.0d));

    ComponentWsResponse response = ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_PULL_REQUEST, "pr-123")
      .setParam(PARAM_METRIC_KEYS, complexity.getKey())
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent()).extracting(Component::getKey, Component::getPullRequest)
      .containsExactlyInAnyOrder(file.getKey(), "pr-123");
    assertThat(response.getComponent().getMeasuresList())
      .extracting(Measures.Measure::getMetric, m -> parseDouble(m.getValue()))
      .containsExactlyInAnyOrder(tuple(complexity.getKey(), measure.getValue()));
  }

  @Test
  public void new_issue_count_measures_are_transformed_in_pr() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setKey("pr-123").setBranchType(PULL_REQUEST));
    SnapshotDto analysis = db.components().insertSnapshot(branch);
    ComponentDto file = db.components().insertComponent(newFileDto(branch));
    MetricDto bugs = db.measures().insertMetric(m1 -> m1.setKey("bugs").setValueType("INT"));
    MetricDto newBugs = db.measures().insertMetric(m1 -> m1.setKey("new_bugs").setValueType("INT"));
    MetricDto violations = db.measures().insertMetric(m1 -> m1.setKey("violations").setValueType("INT"));
    MetricDto newViolations = db.measures().insertMetric(m1 -> m1.setKey("new_violations").setValueType("INT"));
    LiveMeasureDto bugMeasure = db.measures().insertLiveMeasure(file, bugs, m -> m.setValue(12.0d).setVariation(null));
    LiveMeasureDto newBugMeasure = db.measures().insertLiveMeasure(file, newBugs, m -> m.setVariation(1d).setValue(null));
    LiveMeasureDto violationMeasure = db.measures().insertLiveMeasure(file, violations, m -> m.setValue(20.0d).setVariation(null));

    ComponentWsResponse response = ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_PULL_REQUEST, "pr-123")
      .setParam(PARAM_METRIC_KEYS, newBugs.getKey() + "," + bugs.getKey() + "," + newViolations.getKey())
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent()).extracting(Component::getKey, Component::getPullRequest)
      .containsExactlyInAnyOrder(file.getKey(), "pr-123");

    Function<Measures.Measure, Double> extractVariation = m -> {
      if (m.getPeriods().getPeriodsValueCount() > 0) {
        return parseDouble(m.getPeriods().getPeriodsValue(0).getValue());
      }
      return null;
    };
    assertThat(response.getComponent().getMeasuresList())
      .extracting(Measures.Measure::getMetric, extractVariation, m -> m.getValue().isEmpty() ? null : parseDouble(m.getValue()))
      .containsExactlyInAnyOrder(
        tuple(newBugs.getKey(), bugMeasure.getValue(), null),
        tuple(bugs.getKey(), null, bugMeasure.getValue()),
        tuple(newViolations.getKey(), violationMeasure.getValue(), null));
  }

  @Test
  public void new_issue_count_measures_are_not_transformed_if_they_dont_exist_in_pr() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setKey("pr-123").setBranchType(PULL_REQUEST));
    SnapshotDto analysis = db.components().insertSnapshot(branch);
    ComponentDto file = db.components().insertComponent(newFileDto(branch));
    MetricDto bugs = db.measures().insertMetric(m1 -> m1.setKey("bugs").setOptimizedBestValue(false).setValueType("INT"));
    MetricDto newBugs = db.measures().insertMetric(m1 -> m1.setKey("new_bugs").setOptimizedBestValue(false).setValueType("INT"));

    ComponentWsResponse response = ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_PULL_REQUEST, "pr-123")
      .setParam(PARAM_METRIC_KEYS, newBugs.getKey() + "," + bugs.getKey())
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent()).extracting(Component::getKey, Component::getPullRequest)
      .containsExactlyInAnyOrder(file.getKey(), "pr-123");

    assertThat(response.getComponent().getMeasuresList()).isEmpty();
  }

  @Test
  public void reference_uuid_in_the_response() {
    userSession.logIn().setRoot();
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto view = db.components().insertView();
    db.components().insertSnapshot(view);
    ComponentDto projectCopy = db.components().insertComponent(newProjectCopy("project-uuid-copy", project, view));
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    ComponentWsResponse response = newRequest(projectCopy.getKey(), metric.getKey());

    assertThat(response.getComponent().getRefId()).isEqualTo(project.uuid());
    assertThat(response.getComponent().getRefKey()).isEqualTo(project.getKey());
  }

  @Test
  public void return_deprecated_id_in_the_response() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    db.components().insertSnapshot(project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    ComponentWsResponse response = newRequest(project.getKey(), metric.getKey());

    assertThat(response.getComponent().getId()).isEqualTo(project.uuid());
  }

  @Test
  public void use_deprecated_component_id_parameter() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    userSession.addProjectPermission(USER, project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    ComponentWsResponse response = ws.newRequest()
      .setParam("component", project.getDbKey())
      .setParam(PARAM_METRIC_KEYS, metric.getKey())
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent().getKey()).isEqualTo(project.getDbKey());
  }

  @Test
  public void metric_without_a_domain() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    MetricDto metricWithoutDomain = db.measures().insertMetric(m -> m
      .setValueType("INT")
      .setDomain(null));
    db.measures().insertLiveMeasure(project, metricWithoutDomain);

    ComponentWsResponse response = ws.newRequest()
      .setParam(PARAM_COMPONENT, project.getKey())
      .setParam(PARAM_METRIC_KEYS, metricWithoutDomain.getKey())
      .setParam(PARAM_ADDITIONAL_FIELDS, "metrics")
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent().getMeasuresList()).extracting(Measures.Measure::getMetric).containsExactly(metricWithoutDomain.getKey());
    Common.Metric responseMetric = response.getMetrics().getMetrics(0);
    assertThat(responseMetric.getKey()).isEqualTo(metricWithoutDomain.getKey());
    assertThat(responseMetric.hasDomain()).isFalse();
  }

  @Test
  public void use_best_values() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    userSession.addProjectPermission(UserRole.USER, project);
    MetricDto metric = db.measures().insertMetric(m -> m
      .setValueType("INT")
      .setBestValue(7.0d)
      .setOptimizedBestValue(true)
      .setDomain(null));

    ComponentWsResponse response = ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_METRIC_KEYS, metric.getKey())
      .setParam(PARAM_ADDITIONAL_FIELDS, "metrics")
      .executeProtobuf(ComponentWsResponse.class);

    assertThat(response.getComponent().getMeasuresList())
      .extracting(Measures.Measure::getMetric, Measures.Measure::getValue, Measures.Measure::getBestValue)
      .containsExactly(tuple(metric.getKey(), "7", true));
  }

  @Test
  public void fail_when_a_metric_is_not_found() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    db.components().insertSnapshot(project);
    db.measures().insertMetric(m -> m.setKey("ncloc").setValueType("INT"));
    db.measures().insertMetric(m -> m.setKey("complexity").setValueType("INT"));

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("The following metric keys are not found: unknown-metric, another-unknown-metric");

    newRequest(project.getKey(), "ncloc, complexity, unknown-metric, another-unknown-metric");
  }

  @Test
  public void fail_when_empty_metric_keys_parameter() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    db.components().insertSnapshot(project);

    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("At least one metric key must be provided");

    newRequest(project.getKey(), "");
  }

  @Test
  public void fail_when_not_enough_permission() {
    userSession.logIn();
    ComponentDto project = db.components().insertPrivateProject();
    db.components().insertSnapshot(project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    expectedException.expect(ForbiddenException.class);

    newRequest(project.getKey(), metric.getKey());
  }

  @Test
  public void fail_when_component_does_not_exist() {
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("Component key 'project-key' not found");

    ws.newRequest()
      .setParam(PARAM_COMPONENT, "project-key")
      .setParam(PARAM_METRIC_KEYS, metric.getKey())
      .execute();
  }

  @Test
  public void fail_when_component_is_removed() {
    ComponentDto project = db.components().insertPrivateProject(p -> p.setEnabled(false));
    userSession.addProjectPermission(UserRole.USER, project);
    userSession.addProjectPermission(USER, project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage(String.format("Component key '%s' not found", project.getKey()));

    ws.newRequest()
      .setParam(PARAM_COMPONENT, project.getKey())
      .setParam(PARAM_METRIC_KEYS, metric.getKey())
      .execute();
  }

  @Test
  public void fail_if_branch_does_not_exist() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    userSession.addProjectPermission(UserRole.USER, project);
    db.components().insertProjectBranch(project, b -> b.setKey("my_branch"));

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage(String.format("Component '%s' on branch '%s' not found", file.getKey(), "another_branch"));

    ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_BRANCH, "another_branch")
      .setParam(PARAM_METRIC_KEYS, "ncloc")
      .execute();
  }

  @Test
  public void fail_when_using_branch_db_key() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPrivateProject(organization);
    userSession.logIn().addProjectPermission(UserRole.USER, project);
    ComponentDto branch = db.components().insertProjectBranch(project);
    MetricDto metric = db.measures().insertMetric(m -> m.setValueType("INT"));

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage(format("Component key '%s' not found", branch.getDbKey()));

    ws.newRequest()
      .setParam(PARAM_COMPONENT, branch.getDbKey())
      .setParam(PARAM_METRIC_KEYS, metric.getKey())
      .execute();
  }

  @Test
  public void json_example() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    SnapshotDto analysis = db.components().insertSnapshot(project,
      s -> s.setPeriodDate(parseDateTime("2016-01-11T10:49:50+0100").getTime())
        .setPeriodMode("previous_version")
        .setPeriodParam("1.0-SNAPSHOT"));
    ComponentDto file = db.components().insertComponent(newFileDto(project)
      .setDbKey("MY_PROJECT:ElementImpl.java")
      .setName("ElementImpl.java")
      .setLanguage("java")
      .setPath("src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"));

    MetricDto complexity = db.measures().insertMetric(m -> m.setKey("complexity")
      .setShortName("Complexity")
      .setDescription("Cyclomatic complexity")
      .setDomain("Complexity")
      .setValueType("INT")
      .setDirection(-1)
      .setQualitative(false)
      .setHidden(false)
      .setUserManaged(false));
    db.measures().insertLiveMeasure(file, complexity,
      m -> m.setValue(12.0d)
        .setVariation(2.0d)
        .setData((String) null));

    MetricDto ncloc = db.measures().insertMetric(m1 -> m1.setKey("ncloc")
      .setShortName("Lines of code")
      .setDescription("Non Commenting Lines of Code")
      .setDomain("Size")
      .setValueType("INT")
      .setDirection(-1)
      .setQualitative(false)
      .setHidden(false)
      .setUserManaged(false));
    db.measures().insertLiveMeasure(file, ncloc,
      m -> m.setValue(114.0d)
        .setVariation(3.0d)
        .setData((String) null));

    MetricDto newViolations = db.measures().insertMetric(m -> m.setKey("new_violations")
      .setShortName("New issues")
      .setDescription("New Issues")
      .setDomain("Issues")
      .setValueType("INT")
      .setDirection(-1)
      .setQualitative(true)
      .setHidden(false)
      .setUserManaged(false));
    db.measures().insertLiveMeasure(file, newViolations,
      m -> m.setVariation(25.0d)
        .setValue(null)
        .setData((String) null));

    String response = ws.newRequest()
      .setParam(PARAM_COMPONENT, file.getKey())
      .setParam(PARAM_METRIC_KEYS, "ncloc, complexity, new_violations")
      .setParam(PARAM_ADDITIONAL_FIELDS, "metrics,period,periods")
      .execute()
      .getInput();

    assertJson(response).isSimilarTo(getClass().getResource("component-example.json"));
  }

  private ComponentWsResponse newRequest(String componentKey, String metricKeys) {
    return ws.newRequest()
      .setParam(PARAM_COMPONENT, componentKey)
      .setParam(PARAM_METRIC_KEYS, metricKeys)
      .setParam(PARAM_ADDITIONAL_FIELDS, "metrics,period,periods")
      .executeProtobuf(ComponentWsResponse.class);
  }
}
