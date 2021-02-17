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
package org.sonar.server.qualitygate.ws;

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.qualitygate.QGateWithOrgDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.TestComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER_QUALITY_GATES;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER_QUALITY_PROFILES;

public class DeselectActionTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester db = DbTester.create();

  private DbClient dbClient = db.getDbClient();
  private TestDefaultOrganizationProvider organizationProvider = TestDefaultOrganizationProvider.from(db);
  private ComponentFinder componentFinder = TestComponentFinder.from(db);
  private DeselectAction underTest = new DeselectAction(dbClient, new QualityGatesWsSupport(db.getDbClient(), userSession, organizationProvider, componentFinder));
  private WsActionTester ws = new WsActionTester(underTest);

  @Test
  public void deselect_by_key() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);
    associateProjectToQualityGate(project, qualityGate);

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();

    assertDeselected(project);
  }

  @Test
  public void project_admin() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);
    associateProjectToQualityGate(project, qualityGate);
    userSession.logIn().addProjectPermission(UserRole.ADMIN, project);

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();

    assertDeselected(project);
  }

  @Test
  public void other_project_should_not_be_updated() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);
    associateProjectToQualityGate(project, qualityGate);
    // Another project
    ProjectDto anotherProject = db.components().insertPrivateProjectDto(organization);
    associateProjectToQualityGate(anotherProject, qualityGate);

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();

    assertDeselected(project);
    assertSelected(qualityGate, anotherProject);
  }

  @Test
  public void default_organization_is_used_when_no_organization_parameter() {
    OrganizationDto organization = db.getDefaultOrganization();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);
    associateProjectToQualityGate(project, qualityGate);

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .execute();

    assertDeselected(project);
  }

  @Test
  public void fail_when_project_belongs_to_another_organization() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);
    OrganizationDto anotherOrganization = db.organizations().insert();
    ComponentDto project = db.components().insertPrivateProject(anotherOrganization);

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage(format("Project '%s' doesn't exist in organization '%s'", project.getKey(), organization.getKey()));

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_no_project_id() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);

    expectedException.expect(NotFoundException.class);

    ws.newRequest()
      .setParam("projectId", valueOf((Long) 1L))
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_no_project_key() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);

    expectedException.expect(NotFoundException.class);

    ws.newRequest()
      .setParam("projectKey", "unknown")
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_anonymous() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPrivateProject(organization);
    userSession.anonymous();

    expectedException.expect(ForbiddenException.class);
    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_not_project_admin() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPrivateProject(organization);
    userSession.logIn().addProjectPermission(UserRole.ISSUE_ADMIN, project);

    expectedException.expect(ForbiddenException.class);

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_not_quality_gates_admin() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(ADMINISTER_QUALITY_GATES, organization);
    ComponentDto project = db.components().insertPrivateProject(organization);

    userSession.logIn().addPermission(ADMINISTER_QUALITY_PROFILES, project.getOrganizationUuid());

    expectedException.expect(ForbiddenException.class);

    ws.newRequest()
      .setParam("projectKey", project.getKey())
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_using_branch_db_key() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPublicProject(organization);
    userSession.logIn().addProjectPermission(UserRole.ADMIN, project);
    ComponentDto branch = db.components().insertProjectBranch(project);

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage(format("Project '%s' not found", branch.getDbKey()));

    ws.newRequest()
      .setParam("projectKey", branch.getDbKey())
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void fail_when_using_branch_id() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPublicProject(organization);
    userSession.logIn().addProjectPermission(UserRole.ADMIN, project);
    ComponentDto branch = db.components().insertProjectBranch(project);

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage(format("Project '%s' not found", branch.getId()));

    ws.newRequest()
      .setParam("projectId", String.valueOf(branch.getId()))
      .setParam("organization", organization.getKey())
      .execute();
  }

  @Test
  public void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.description()).isNotEmpty();
    assertThat(def.isPost()).isTrue();
    assertThat(def.since()).isEqualTo("4.3");
    assertThat(def.changelog()).extracting(Change::getVersion, Change::getDescription).containsExactly(
      tuple("6.6", "The parameter 'gateId' was removed"));

    assertThat(def.params())
      .extracting(WebService.Param::key, WebService.Param::isRequired)
      .containsExactlyInAnyOrder(
        tuple("projectKey", false),
        tuple("projectId", false),
        tuple("organization", false));
  }

  private void associateProjectToQualityGate(ProjectDto project, QualityGateDto qualityGate) {
    db.qualityGates().associateProjectToQualityGate(project, qualityGate);
    db.commit();
  }

  private void assertDeselected(ProjectDto project) {
    Optional<String> qGateUuid = db.qualityGates().selectQGateUuidByComponentUuid(project.getUuid());
    assertThat(qGateUuid)
      .isNotNull()
      .isEmpty();
  }

  private void assertSelected(QGateWithOrgDto qualityGate, ProjectDto project) {
    Optional<String> qGateUuid = db.qualityGates().selectQGateUuidByComponentUuid(project.getUuid());
    assertThat(qGateUuid)
      .isNotNull()
      .isNotEmpty()
      .hasValue(qualityGate.getUuid());
  }
}
