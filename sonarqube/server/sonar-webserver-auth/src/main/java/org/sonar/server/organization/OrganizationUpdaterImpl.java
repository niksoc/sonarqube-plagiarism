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
package org.sonar.server.organization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.DefaultTemplates;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationMemberDto;
import org.sonar.db.permission.GroupPermissionDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.db.qualityprofile.DefaultQProfileDto;
import org.sonar.db.qualityprofile.OrgQProfileDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserGroupDto;
import org.sonar.server.permission.PermissionService;
import org.sonar.server.qualityprofile.BuiltInQProfile;
import org.sonar.server.qualityprofile.BuiltInQProfileRepository;
import org.sonar.server.qualityprofile.QProfileName;
import org.sonar.server.user.index.UserIndexer;
import org.sonar.server.usergroups.DefaultGroupCreator;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.sonar.api.web.UserRole.ADMIN;
import static org.sonar.api.web.UserRole.CODEVIEWER;
import static org.sonar.api.web.UserRole.ISSUE_ADMIN;
import static org.sonar.api.web.UserRole.SECURITYHOTSPOT_ADMIN;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.core.util.stream.MoreCollectors.uniqueIndex;
import static org.sonar.db.organization.OrganizationDto.Subscription.FREE;
import static org.sonar.db.permission.OrganizationPermission.SCAN;

public class OrganizationUpdaterImpl implements OrganizationUpdater {

  private final DbClient dbClient;
  private final System2 system2;
  private final UuidFactory uuidFactory;
  private final OrganizationValidation organizationValidation;
  private final BuiltInQProfileRepository builtInQProfileRepository;
  private final DefaultGroupCreator defaultGroupCreator;
  private final UserIndexer userIndexer;
  private final PermissionService permissionService;

  public OrganizationUpdaterImpl(DbClient dbClient, System2 system2, UuidFactory uuidFactory,
    OrganizationValidation organizationValidation, UserIndexer userIndexer,
    BuiltInQProfileRepository builtInQProfileRepository, DefaultGroupCreator defaultGroupCreator, PermissionService permissionService) {
    this.dbClient = dbClient;
    this.system2 = system2;
    this.uuidFactory = uuidFactory;
    this.organizationValidation = organizationValidation;
    this.userIndexer = userIndexer;
    this.builtInQProfileRepository = builtInQProfileRepository;
    this.defaultGroupCreator = defaultGroupCreator;
    this.permissionService = permissionService;
  }

  @Override
  public OrganizationDto create(DbSession dbSession, UserDto userCreator, NewOrganization newOrganization, Consumer<OrganizationDto> beforeCommit) throws KeyConflictException {
    validate(newOrganization);
    String key = newOrganization.getKey();
    if (organizationKeyIsUsed(dbSession, key)) {
      throw new KeyConflictException(format("Organization key '%s' is already used", key));
    }

    QualityGateDto builtInQualityGate = dbClient.qualityGateDao().selectBuiltIn(dbSession);
    OrganizationDto organization = insertOrganization(dbSession, newOrganization, builtInQualityGate);
    beforeCommit.accept(organization);
    insertOrganizationMember(dbSession, organization, userCreator.getId());
    dbClient.qualityGateDao().associate(dbSession, uuidFactory.create(), organization, builtInQualityGate);
    GroupDto ownerGroup = insertOwnersGroup(dbSession, organization);
    GroupDto defaultGroup = defaultGroupCreator.create(dbSession, organization.getUuid());
    insertDefaultTemplateOnGroups(dbSession, organization, ownerGroup, defaultGroup);
    addCurrentUserToGroup(dbSession, ownerGroup, userCreator.getId());
    addCurrentUserToGroup(dbSession, defaultGroup, userCreator.getId());
    try (DbSession batchDbSession = dbClient.openSession(true)) {
      insertQualityProfiles(dbSession, batchDbSession, organization);
      batchDbSession.commit();

      // Elasticsearch is updated when DB session is committed
      userIndexer.commitAndIndex(dbSession, userCreator);

      return organization;
    }
  }

  @Override
  public void updateOrganizationKey(DbSession dbSession, OrganizationDto organization, String newKey) {
    String sanitizedKey = organizationValidation.generateKeyFrom(newKey);
    if (organization.getKey().equals(sanitizedKey)) {
      return;
    }
    checkKey(dbSession, sanitizedKey);
    dbClient.organizationDao().update(dbSession, organization.setKey(sanitizedKey));
  }

  private void checkKey(DbSession dbSession, String key) {
    checkState(!organizationKeyIsUsed(dbSession, key),
      "Can't create organization with key '%s' because an organization with this key already exists", key);
  }

  private void validate(NewOrganization newOrganization) {
    requireNonNull(newOrganization, "newOrganization can't be null");
    organizationValidation.checkName(newOrganization.getName());
    organizationValidation.checkKey(newOrganization.getKey());
    organizationValidation.checkDescription(newOrganization.getDescription());
    organizationValidation.checkUrl(newOrganization.getUrl());
    organizationValidation.checkAvatar(newOrganization.getAvatar());
  }

