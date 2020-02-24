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
package org.sonar.server.project.ws;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.System2;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.organization.BillingValidationsProxy;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.project.Project;
import org.sonar.server.project.ProjectLifeCycleListeners;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.sonar.api.utils.DateUtils.formatDate;
import static org.sonar.db.component.SnapshotTesting.newAnalysis;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_ANALYZED_BEFORE;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_ON_PROVISIONED_ONLY;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_ORGANIZATION;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_PROJECTS;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_QUALIFIERS;

public class BulkDeleteActionTest {

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private ComponentCleanerService componentCleanerService = mock(ComponentCleanerService.class);
  private DbClient dbClient = db.getDbClient();
  private ProjectsWsSupport support = new ProjectsWsSupport(dbClient, TestDefaultOrganizationProvider.from(db), mock(BillingValidationsProxy.class));
  private ProjectLifeCycleListeners projectLifeCycleListeners = mock(ProjectLifeCycleListeners.class);

  private BulkDeleteAction underTest = new BulkDeleteAction(componentCleanerService, dbClient, userSession, support, projectLifeCycleListeners);
  private WsActionTester ws = new WsActionTester(underTest);

  private OrganizationDto org1;
  private OrganizationDto org2;

  @Before
  public void setUp() {
    org1 = db.organizations().insert();
    org2 = db.organizations().insert();
  }

  @Test
  public void delete_projects_in_default_organization_if_no_org_provided() {
    userSession.logIn().setRoot();
    OrganizationDto defaultOrganization = db.getDefaultOrganization();
    ComponentDto toDeleteInOrg1 = db.components().insertPrivateProject(org1);
    ComponentDto toDeleteInOrg2 = db.components().insertPrivateProject(defaultOrganization);
    ComponentDto toKeep = db.components().insertPrivateProject(defaultOrganization);

    TestResponse result = ws.newRequest()
      .setParam(PARAM_PROJECTS, toDeleteInOrg1.getDbKey() + "," + toDeleteInOrg2.getDbKey())
      .execute();

    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
    assertThat(result.getInput()).isEmpty();
    verifyComponentDeleted(toDeleteInOrg2);
    verifyListenersOnProjectsDeleted(toDeleteInOrg2);
  }

  @Test
  public void delete_projects_by_keys() {
    userSession.logIn().setRoot();
    ComponentDto toDeleteInOrg1 = db.components().insertPrivateProject(org1);
    ComponentDto toDeleteInOrg2 = db.components().insertPrivateProject(org1);
    ComponentDto toKeep = db.components().insertPrivateProject(org1);

    ws.newRequest()
      .setParam(PARAM_ORGANIZATION, org1.getKey())
      .setParam(PARAM_PROJECTS, toDeleteInOrg1.getDbKey() + "," + toDeleteInOrg2.getDbKey())
      .execute();

    verifyComponentDeleted(toDeleteInOrg1, toDeleteInOrg2);
    verifyListenersOnProjectsDeleted(toDeleteInOrg1, toDeleteInOrg2);
  }

  @Test
  public void throw_IllegalArgumentException_if_request_without_any_parameters() {
    userSession.logIn().setRoot();
    db.components().insertPrivateProject(org1);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("At lease one parameter among analyzedBefore, projects and q must be provided");

    try {
      ws.newRequest().execute();
    } finally {
      verifyNoDeletions();
      verifyZeroInteractions(projectLifeCycleListeners);
    }
  }

  @Test
  public void projects_that_dont_exist_are_ignored_and_dont_break_bulk_deletion() {
    userSession.logIn().setRoot();
    ComponentDto toDelete1 = db.components().insertPrivateProject(org1);
    ComponentDto toDelete2 = db.components().insertPrivateProject(org1);

    ws.newRequest()
      .setParam("organization", org1.getKey())
      .setParam("projects", toDelete1.getDbKey() + ",missing," + toDelete2.getDbKey() + ",doesNotExist")
      .execute();

    verifyComponentDeleted(toDelete1, toDelete2);
    verifyListenersOnProjectsDeleted(toDelete1, toDelete2);
  }

