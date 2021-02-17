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
package org.sonar.server.tester;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sonar.api.web.UserRole;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.user.AbstractUserSession;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class AbstractMockUserSession<T extends AbstractMockUserSession> extends AbstractUserSession {
  private static final Set<String> PUBLIC_PERMISSIONS = ImmutableSet.of(UserRole.USER, UserRole.CODEVIEWER); // FIXME to check with Simon

  private final Class<T> clazz;
  private HashMultimap<String, String> projectUuidByPermission = HashMultimap.create();
  private final HashMultimap<String, OrganizationPermission> permissionsByOrganizationUuid = HashMultimap.create();
  private Map<String, String> projectUuidByComponentUuid = new HashMap<>();
  private Set<String> projectPermissions = new HashSet<>();
  private Set<String> organizationMembership = new HashSet<>();
  private boolean systemAdministrator = false;

  protected AbstractMockUserSession(Class<T> clazz) {
    this.clazz = clazz;
  }

  public T addPermission(OrganizationPermission permission, String organizationUuid) {
    permissionsByOrganizationUuid.put(organizationUuid, permission);
    return clazz.cast(this);
  }

  @Override
  protected boolean hasPermissionImpl(OrganizationPermission permission, String organizationUuid) {
    return permissionsByOrganizationUuid.get(organizationUuid).contains(permission);
  }

  /**
   * Use this method to register public root component and non root components the UserSession must be aware of.
   * (ie. this method can be used to emulate the content of the DB)
   */
  public T registerComponents(ComponentDto... components) {
    Arrays.stream(components)
      .forEach(component -> {
        if (component.projectUuid().equals(component.uuid()) && !component.isPrivate()) {
          this.projectUuidByPermission.put(UserRole.USER, component.uuid());
          this.projectUuidByPermission.put(UserRole.CODEVIEWER, component.uuid());
          this.projectPermissions.add(UserRole.USER);
          this.projectPermissions.add(UserRole.CODEVIEWER);
        }
        this.projectUuidByComponentUuid.put(component.uuid(), component.projectUuid());
      });
    return clazz.cast(this);
  }

  public T registerProjects(ProjectDto... projects) {
    Arrays.stream(projects)
      .forEach(project -> {
        if (!project.isPrivate()) {
          this.projectUuidByPermission.put(UserRole.USER, project.getUuid());
          this.projectUuidByPermission.put(UserRole.CODEVIEWER, project.getUuid());
          this.projectPermissions.add(UserRole.USER);
          this.projectPermissions.add(UserRole.CODEVIEWER);
        }
        this.projectUuidByComponentUuid.put(project.getUuid(), project.getUuid());
      });
    return clazz.cast(this);
  }

  public T addProjectPermission(String permission, ComponentDto... components) {
    Arrays.stream(components).forEach(component -> {
      checkArgument(
        component.isPrivate() || !PUBLIC_PERMISSIONS.contains(permission),
        "public component %s can't be granted public permission %s", component.uuid(), permission);
    });
    registerComponents(components);
    this.projectPermissions.add(permission);
    Arrays.stream(components)
      .forEach(component -> this.projectUuidByPermission.put(permission, component.projectUuid()));
    return clazz.cast(this);
  }

  public T addProjectPermission(String permission, ProjectDto... projects) {
    Arrays.stream(projects).forEach(component -> {
      checkArgument(
        component.isPrivate() || !PUBLIC_PERMISSIONS.contains(permission),
        "public component %s can't be granted public permission %s", component.getUuid(), permission);
    });
    registerProjects(projects);
    this.projectPermissions.add(permission);
    Arrays.stream(projects)
      .forEach(component -> this.projectUuidByPermission.put(permission, component.getUuid()));
    return clazz.cast(this);
  }

  @Override
  protected Optional<String> componentUuidToProjectUuid(String componentUuid) {
    return Optional.ofNullable(projectUuidByComponentUuid.get(componentUuid));
  }

  @Override
  protected boolean hasProjectUuidPermission(String permission, String projectUuid) {
    return projectPermissions.contains(permission) && projectUuidByPermission.get(permission).contains(projectUuid);
  }

  public T setSystemAdministrator(boolean b) {
    this.systemAdministrator = b;
    return clazz.cast(this);
  }

  @Override
  public boolean isSystemAdministrator() {
    return isRoot() || systemAdministrator;
  }

  @Override
  protected boolean hasMembershipImpl(OrganizationDto organizationDto) {
    return organizationMembership.contains(organizationDto.getUuid());
  }

  public void addOrganizationMembership(OrganizationDto organization) {
    this.organizationMembership.add(organization.getUuid());
  }

}
