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
package org.sonar.server.platform.db.migration.version.v80;

import java.sql.SQLException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.db.CoreDbTester;

import static java.sql.Types.BIGINT;
import static java.sql.Types.VARCHAR;

public class CreateNewCodePeriodTableTest {
  private static final String TABLE_NAME = "new_code_periods";

  @Rule
  public CoreDbTester dbTester = CoreDbTester.createEmpty();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private CreateNewCodePeriodTable underTest = new CreateNewCodePeriodTable(dbTester.database());

  @Test
  public void table_has_been_created() throws SQLException {
    underTest.execute();

    dbTester.assertTableExists(TABLE_NAME);
    dbTester.assertPrimaryKey(TABLE_NAME, "pk_new_code_periods", "uuid");
    dbTester.assertUniqueIndex(TABLE_NAME, "uniq_new_code_periods", "project_uuid", "branch_uuid");

    dbTester.assertColumnDefinition(TABLE_NAME, "uuid", VARCHAR, 40, false);
    dbTester.assertColumnDefinition(TABLE_NAME, "project_uuid", VARCHAR, 40, true);
    dbTester.assertColumnDefinition(TABLE_NAME, "branch_uuid", VARCHAR, 40, true);
    dbTester.assertColumnDefinition(TABLE_NAME, "type", VARCHAR, 30, false);
    dbTester.assertColumnDefinition(TABLE_NAME, "value", VARCHAR, 40, true);
    dbTester.assertColumnDefinition(TABLE_NAME, "updated_at", BIGINT, 20, false);
    dbTester.assertColumnDefinition(TABLE_NAME, "created_at", BIGINT, 20, false);

    //script should not fail if executed twice
    underTest.execute();
  }

}