  @Test
  public void old_projects() {
    userSession.logIn().addPermission(ADMINISTER, db.getDefaultOrganization());
    long aLongTimeAgo = 1_000_000_000L;
    long recentTime = 3_000_000_000L;
    ComponentDto oldProject = db.components().insertPublicProject();
    db.getDbClient().snapshotDao().insert(db.getSession(), newAnalysis(oldProject).setCreatedAt(aLongTimeAgo));
    ComponentDto recentProject = db.components().insertPublicProject();
    db.getDbClient().snapshotDao().insert(db.getSession(), newAnalysis(recentProject).setCreatedAt(recentTime));
    db.commit();

    ws.newRequest()
      .setParam(PARAM_ANALYZED_BEFORE, formatDate(new Date(recentTime)))
      .execute();

    verifyComponentDeleted(oldProject);
    verifyListenersOnProjectsDeleted(oldProject);
  }

  @Test
  public void provisioned_projects() {
    userSession.logIn().addPermission(ADMINISTER, db.getDefaultOrganization());
    ComponentDto provisionedProject = db.components().insertPrivateProject();
    ComponentDto analyzedProject = db.components().insertPrivateProject();
    db.components().insertSnapshot(newAnalysis(analyzedProject));

    ws.newRequest().setParam(PARAM_PROJECTS, provisionedProject.getKey() + "," + analyzedProject.getKey()).setParam(PARAM_ON_PROVISIONED_ONLY, "true").execute();

    verifyComponentDeleted(provisionedProject);
    verifyListenersOnProjectsDeleted(provisionedProject);
  }

  @Test
  public void delete_more_than_50_projects() {
    userSession.logIn().addPermission(ADMINISTER, db.getDefaultOrganization());
    ComponentDto[] projects = IntStream.range(0, 55).mapToObj(i -> db.components().insertPrivateProject()).toArray(ComponentDto[]::new);

    List<String> projectKeys = Stream.of(projects).map(ComponentDto::getKey).collect(Collectors.toList());
    ws.newRequest().setParam(PARAM_PROJECTS, String.join(",", projectKeys)).execute();

    verifyComponentDeleted(projects);
    verifyListenersOnProjectsDeleted(projects);
  }

  @Test
  public void projects_and_views() {
    userSession.logIn().addPermission(ADMINISTER, db.getDefaultOrganization());
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto view = db.components().insertView();

    ws.newRequest()
      .setParam(PARAM_PROJECTS, project.getKey() + "," + view.getKey())
      .setParam(PARAM_QUALIFIERS, String.join(",", Qualifiers.PROJECT, Qualifiers.VIEW))
      .execute();

    verifyComponentDeleted(project, view);
    verifyListenersOnProjectsDeleted(project, view);
  }

  @Test
  public void delete_by_key_query_with_partial_match_case_insensitive() {
    userSession.logIn().addPermission(ADMINISTER, db.getDefaultOrganization());
    ComponentDto matchKeyProject = db.components().insertPrivateProject(p -> p.setDbKey("project-_%-key"));
    ComponentDto matchUppercaseKeyProject = db.components().insertPrivateProject(p -> p.setDbKey("PROJECT-_%-KEY"));
    ComponentDto noMatchProject = db.components().insertPrivateProject(p -> p.setDbKey("project-key-without-escaped-characters"));

    ws.newRequest().setParam(Param.TEXT_QUERY, "JeCt-_%-k").execute();

    verifyComponentDeleted(matchKeyProject, matchUppercaseKeyProject);
    verifyListenersOnProjectsDeleted(matchKeyProject, matchUppercaseKeyProject);
  }

  @Test
  public void throw_ForbiddenException_if_organization_administrator_does_not_set_organization_parameter() {
    userSession.logIn().addPermission(ADMINISTER, org1);
    ComponentDto project = db.components().insertPrivateProject(org1);

    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");

    try {
      ws.newRequest()
        .setParam("projects", project.getDbKey())
        .execute();
    } finally {
      verifyNoDeletions();
      verifyZeroInteractions(projectLifeCycleListeners);
    }
  }

  /**
   * SONAR-10356
   */
  @Test
  public void delete_only_the_1000_first_projects() {
    userSession.logIn().addPermission(ADMINISTER, org1);
    List<String> keys = IntStream.range(0, 1_010).mapToObj(i -> "key" + i).collect(MoreCollectors.toArrayList());
    keys.forEach(key -> db.components().insertPrivateProject(org1, p -> p.setDbKey(key)));

    ws.newRequest()
      .setParam("organization", org1.getKey())
      .setParam("projects", StringUtils.join(keys, ","))
      .execute();

    verify(componentCleanerService, times(1_000)).delete(any(DbSession.class), any(ComponentDto.class));
    ArgumentCaptor<Set<Project>> projectsCaptor = ArgumentCaptor.forClass(Set.class);
    verify(projectLifeCycleListeners).onProjectsDeleted(projectsCaptor.capture());
    assertThat(projectsCaptor.getValue()).hasSize(1_000);
  }

