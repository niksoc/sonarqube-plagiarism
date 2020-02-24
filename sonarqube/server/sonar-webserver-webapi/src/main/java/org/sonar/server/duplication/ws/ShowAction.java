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
package org.sonar.server.duplication.ws;

import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.user.UserSession;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sonar.server.component.ComponentFinder.ParamNames.UUID_AND_KEY;
import static org.sonar.server.ws.KeyExamples.KEY_BRANCH_EXAMPLE_001;
import static org.sonar.server.ws.KeyExamples.KEY_PULL_REQUEST_EXAMPLE_001;
import static org.sonar.server.ws.WsUtils.writeProtobuf;

public class ShowAction implements DuplicationsWsAction {

  private static final String PARAM_KEY = "key";
  private static final String PARAM_UUID = "uuid";
  private static final String PARAM_BRANCH = "branch";
  private static final String PARAM_PULL_REQUEST = "pullRequest";
  private final DbClient dbClient;
  private final DuplicationsParser parser;
  private final ShowResponseBuilder responseBuilder;
  private final UserSession userSession;
  private final ComponentFinder componentFinder;

  public ShowAction(DbClient dbClient, DuplicationsParser parser, ShowResponseBuilder responseBuilder, UserSession userSession, ComponentFinder componentFinder) {
    this.dbClient = dbClient;
    this.parser = parser;
    this.responseBuilder = responseBuilder;
    this.userSession = userSession;
    this.componentFinder = componentFinder;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("show")
      .setDescription("Get duplications. Require Browse permission on file's project")
      .setSince("4.4")
      .setHandler(this)
      .setResponseExample(getClass().getResource("show-example.json"));

    action.setChangelog(
      new Change("6.5", "The fields 'uuid', 'projectUuid', 'subProjectUuid' are deprecated in the response."));

    action
      .createParam(PARAM_KEY)
      .setDescription("File key")
      .setExampleValue("my_project:/src/foo/Bar.php");

    action
      .createParam(PARAM_UUID)
      .setDeprecatedSince("6.5")
      .setDescription("File ID. If provided, 'key' must not be provided.")
      .setExampleValue("584a89f2-8037-4f7b-b82c-8b45d2d63fb2");

    action
      .createParam(PARAM_BRANCH)
      .setDescription("Branch key")
      .setInternal(true)
      .setExampleValue(KEY_BRANCH_EXAMPLE_001);

    action
      .createParam(PARAM_PULL_REQUEST)
      .setDescription("Pull request id")
      .setInternal(true)
      .setSince("7.1")
      .setExampleValue(KEY_PULL_REQUEST_EXAMPLE_001);
  }

  @Override
  public void handle(Request request, Response response) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto component = loadComponent(dbSession, request);
      userSession.checkComponentPermission(UserRole.CODEVIEWER, component);
      String duplications = findDataFromComponent(dbSession, component);
      String branch = component.getBranch();
      String pullRequest = component.getPullRequest();
      List<DuplicationsParser.Block> blocks = parser.parse(dbSession, component, branch, pullRequest, duplications);
      writeProtobuf(responseBuilder.build(dbSession, blocks, branch, pullRequest), request, response);
    }
  }

  private ComponentDto loadComponent(DbSession dbSession, Request request) {
    String componentUuid = request.param(PARAM_UUID);
    String branch = request.param(PARAM_BRANCH);
    String pullRequest = request.param(PARAM_PULL_REQUEST);
    checkArgument(componentUuid == null || (branch == null && pullRequest == null), "Parameter '%s' cannot be used at the same time as '%s' or '%s'", PARAM_UUID,
      PARAM_BRANCH, PARAM_PULL_REQUEST);
    if (branch == null && pullRequest == null) {
      return componentFinder.getByUuidOrKey(dbSession, componentUuid, request.param(PARAM_KEY), UUID_AND_KEY);
    }
    return componentFinder.getByKeyAndOptionalBranchOrPullRequest(dbSession, request.mandatoryParam(PARAM_KEY), branch, pullRequest);
  }

  @CheckForNull
  private String findDataFromComponent(DbSession dbSession, ComponentDto component) {
    return dbClient.liveMeasureDao().selectMeasure(dbSession, component.uuid(), CoreMetrics.DUPLICATIONS_DATA_KEY)
      .map(LiveMeasureDto::getDataAsString)
      .orElse(null);
  }
}
