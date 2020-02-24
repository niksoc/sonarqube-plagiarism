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
package org.sonar.server.component;

import java.util.List;
import org.sonar.api.resources.ResourceType;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.resources.Scopes;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.es.ProjectIndexers;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.singletonList;
import static org.sonar.server.es.ProjectIndexer.Cause.PROJECT_DELETION;

@ServerSide
public class ComponentCleanerService {

  private final DbClient dbClient;
  private final ResourceTypes resourceTypes;
  private final ProjectIndexers projectIndexers;

  public ComponentCleanerService(DbClient dbClient, ResourceTypes resourceTypes, ProjectIndexers projectIndexers) {
    this.dbClient = dbClient;
    this.resourceTypes = resourceTypes;
    this.projectIndexers = projectIndexers;
  }

  public void delete(DbSession dbSession, List<ProjectDto> projects) {
    for (ProjectDto project : projects) {
      delete(dbSession, project);
    }
  }

  public void deleteComponents(DbSession dbSession, List<ComponentDto> components) {
    for (ComponentDto component : components) {
      delete(dbSession, component);
    }
  }

  public void deleteBranch(DbSession dbSession, BranchDto branch) {
    // TODO: detect if other branches depend on it?
    dbClient.purgeDao().deleteBranch(dbSession, branch.getUuid());
    projectIndexers.commitAndIndexBranches(dbSession, singletonList(branch), PROJECT_DELETION);
  }

  public void delete(DbSession dbSession, ProjectDto project) {
    dbClient.purgeDao().deleteProject(dbSession, project.getUuid());
    dbClient.userDao().cleanHomepage(dbSession, project);
    projectIndexers.commitAndIndexProjects(dbSession, singletonList(project), PROJECT_DELETION);
  }

  public void delete(DbSession dbSession, ComponentDto project) {
    checkArgument(!hasNotProjectScope(project) && !isNotDeletable(project) && project.getMainBranchProjectUuid() == null, "Only projects can be deleted");
    dbClient.purgeDao().deleteProject(dbSession, project.uuid());
    dbClient.userDao().cleanHomepage(dbSession, project);
    projectIndexers.commitAndIndexComponents(dbSession, singletonList(project), PROJECT_DELETION);
  }

  private static boolean hasNotProjectScope(ComponentDto project) {
    return !Scopes.PROJECT.equals(project.scope());
  }

  private boolean isNotDeletable(ComponentDto project) {
    ResourceType resourceType = resourceTypes.get(project.qualifier());
    // this essentially means PROJECTS, VIEWS and APPS (not SUBVIEWS)
    return resourceType == null || !resourceType.getBooleanProperty("deletable");
  }
}
