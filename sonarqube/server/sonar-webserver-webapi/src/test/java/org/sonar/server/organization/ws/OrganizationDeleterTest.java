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
package org.sonar.server.organization.ws;

import com.google.common.collect.ImmutableSet;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.utils.System2;
import org.sonar.core.util.UuidFactoryFast;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.Pagination;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationQuery;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.qualitygate.QGateWithOrgDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.db.qualityprofile.QProfileDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.db.webhook.WebhookDto;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.es.ProjectIndexers;
import org.sonar.server.es.SearchOptions;
import org.sonar.server.organization.BillingValidations;
import org.sonar.server.organization.BillingValidationsProxy;
import org.sonar.server.project.Project;
import org.sonar.server.project.ProjectLifeCycleListeners;
import org.sonar.server.qualityprofile.QProfileFactoryImpl;
import org.sonar.server.qualityprofile.index.ActiveRuleIndexer;
import org.sonar.server.user.index.UserIndex;
import org.sonar.server.user.index.UserIndexer;
import org.sonar.server.user.index.UserQuery;

import static com.google.common.collect.ImmutableList.of;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sonar.core.util.stream.MoreCollectors.toSet;
import static org.sonar.db.user.UserTesting.newUserDto;
import static org.sonar.server.organization.ws.OrganizationDeleter.PAGE_SIZE;

@RunWith(DataProviderRunner.class)
public class OrganizationDeleterTest {

  @Rule
  public final DbTester db = DbTester.create(new System2()).setDisableDefaultOrganization(true);
  private final DbClient dbClient = db.getDbClient();
  private final DbSession dbSession = db.getSession();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Rule
  public final EsTester es = EsTester.create();
  private final EsClient esClient = es.client();
  private ResourceTypes mockResourceTypes = mock(ResourceTypes.class);
  private final ComponentCleanerService componentCleanerService = spy(new ComponentCleanerService(db.getDbClient(), mockResourceTypes, mock(ProjectIndexers.class)));
  private final UserIndex userIndex = new UserIndex(esClient, System2.INSTANCE);
  private final UserIndexer userIndexer = new UserIndexer(dbClient, esClient);
  private final ProjectLifeCycleListeners projectLifeCycleListeners = mock(ProjectLifeCycleListeners.class);
  private final BillingValidationsProxy billingValidations = mock(BillingValidationsProxy.class);

  private final OrganizationDeleter underTest = new OrganizationDeleter(dbClient, componentCleanerService, userIndexer,
    new QProfileFactoryImpl(dbClient, UuidFactoryFast.getInstance(), new System2(), new ActiveRuleIndexer(dbClient, esClient)),
    projectLifeCycleListeners,
    billingValidations);

  @Test
  public void delete_specified_organization() {
    OrganizationDto organization = db.organizations().insert();

    underTest.delete(dbSession, organization);

    verifyOrganizationDoesNotExist(organization);
    verify(projectLifeCycleListeners).onProjectsDeleted(emptySet());
  }

  @Test
  public void delete_webhooks_of_organization_if_exist() {
    OrganizationDto organization = db.organizations().insert();
    db.webhooks().insertWebhook(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);
    WebhookDto projectWebhook = db.webhooks().insertWebhook(project);
    db.webhookDelivery().insert(projectWebhook);

    underTest.delete(dbSession, organization);

    assertThat(db.countRowsOfTable(db.getSession(), "webhooks")).isZero();
    assertThat(db.countRowsOfTable(db.getSession(), "webhook_deliveries")).isZero();
  }

  @Test
  public void clear_user_homepage_on_organization_if_exists() {
    OrganizationDto organization = db.organizations().insert();
    UserDto user = dbClient.userDao().insert(dbSession, newUserDto().setHomepageType("ORGANIZATION").setHomepageParameter(organization.getUuid()));
    dbSession.commit();

    underTest.delete(dbSession, organization);

    UserDto userReloaded = dbClient.userDao().selectUserById(dbSession, user.getId());
    assertThat(userReloaded.getHomepageType()).isNull();
    assertThat(userReloaded.getHomepageParameter()).isNull();
  }