  @Test
  public void projectLifeCycleListeners_onProjectsDeleted_called_even_if_delete_fails() {
    userSession.logIn().addPermission(ADMINISTER, org1);
    ComponentDto project1 = db.components().insertPrivateProject(org1);
    ComponentDto project2 = db.components().insertPrivateProject(org1);
    ComponentDto project3 = db.components().insertPrivateProject(org1);
    ComponentCleanerService componentCleanerService = mock(ComponentCleanerService.class);
    RuntimeException expectedException = new RuntimeException("Faking delete failing on 2nd project");
    doNothing()
      .doThrow(expectedException)
      .when(componentCleanerService)
      .delete(any(), any(ProjectDto.class));

    try {
      ws.newRequest()
        .setParam("organization", org1.getKey())
        .setParam("projects", project1.getDbKey() + "," + project2.getDbKey() + "," + project3.getDbKey())
        .execute();
    } catch (RuntimeException e) {
      assertThat(e).isSameAs(expectedException);
      verifyListenersOnProjectsDeleted(project1, project2, project3);
    }
  }

  @Test
  public void organization_administrator_deletes_projects_by_keys_in_his_organization() {
    userSession.logIn().addPermission(ADMINISTER, org1);
    ComponentDto toDelete = db.components().insertPrivateProject(org1);
    ComponentDto cantBeDeleted = db.components().insertPrivateProject(org2);

    ws.newRequest()
      .setParam("organization", org1.getKey())
      .setParam("projects", toDelete.getDbKey() + "," + cantBeDeleted.getDbKey())
      .execute();

    verifyComponentDeleted(toDelete);
    verifyListenersOnProjectsDeleted(toDelete);
  }

  @Test
  public void throw_UnauthorizedException_if_not_logged_in() {
    expectedException.expect(UnauthorizedException.class);
    expectedException.expectMessage("Authentication is required");

    ws.newRequest()
      .setParam("ids", "whatever-the-uuid").execute();

    verifyNoDeletions();
    verifyZeroInteractions(projectLifeCycleListeners);
  }

  @Test
  public void throw_ForbiddenException_if_param_organization_is_not_set_and_not_system_administrator() {
    userSession.logIn().setNonSystemAdministrator();

    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");

    ws.newRequest()
      .setParam("ids", "whatever-the-uuid").execute();

    verifyNoDeletions();
    verifyZeroInteractions(projectLifeCycleListeners);
  }

  @Test
  public void throw_ForbiddenException_if_param_organization_is_set_but_not_organization_administrator() {
    userSession.logIn();

    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");

    ws.newRequest()
      .setParam("organization", org1.getKey())
      .setParam("ids", "whatever-the-uuid")
      .execute();

    verifyNoDeletions();
    verifyZeroInteractions(projectLifeCycleListeners);
  }

  private void verifyProjectDeleted(ComponentDto... projects) {
    ArgumentCaptor<ProjectDto> argument = ArgumentCaptor.forClass(ProjectDto.class);
    verify(componentCleanerService, times(projects.length)).delete(any(DbSession.class), argument.capture());

    for (ComponentDto project : projects) {
      assertThat(argument.getAllValues()).extracting(ProjectDto::getUuid).contains(project.uuid());
    }
  }

  private void verifyComponentDeleted(ComponentDto... projects) {
    ArgumentCaptor<ComponentDto> argument = ArgumentCaptor.forClass(ComponentDto.class);
    verify(componentCleanerService, times(projects.length)).delete(any(DbSession.class), argument.capture());

    for (ComponentDto project : projects) {
      assertThat(argument.getAllValues()).extracting(ComponentDto::uuid).contains(project.uuid());
    }
  }

  private void verifyNoDeletions() {
    verifyZeroInteractions(componentCleanerService);
  }

  private void verifyListenersOnProjectsDeleted(ComponentDto... components) {
    verify(projectLifeCycleListeners)
      .onProjectsDeleted(Arrays.stream(components).map(Project::from).collect(Collectors.toSet()));
  }
}
