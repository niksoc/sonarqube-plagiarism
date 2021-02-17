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

import com.google.common.base.Strings;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.component.ComponentUpdater;
import org.sonar.server.es.TestProjectIndexers;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.favorite.FavoriteUpdater;
import org.sonar.server.l18n.I18nRule;
import org.sonar.server.organization.BillingValidations;
import org.sonar.server.organization.BillingValidations.BillingValidationsException;
import org.sonar.server.organization.BillingValidationsProxy;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.permission.PermissionTemplateService;
import org.sonar.server.project.ws.CreateAction.CreateRequest;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Projects.CreateWsResponse;
import org.sonarqube.ws.Projects.CreateWsResponse.Project;

import static java.util.Optional.ofNullable;
import static java.util.stream.IntStream.rangeClosed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.db.permission.OrganizationPermission.PROVISION_PROJECTS;
import static org.sonar.server.project.Visibility.PRIVATE;
import static org.sonar.server.project.ws.ProjectsWsSupport.PARAM_ORGANIZATION;
import static org.sonar.test.JsonAssert.assertJson;
import static org.sonarqube.ws.client.WsRequest.Method.POST;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_NAME;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_PROJECT;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_VISIBILITY;

public class CreateActionTest {

  private static final String DEFAULT_PROJECT_KEY = "project-key";
  private static final String DEFAULT_PROJECT_NAME = "project-name";

  private System2 system2 = System2.INSTANCE;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(system2);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public I18nRule i18n = new I18nRule().put("qualifier.TRK", "Project");

  private DefaultOrganizationProvider defaultOrganizationProvider = TestDefaultOrganizationProvider.from(db);
  private BillingValidationsProxy billingValidations = mock(BillingValidationsProxy.class);
  private TestProjectIndexers projectIndexers = new TestProjectIndexers();
  private PermissionTemplateService permissionTemplateService = mock(PermissionTemplateService.class);
  private WsActionTester ws = new WsActionTester(
    new CreateAction(
      new ProjectsWsSupport(db.getDbClient(), defaultOrganizationProvider, billingValidations),
      db.getDbClient(), userSession,
      new ComponentUpdater(db.getDbClient(), i18n, system2, permissionTemplateService, new FavoriteUpdater(db.getDbClient()),
        projectIndexers)));

  @Test
  public void create_project() {
    userSession.addPermission(PROVISION_PROJECTS, db.getDefaultOrganization());

    CreateWsResponse response = call(CreateRequest.builder()
      .setProjectKey(DEFAULT_PROJECT_KEY)
      .setName(DEFAULT_PROJECT_NAME)
      .build());

    assertThat(response.getProject())
      .extracting(Project::getKey, Project::getName, Project::getQualifier, Project::getVisibility)
      .containsOnly(DEFAULT_PROJECT_KEY, DEFAULT_PROJECT_NAME, "TRK", "public");
    assertThat(db.getDbClient().componentDao().selectByKey(db.getSession(), DEFAULT_PROJECT_KEY).get())
      .extracting(ComponentDto::getDbKey, ComponentDto::name, ComponentDto::qualifier, ComponentDto::scope, ComponentDto::isPrivate, ComponentDto::getMainBranchProjectUuid)
      .containsOnly(DEFAULT_PROJECT_KEY, DEFAULT_PROJECT_NAME, "TRK", "PRJ", false, null);
  }

  @Test
  public void apply_project_visibility_public() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(PROVISION_PROJECTS, organization);

    CreateWsResponse result = ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .setParam("visibility", "public")
      .executeProtobuf(CreateWsResponse.class);

