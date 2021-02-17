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
package org.sonar.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SQDatabaseForH2Test {
  SQDatabase db = SQDatabase.newH2Database("sonar2", true);

  @Before
  public void startDb() {
    db.start();
  }

  @After
  public void stopDb() {
    db.stop();
  }

  @Test
  public void shouldExecuteDdlAtStartup() throws SQLException {
    Connection connection = db.getDataSource().getConnection();
    int tableCount = countTables(connection);
    connection.close();

    assertThat(tableCount).isGreaterThan(30);
  }

  private static int countTables(Connection connection) throws SQLException {
    int count = 0;
    ResultSet resultSet = connection.getMetaData().getTables("", null, null, new String[] {"TABLE"});
    while (resultSet.next()) {
      count++;
    }
    resultSet.close();
    return count;
  }
}
