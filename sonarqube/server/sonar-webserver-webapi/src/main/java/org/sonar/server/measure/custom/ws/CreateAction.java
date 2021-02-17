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
package org.sonar.server.measure.custom.ws;

import org.sonar.api.resources.Scopes;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.custom.CustomMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.user.UserSession;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.sonar.server.component.ComponentFinder.ParamNames.PROJECT_ID_AND_KEY;
import static org.sonar.server.measure.custom.ws.CustomMeasureValidator.checkPermissions;
import static org.sonar.server.measure.custom.ws.CustomMeasureValueDescription.measureValueDescription;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonar.server.exceptions.BadRequestException.checkRequest;

public class CreateAction implements CustomMeasuresWsAction {
  public static final String ACTION = "create";
  public static final String PARAM_PROJECT_ID = "projectId";
  public static final String PARAM_PROJECT_KEY = "projectKey";
  public static final String PARAM_METRIC_ID = "metricId";
  public static final String PARAM_METRIC_KEY = "metricKey";
  public static final String PARAM_VALUE = "value";
  public static final String PARAM_DESCRIPTION = "description";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final System2 system;
  private final CustomMeasureValidator validator;
  private final CustomMeasureJsonWriter customMeasureJsonWriter;
  private final ComponentFinder componentFinder;

  public CreateAction(DbClient dbClient, UserSession userSession, System2 system, CustomMeasureValidator validator, CustomMeasureJsonWriter customMeasureJsonWriter,
    ComponentFinder componentFinder) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.system = system;
    this.validator = validator;
    this.customMeasureJsonWriter = customMeasureJsonWriter;
    this.componentFinder = componentFinder;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION)
      .setDescription("Create a custom measure.<br /> " +
        "The project id or the project key must be provided (only project and module custom measures can be created). The metric id or the metric key must be provided.<br/>" +
        "Requires 'Administer' permission on the project.")
      .setSince("5.2")
      .setDeprecatedSince("7.4")
      .setPost(true)
      .setHandler(this);

    action.createParam(PARAM_PROJECT_ID)
      .setDescription("Project id")
      .setExampleValue("ce4c03d6-430f-40a9-b777-ad877c00aa4d");

    action.createParam(PARAM_PROJECT_KEY)
      .setDescription("Project key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001);

    action.createParam(PARAM_METRIC_ID)
      .setDescription("Metric id")
      .setExampleValue("16");

    action.createParam(PARAM_METRIC_KEY)
      .setDescription("Metric key")
      .setExampleValue("ncloc");

    action.createParam(PARAM_VALUE)
      .setRequired(true)
      .setDescription(measureValueDescription())
      .setExampleValue("47");

    action.createParam(PARAM_DESCRIPTION)
      .setDescription("Description")
      .setExampleValue("Team size growing.");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String valueAsString = request.mandatoryParam(PARAM_VALUE);
    String description = request.param(PARAM_DESCRIPTION);
    long now = system.now();

    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto component = componentFinder.getByUuidOrKey(dbSession, request.param(PARAM_PROJECT_ID), request.param(PARAM_PROJECT_KEY), PROJECT_ID_AND_KEY);
      MetricDto metric = searchMetric(dbSession, request);
      checkPermissions(userSession, component);
      checkIsProjectOrModule(component);
      checkMeasureDoesNotExistAlready(dbSession, component, metric);
      String userUuid = requireNonNull(userSession.getUuid(), "User uuid should not be null");
      UserDto user = dbClient.userDao().selectByUuid(dbSession, userUuid);
      checkState(user != null, "User with uuid '%s' does not exist", userUuid);
      CustomMeasureDto measure = new CustomMeasureDto()
        .setComponentUuid(component.uuid())
        .setMetricId(metric.getId())
        .setDescription(description)
        .setUserUuid(user.getUuid())
        .setCreatedAt(now)
        .setUpdatedAt(now);
      validator.setMeasureValue(measure, valueAsString, metric);
      dbClient.customMeasureDao().insert(dbSession, measure);
      dbSession.commit();

      JsonWriter json = response.newJsonWriter();
      customMeasureJsonWriter.write(json, measure, metric, component, user, true, CustomMeasureJsonWriter.OPTIONAL_FIELDS);
      json.close();
    }
  }

  private static void checkIsProjectOrModule(ComponentDto component) {
    checkRequest(Scopes.PROJECT.equals(component.scope()), "Component '%s' must be a project or a module.", component.getDbKey());
  }

  private void checkMeasureDoesNotExistAlready(DbSession dbSession, ComponentDto component, MetricDto metric) {
    int nbMeasuresOnSameMetricAndMeasure = dbClient.customMeasureDao().countByComponentIdAndMetricId(dbSession, component.uuid(), metric.getId());
    checkRequest(nbMeasuresOnSameMetricAndMeasure == 0,
      "A measure already exists for project '%s' and metric '%s'",
      component.getDbKey(), metric.getKey());
  }

  private MetricDto searchMetric(DbSession dbSession, Request request) {
    Integer metricId = request.paramAsInt(PARAM_METRIC_ID);
    String metricKey = request.param(PARAM_METRIC_KEY);
    checkArgument(metricId != null ^ metricKey != null, "Either the metric id or the metric key must be provided");

    if (metricId == null) {
      MetricDto metric = dbClient.metricDao().selectByKey(dbSession, metricKey);
      checkArgument(metric != null, "Metric with key '%s' does not exist", metricKey);
      return metric;
    }
    MetricDto metric = dbClient.metricDao().selectById(dbSession, metricId);
    checkArgument(metric != null, "Metric with id '%s' does not exist", metricId);
    return metric;
  }
}