    assertThat(result.getProject().getVisibility()).isEqualTo("public");
  }

  @Test
  public void apply_project_visibility_private() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(PROVISION_PROJECTS, organization);

    CreateWsResponse result = ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .setParam("visibility", PRIVATE.getLabel())
      .executeProtobuf(CreateWsResponse.class);

    assertThat(result.getProject().getVisibility()).isEqualTo("private");
  }

  @Test
  public void apply_default_project_visibility_public() {
    OrganizationDto organization = db.organizations().insert();
    db.organizations().setNewProjectPrivate(organization, false);
    userSession.addPermission(PROVISION_PROJECTS, organization);

    CreateWsResponse result = ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .executeProtobuf(CreateWsResponse.class);

    assertThat(result.getProject().getVisibility()).isEqualTo("public");
  }

  @Test
  public void apply_default_project_visibility_private() {
    OrganizationDto organization = db.organizations().insert();
    db.organizations().setNewProjectPrivate(organization, true);
    userSession.addPermission(PROVISION_PROJECTS, organization);

    CreateWsResponse result = ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .executeProtobuf(CreateWsResponse.class);

    assertThat(result.getProject().getVisibility()).isEqualTo("private");
  }

  @Test
  public void does_not_fail_to_create_public_projects_when_organization_is_not_allowed_to_use_private_projects() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(PROVISION_PROJECTS, organization);
    doThrow(new BillingValidationsException("This organization cannot use project private")).when(billingValidations)
      .checkCanUpdateProjectVisibility(any(BillingValidations.Organization.class), eq(true));

    CreateWsResponse result = ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .setParam("visibility", "public")
      .executeProtobuf(CreateWsResponse.class);

    assertThat(result.getProject().getVisibility()).isEqualTo("public");
  }

  @Test
  public void abbreviate_project_name_if_very_long() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(PROVISION_PROJECTS, organization);
    String longName = Strings.repeat("a", 1_000);

    ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", longName)
      .setParam("organization", organization.getKey())
      .executeProtobuf(CreateWsResponse.class);

    assertThat(db.getDbClient().componentDao().selectByKey(db.getSession(), DEFAULT_PROJECT_KEY).get().name())
      .isEqualTo(Strings.repeat("a", 497) + "...");
  }

  @Test
  public void add_project_to_user_favorites_if_project_creator_is_defined_in_permission_template() {
    OrganizationDto organization = db.organizations().insert();
    UserDto user = db.users().insertUser();
    when(permissionTemplateService.hasDefaultTemplateWithPermissionOnProjectCreator(any(DbSession.class), any(ComponentDto.class))).thenReturn(true);
    userSession.logIn(user).addPermission(PROVISION_PROJECTS, organization);

    ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .executeProtobuf(CreateWsResponse.class);

    ComponentDto project = db.getDbClient().componentDao().selectByKey(db.getSession(), DEFAULT_PROJECT_KEY).get();
    assertThat(db.favorites().hasFavorite(project, user.getId())).isTrue();
  }

  @Test
  public void do_not_add_project_to_user_favorites_if_project_creator_is_defined_in_permission_template_and_already_100_favorites() {
    OrganizationDto organization = db.organizations().insert();
    UserDto user = db.users().insertUser();
    when(permissionTemplateService.hasDefaultTemplateWithPermissionOnProjectCreator(any(DbSession.class), any(ComponentDto.class))).thenReturn(true);
    rangeClosed(1, 100).forEach(i -> db.favorites().add(db.components().insertPrivateProject(), user.getId()));
    userSession.logIn(user).addPermission(PROVISION_PROJECTS, organization);

    ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .executeProtobuf(CreateWsResponse.class);

    ComponentDto project = db.getDbClient().componentDao().selectByKey(db.getSession(), DEFAULT_PROJECT_KEY).get();
    assertThat(db.favorites().hasNoFavorite(project)).isTrue();
  }

  @Test
  public void fail_to_create_private_projects_when_organization_is_not_allowed_to_use_private_projects() {
    OrganizationDto organization = db.organizations().insert();
    userSession.addPermission(PROVISION_PROJECTS, organization);
    doThrow(new BillingValidationsException("This organization cannot use project private")).when(billingValidations)
      .checkCanUpdateProjectVisibility(any(BillingValidations.Organization.class), eq(true));

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("This organization cannot use project private");

    ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .setParam("organization", organization.getKey())
      .setParam("visibility", "private")
      .executeProtobuf(CreateWsResponse.class);
  }

  @Test
  public void fail_when_project_already_exists() {
    OrganizationDto organization = db.organizations().insert();
    db.components().insertPublicProject(project -> project.setDbKey(DEFAULT_PROJECT_KEY));
    userSession.addPermission(PROVISION_PROJECTS, organization);

    expectedException.expect(BadRequestException.class);

    call(CreateRequest.builder()
      .setOrganization(organization.getKey())
      .setProjectKey(DEFAULT_PROJECT_KEY)
      .setName(DEFAULT_PROJECT_NAME)
      .build());
  }

  @Test
  public void properly_fail_when_invalid_project_key() {
    userSession.addPermission(PROVISION_PROJECTS, db.getDefaultOrganization());

    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Malformed key for Project: 'project Key'. It cannot be empty nor contain whitespaces.");

    call(CreateRequest.builder()
      .setProjectKey("project Key")
      .setName(DEFAULT_PROJECT_NAME)
      .build());
  }

  @Test
  public void fail_when_missing_project_parameter() {
    userSession.addPermission(PROVISION_PROJECTS, db.getDefaultOrganization());

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The 'project' parameter is missing");

    call(null, null, DEFAULT_PROJECT_NAME);
  }

  @Test
  public void fail_when_missing_name_parameter() {
    userSession.addPermission(PROVISION_PROJECTS, db.getDefaultOrganization());

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The 'name' parameter is missing");

    call(null, DEFAULT_PROJECT_KEY, null);
  }

  @Test
  public void fail_when_missing_create_project_permission() {
    expectedException.expect(ForbiddenException.class);

    call(CreateRequest.builder().setProjectKey(DEFAULT_PROJECT_KEY).setName(DEFAULT_PROJECT_NAME).build());
  }

  @Test
  public void test_example() {
    userSession.addPermission(PROVISION_PROJECTS, db.getDefaultOrganization());

    String result = ws.newRequest()
      .setParam("project", DEFAULT_PROJECT_KEY)
      .setParam("name", DEFAULT_PROJECT_NAME)
      .execute().getInput();

    assertJson(result).isSimilarTo(getClass().getResource("create-example.json"));
  }

  @Test
  public void definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.key()).isEqualTo("create");
    assertThat(definition.since()).isEqualTo("4.0");
    assertThat(definition.isInternal()).isFalse();
    assertThat(definition.responseExampleAsString()).isNotEmpty();

    assertThat(definition.params()).extracting(WebService.Param::key).containsExactlyInAnyOrder(
      PARAM_VISIBILITY,
      PARAM_ORGANIZATION,
      PARAM_NAME,
      PARAM_PROJECT);

    WebService.Param organization = definition.param(PARAM_ORGANIZATION);
    assertThat(organization.description()).isEqualTo("The key of the organization");
    assertThat(organization.isInternal()).isTrue();
    assertThat(organization.isRequired()).isFalse();
    assertThat(organization.since()).isEqualTo("6.3");

    WebService.Param visibilityParam = definition.param(PARAM_VISIBILITY);
    assertThat(visibilityParam.description()).isNotEmpty();
    assertThat(visibilityParam.isInternal()).isFalse();
    assertThat(visibilityParam.isRequired()).isFalse();
    assertThat(visibilityParam.since()).isEqualTo("6.4");
    assertThat(visibilityParam.possibleValues()).containsExactlyInAnyOrder("private", "public");

    WebService.Param project = definition.param(PARAM_PROJECT);
    assertThat(project.isRequired()).isTrue();
    assertThat(project.maximumLength()).isEqualTo(400);

    WebService.Param name = definition.param(PARAM_NAME);
    assertThat(name.isRequired()).isTrue();
    assertThat(name.description()).isEqualTo("Name of the project. If name is longer than 500, it is abbreviated.");
  }

  @Test
  public void fail_when_set_null_project_name_on_create_request_builder() {
    expectedException.expect(NullPointerException.class);

    CreateRequest.builder()
      .setProjectKey(DEFAULT_PROJECT_KEY)
      .setName(null);
  }

  @Test
  public void fail_when_set_null_project_key_on_create_request_builder() {
    expectedException.expect(NullPointerException.class);

    CreateRequest.builder()
      .setProjectKey(null)
      .setName(DEFAULT_PROJECT_NAME);
  }

  @Test
  public void fail_when_project_key_not_set_on_create_request_builder() {
    expectedException.expect(NullPointerException.class);
    CreateRequest.builder()
      .setName(DEFAULT_PROJECT_NAME)
      .build();
  }

  @Test
  public void fail_when_project_name_not_set_on_create_request_builder() {
    expectedException.expect(NullPointerException.class);

    CreateRequest.builder()
      .setProjectKey(DEFAULT_PROJECT_KEY)
      .build();
  }

  private CreateWsResponse call(CreateRequest request) {
    return call(request.getOrganization(), request.getProjectKey(), request.getName());
  }

  private CreateWsResponse call(@Nullable String organization, @Nullable String projectKey, @Nullable String projectName) {
    TestRequest httpRequest = ws.newRequest()
      .setMethod(POST.name());
    ofNullable(organization).ifPresent(org -> httpRequest.setParam("organization", org));
    ofNullable(projectKey).ifPresent(key -> httpRequest.setParam("project", key));
    ofNullable(projectName).ifPresent(name -> httpRequest.setParam("name", name));
    return httpRequest.executeProtobuf(CreateWsResponse.class);
  }

}