  private OrganizationDto insertOrganization(DbSession dbSession, NewOrganization newOrganization, QualityGateDto builtInQualityGate, Consumer<OrganizationDto>... extendCreation) {
    OrganizationDto res = new OrganizationDto()
      .setUuid(uuidFactory.create())
      .setName(newOrganization.getName())
      .setKey(newOrganization.getKey())
      .setDescription(newOrganization.getDescription())
      .setUrl(newOrganization.getUrl())
      .setDefaultQualityGateUuid(builtInQualityGate.getUuid())
      .setAvatarUrl(newOrganization.getAvatar())
      .setSubscription(FREE);
    Arrays.stream(extendCreation).forEach(c -> c.accept(res));
    dbClient.organizationDao().insert(dbSession, res, false);
    return res;
  }

  private boolean organizationKeyIsUsed(DbSession dbSession, String key) {
    return dbClient.organizationDao().selectByKey(dbSession, key).isPresent();
  }

  private void insertDefaultTemplateOnGroups(DbSession dbSession, OrganizationDto organizationDto, GroupDto ownerGroup, GroupDto defaultGroup) {
    Date now = new Date(system2.now());
    PermissionTemplateDto permissionTemplateDto = dbClient.permissionTemplateDao().insert(
      dbSession,
      new PermissionTemplateDto()
        .setOrganizationUuid(organizationDto.getUuid())
        .setUuid(uuidFactory.create())
        .setName(PERM_TEMPLATE_NAME)
        .setDescription(format(PERM_TEMPLATE_DESCRIPTION_PATTERN, organizationDto.getName()))
        .setCreatedAt(now)
        .setUpdatedAt(now));

    insertGroupPermission(dbSession, permissionTemplateDto, ADMIN, ownerGroup);
    insertGroupPermission(dbSession, permissionTemplateDto, SCAN.getKey(), ownerGroup);
    insertGroupPermission(dbSession, permissionTemplateDto, USER, defaultGroup);
    insertGroupPermission(dbSession, permissionTemplateDto, CODEVIEWER, defaultGroup);
    insertGroupPermission(dbSession, permissionTemplateDto, ISSUE_ADMIN, defaultGroup);
    insertGroupPermission(dbSession, permissionTemplateDto, SECURITYHOTSPOT_ADMIN, defaultGroup);

    dbClient.organizationDao().setDefaultTemplates(
      dbSession,
      organizationDto.getUuid(),
      new DefaultTemplates().setProjectUuid(permissionTemplateDto.getUuid()));
  }

  private void insertGroupPermission(DbSession dbSession, PermissionTemplateDto template, String permission, @Nullable GroupDto group) {
    dbClient.permissionTemplateDao().insertGroupPermission(dbSession, template.getId(), group == null ? null : group.getId(), permission);
  }

  private void insertQualityProfiles(DbSession dbSession, DbSession batchDbSession, OrganizationDto organization) {
    Map<QProfileName, BuiltInQProfile> builtInsPerName = builtInQProfileRepository.get().stream()
      .collect(uniqueIndex(BuiltInQProfile::getQProfileName));

    List<DefaultQProfileDto> defaults = new ArrayList<>();
    dbClient.qualityProfileDao().selectBuiltInRuleProfiles(dbSession).forEach(rulesProfile -> {
      OrgQProfileDto dto = new OrgQProfileDto()
        .setOrganizationUuid(organization.getUuid())
        .setRulesProfileUuid(rulesProfile.getKee())
        .setUuid(uuidFactory.create());

      QProfileName name = new QProfileName(rulesProfile.getLanguage(), rulesProfile.getName());
      BuiltInQProfile builtIn = builtInsPerName.get(name);
      if (builtIn == null || builtIn.isDefault()) {
        // If builtIn == null, the plugin has been removed
        // rows of table default_qprofiles must be inserted after org_qprofiles
        // in order to benefit from batch SQL inserts
        defaults.add(new DefaultQProfileDto()
          .setQProfileUuid(dto.getUuid())
          .setOrganizationUuid(organization.getUuid())
          .setLanguage(rulesProfile.getLanguage()));
      }

      dbClient.qualityProfileDao().insert(batchDbSession, dto);
    });

    defaults.forEach(defaultQProfileDto -> dbClient.defaultQProfileDao().insertOrUpdate(dbSession, defaultQProfileDto));
  }

  /**
   * Owners group has an hard coded name, a description based on the organization's name and has all global permissions.
   */
  private GroupDto insertOwnersGroup(DbSession dbSession, OrganizationDto organization) {
    GroupDto group = dbClient.groupDao().insert(dbSession, new GroupDto()
      .setOrganizationUuid(organization.getUuid())
      .setName(OWNERS_GROUP_NAME)
      .setDescription(OWNERS_GROUP_DESCRIPTION));
    permissionService.getAllOrganizationPermissions().forEach(p -> addPermissionToGroup(dbSession, group, p));
    return group;
  }

  private void addPermissionToGroup(DbSession dbSession, GroupDto group, OrganizationPermission permission) {
    dbClient.groupPermissionDao().insert(
      dbSession,
      new GroupPermissionDto()
        .setOrganizationUuid(group.getOrganizationUuid())
        .setGroupId(group.getId())
        .setRole(permission.getKey()));
  }

  private void addCurrentUserToGroup(DbSession dbSession, GroupDto group, int createUserId) {
    dbClient.userGroupDao().insert(
      dbSession,
      new UserGroupDto().setGroupId(group.getId()).setUserId(createUserId));
  }

  private void insertOrganizationMember(DbSession dbSession, OrganizationDto organizationDto, int userId) {
    dbClient.organizationMemberDao().insert(dbSession, new OrganizationMemberDto()
      .setOrganizationUuid(organizationDto.getUuid())
      .setUserId(userId));
  }
}