  @Test
  public void clear_project_homepage_on_organization_if_exists() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPrivateProject(organization);
    UserDto user = dbClient.userDao().insert(dbSession,
      newUserDto().setHomepageType("PROJECT").setHomepageParameter(project.uuid()));
    dbSession.commit();

    underTest.delete(dbSession, organization);

    UserDto userReloaded = dbClient.userDao().selectUserById(dbSession, user.getId());
    assertThat(userReloaded.getHomepageType()).isNull();
    assertThat(userReloaded.getHomepageParameter()).isNull();
    verify(projectLifeCycleListeners).onProjectsDeleted(ImmutableSet.of(Project.from(project)));
  }

  @Test
  @UseDataProvider("OneOrMoreIterations")
  public void delete_components(int numberOfIterations) {
    OrganizationDto organization = db.organizations().insert();
    Set<ComponentDto> projects = IntStream.range(0, numberOfIterations).mapToObj(i -> {
      ComponentDto project = db.components().insertPrivateProject(organization);
      ComponentDto module = db.components().insertComponent(ComponentTesting.newModuleDto(project));
      ComponentDto directory = db.components().insertComponent(ComponentTesting.newDirectory(module, "a/b" + i));
      ComponentDto file = db.components().insertComponent(ComponentTesting.newFileDto(module, directory));
      ComponentDto branch = db.components().insertProjectBranch(project);
      return project;
    }).collect(toSet());

    underTest.delete(dbSession, organization);

    verifyOrganizationDoesNotExist(organization);
    assertThat(db.countRowsOfTable(db.getSession(), "projects")).isZero();
    verify(projectLifeCycleListeners).onProjectsDeleted(projects.stream().map(Project::from).collect(toSet()));
  }

  @DataProvider
  public static Object[][] OneOrMoreIterations() {
    return new Object[][] {
      {1},
      {1 + new Random().nextInt(10)},
    };
  }

  @Test
  public void delete_branches() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPublicProject(organization);
    ComponentDto branch = db.components().insertProjectBranch(project);

    underTest.delete(dbSession, organization);

    verifyOrganizationDoesNotExist(organization);
    assertThat(db.countRowsOfTable(db.getSession(), "projects")).isZero();
    assertThat(db.countRowsOfTable(db.getSession(), "project_branches")).isZero();
    verify(projectLifeCycleListeners).onProjectsDeleted(ImmutableSet.of(Project.from(project)));
  }

  @Test
  public void delete_permissions_templates_and_permissions_and_groups() {
    OrganizationDto org = db.organizations().insert();
    OrganizationDto otherOrg = db.organizations().insert();

    UserDto user1 = db.users().insertUser();
    UserDto user2 = db.users().insertUser();
    GroupDto group1 = db.users().insertGroup(org);
    GroupDto group2 = db.users().insertGroup(org);
    GroupDto otherGroup1 = db.users().insertGroup(otherOrg);
    GroupDto otherGroup2 = db.users().insertGroup(otherOrg);

    ComponentDto projectDto = db.components().insertPublicProject(org);
    ComponentDto otherProjectDto = db.components().insertPublicProject(otherOrg);

    db.users().insertPermissionOnAnyone(org, "u1");
    db.users().insertPermissionOnAnyone(otherOrg, "not deleted u1");
    db.users().insertPermissionOnUser(org, user1, "u2");
    db.users().insertPermissionOnUser(otherOrg, user1, "not deleted u2");
    db.users().insertPermissionOnGroup(group1, "u3");
    db.users().insertPermissionOnGroup(otherGroup1, "not deleted u3");
    db.users().insertProjectPermissionOnAnyone("u4", projectDto);
    db.users().insertProjectPermissionOnAnyone("not deleted u4", otherProjectDto);
    db.users().insertProjectPermissionOnGroup(group1, "u5", projectDto);
    db.users().insertProjectPermissionOnGroup(otherGroup1, "not deleted u5", otherProjectDto);
    db.users().insertProjectPermissionOnUser(user1, "u6", projectDto);
    db.users().insertProjectPermissionOnUser(user1, "not deleted u6", otherProjectDto);

    PermissionTemplateDto templateDto = db.permissionTemplates().insertTemplate(org);
    PermissionTemplateDto otherTemplateDto = db.permissionTemplates().insertTemplate(otherOrg);

    underTest.delete(dbSession, org);

    verifyOrganizationDoesNotExist(org);
    assertThat(dbClient.groupDao().selectByIds(dbSession, of(group1.getId(), otherGroup1.getId(), group2.getId(), otherGroup2.getId())))
      .extracting(GroupDto::getId)
      .containsOnly(otherGroup1.getId(), otherGroup2.getId());
    assertThat(dbClient.permissionTemplateDao().selectByUuid(dbSession, templateDto.getUuid()))
      .isNull();
    assertThat(dbClient.permissionTemplateDao().selectByUuid(dbSession, otherTemplateDto.getUuid()))
      .isNotNull();
    assertThat(db.select("select role as \"role\" from USER_ROLES"))
      .extracting(row -> (String) row.get("role"))
      .doesNotContain("u2", "u6")
      .contains("not deleted u2", "not deleted u6");
    assertThat(db.select("select role as \"role\" from GROUP_ROLES"))
      .extracting(row -> (String) row.get("role"))
      .doesNotContain("u1", "u3", "u4", "u5")
      .contains("not deleted u1", "not deleted u3", "not deleted u4", "not deleted u5");
    verify(projectLifeCycleListeners).onProjectsDeleted(ImmutableSet.of(Project.from(projectDto)));
  }

  @Test
  public void delete_members() {
    OrganizationDto org = db.organizations().insert();
    OrganizationDto otherOrg = db.organizations().insert();
    UserDto user1 = db.users().insertUser();
    UserDto user2 = db.users().insertUser();
    db.organizations().addMember(org, user1);
    db.organizations().addMember(otherOrg, user1);
    db.organizations().addMember(org, user2);
    userIndexer.commitAndIndex(db.getSession(), asList(user1, user2));

    underTest.delete(dbSession, org);

    verifyOrganizationDoesNotExist(org);
    assertThat(db.getDbClient().organizationMemberDao().select(db.getSession(), org.getUuid(), user1.getId())).isNotPresent();
    assertThat(db.getDbClient().organizationMemberDao().select(db.getSession(), org.getUuid(), user2.getId())).isNotPresent();
    assertThat(db.getDbClient().organizationMemberDao().select(db.getSession(), otherOrg.getUuid(), user1.getId())).isPresent();
    assertThat(userIndex.search(UserQuery.builder().setOrganizationUuid(org.getUuid()).build(), new SearchOptions()).getTotal()).isEqualTo(0);
    assertThat(userIndex.search(UserQuery.builder().setOrganizationUuid(otherOrg.getUuid()).build(), new SearchOptions()).getTotal()).isEqualTo(1);
    verify(projectLifeCycleListeners).onProjectsDeleted(emptySet());
  }

  @Test
  public void delete_quality_profiles() {
    OrganizationDto org = db.organizations().insert();
    OrganizationDto otherOrg = db.organizations().insert();
    QProfileDto profileInOrg = db.qualityProfiles().insert(org);
    QProfileDto profileInOtherOrg = db.qualityProfiles().insert(otherOrg);

    underTest.delete(dbSession, org);

    verifyOrganizationDoesNotExist(org);
    assertThat(db.select("select uuid as \"profileKey\" from org_qprofiles"))
      .extracting(row -> (String) row.get("profileKey"))
      .containsOnly(profileInOtherOrg.getKee());
  }

  @Test
  public void delete_quality_gates() {
    QualityGateDto builtInQualityGate = db.qualityGates().insertBuiltInQualityGate();
    OrganizationDto organization = db.organizations().insert();
    db.qualityGates().associateQualityGateToOrganization(builtInQualityGate, organization);
    OrganizationDto otherOrganization = db.organizations().insert();
    db.qualityGates().associateQualityGateToOrganization(builtInQualityGate, otherOrganization);
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    QGateWithOrgDto qualityGateInOtherOrg = db.qualityGates().insertQualityGate(otherOrganization);

    underTest.delete(dbSession, organization);

    verifyOrganizationDoesNotExist(organization);
    assertThat(db.select("select uuid as \"uuid\" from quality_gates"))
      .extracting(row -> (String) row.get("uuid"))
      .containsExactlyInAnyOrder(qualityGateInOtherOrg.getUuid(), builtInQualityGate.getUuid());
    assertThat(db.select("select organization_uuid as \"organizationUuid\" from org_quality_gates"))
      .extracting(row -> (String) row.get("organizationUuid"))
      .containsOnly(otherOrganization.getUuid());

    // Check built-in quality gate is still available in other organization
    assertThat(db.getDbClient().qualityGateDao().selectByOrganizationAndName(db.getSession(), otherOrganization, "Sonar way")).isNotNull();
    verify(projectLifeCycleListeners).onProjectsDeleted(emptySet());
  }

  @Test
  public void projectLifeCycleListener_are_notified_even_if_deletion_of_a_project_throws_an_Exception() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto[] components = new ComponentDto[] {
      db.components().insertPublicProject(organization),
      db.components().insertPublicProject(organization),
      db.components().insertPublicProject(organization)
    };
    ProjectDto[] projects = Arrays.stream(components).map(c -> dbClient.projectDao().selectByUuid(dbSession, c.uuid()).get()).toArray(ProjectDto[]::new);

    RuntimeException expectedException = new RuntimeException("Faking deletion of 2nd project throwing an exception");
    doThrow(expectedException).when(componentCleanerService).delete(any(), anyList());
    exception.expect(RuntimeException.class);
    exception.expectMessage(expectedException.getMessage());

    underTest.delete(dbSession, organization);

    verify(projectLifeCycleListeners).onProjectsDeleted(Arrays.stream(projects).map(Project::from).collect(toSet()));
  }

  @Test
  public void call_billing_validation_on_delete() {
    OrganizationDto organization = db.organizations().insert();

    underTest.delete(dbSession, organization);

    verify(billingValidations).onDelete(any(BillingValidations.Organization.class));
  }

  @Test
  public void delete_organization_alm_binding() {
    OrganizationDto organization = db.organizations().insert();
    db.alm().insertOrganizationAlmBinding(organization, db.alm().insertAlmAppInstall(), true);

    underTest.delete(dbSession, organization);

    assertThat(db.getDbClient().organizationAlmBindingDao().selectByOrganization(db.getSession(), organization)).isNotPresent();
  }

  @Test
  @UseDataProvider("queriesAndUnmatchedOrganizationKeys")
  public void delete_organizations_matched_by_query(OrganizationQuery query, Collection<String> unmatchedOrgKeys) {
    db.organizations().insert(o -> o.setKey("org1"));
    db.organizations().insert(o -> o.setKey("org2"));
    db.organizations().insert(o -> o.setKey("org3"));

    underTest.deleteByQuery(query);

    assertThat(dbClient.organizationDao().selectByQuery(db.getSession(), OrganizationQuery.returnAll(), Pagination.all()))
      .extracting(OrganizationDto::getKey)
      .containsExactlyInAnyOrderElementsOf(unmatchedOrgKeys);
  }

  @DataProvider
  public static Object[][] queriesAndUnmatchedOrganizationKeys() {
    return new Object[][] {
      {OrganizationQuery.returnAll(), Collections.emptyList()},
      {OrganizationQuery.newOrganizationQueryBuilder().setKeys(singleton("nonexistent")).build(), Arrays.asList("org1", "org2", "org3")},
      {OrganizationQuery.newOrganizationQueryBuilder().setKeys(singleton("org1")).build(), Arrays.asList("org2", "org3")},
    };
  }

  @Test
  public void delete_organizations_for_all_query_pages() {
    int orgsCountGreaterThanPageSize = PAGE_SIZE + 1;

    IntStream.range(0, orgsCountGreaterThanPageSize).forEach(ignored -> db.organizations().insert());

    OrganizationQuery query = OrganizationQuery.returnAll();
    assertThat(dbClient.organizationDao().countByQuery(db.getSession(), query)).isEqualTo(orgsCountGreaterThanPageSize);

    underTest.deleteByQuery(query);

    assertThat(dbClient.organizationDao().countByQuery(db.getSession(), query)).isZero();
  }

  private void verifyOrganizationDoesNotExist(OrganizationDto organization) {
    assertThat(db.getDbClient().organizationDao().selectByKey(dbSession, organization.getKey())).isEmpty();
  }
}
