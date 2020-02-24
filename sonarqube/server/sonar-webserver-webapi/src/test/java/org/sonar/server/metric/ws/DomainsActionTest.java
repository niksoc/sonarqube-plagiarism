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
package org.sonar.server.metric.ws;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.TestDBSessions;
import org.sonar.db.metric.MetricDao;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonar.test.JsonAssert;

import static org.sonar.db.metric.MetricTesting.newMetricDto;


public class DomainsActionTest {

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private DbClient dbClient = new DbClient(db.database(), db.myBatis(), new TestDBSessions(db.myBatis()), new MetricDao());
  private DbSession dbSession = dbClient.openSession(false);
  private DomainsAction underTest = new DomainsAction(dbClient);
  private WsActionTester tester = new WsActionTester(underTest);

  @After
  public void tearDown() {
    dbSession.close();
  }

  @Test
  public void json_example_validated() {
    insertNewMetricDto(newEnabledMetric("API Compatibility"));
    insertNewMetricDto(newEnabledMetric("Issues"));
    insertNewMetricDto(newEnabledMetric("Rules"));
    insertNewMetricDto(newEnabledMetric("Tests"));
    insertNewMetricDto(newEnabledMetric("Documentation"));
    insertNewMetricDto(newEnabledMetric(null));
    insertNewMetricDto(newEnabledMetric(""));
    insertNewMetricDto(newMetricDto().setDomain("Domain of Deactivated Metric").setEnabled(false));

    TestRequest result = tester.newRequest();

    JsonAssert.assertJson(result.execute().getInput()).isSimilarTo(getClass().getResource("example-domains.json"));
  }

  private void insertNewMetricDto(MetricDto metric) {
    dbClient.metricDao().insert(dbSession, metric);
    dbSession.commit();
  }

  private MetricDto newEnabledMetric(String domain) {
    return newMetricDto().setDomain(domain).setEnabled(true);
  }
}
