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

import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;

import static org.sonar.server.qualitygate.ws.QualityGatesWsParameters.PARAM_PROJECT_ID;
import static org.sonar.server.qualitygate.ws.QualityGatesWsParameters.PARAM_PROJECT_KEY;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;

public class DeselectAction implements QualityGatesWsAction {

  private final DbClient dbClient;
  private final QualityGatesWsSupport wsSupport;

  public DeselectAction(DbClient dbClient, QualityGatesWsSupport wsSupport) {
    this.wsSupport = wsSupport;
    this.dbClient = dbClient;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("deselect")
      .setDescription("Remove the association of a project from a quality gate.<br>" +
        "Requires one of the following permissions:" +
        "<ul>" +
        "<li>'Administer Quality Gates'</li>" +
        "<li>'Administer' rights on the project</li>" +
        "</ul>")
      .setPost(true)
      .setSince("4.3")
      .setHandler(this)
      .setChangelog(new Change("6.6", "The parameter 'gateId' was removed"));

    action.createParam(PARAM_PROJECT_ID)
      .setDescription("Project id")
      .setDeprecatedSince("6.1")
      .setExampleValue(Uuids.UUID_EXAMPLE_01);

    action.createParam(PARAM_PROJECT_KEY)
      .setDescription("Project key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001)
      .setSince("6.1");

    wsSupport.createOrganizationParam(action);
  }

  @Override
  public void handle(Request request, Response response) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = wsSupport.getOrganization(dbSession, request);
      ProjectDto project = wsSupport.getProject(dbSession, organization, request.param(PARAM_PROJECT_KEY), request.param(PARAM_PROJECT_ID));
      dissociateProject(dbSession, organization, project);
      response.noContent();
    }
  }

  private void dissociateProject(DbSession dbSession, OrganizationDto organization, ProjectDto project) {
    wsSupport.checkCanAdminProject(organization, project);
    dbClient.projectQgateAssociationDao().deleteByProjectUuid(dbSession, project.getUuid());
    dbSession.commit();
  }
}
