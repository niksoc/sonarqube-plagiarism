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
package org.sonar.server.permission.index;

import com.google.common.collect.ImmutableList;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;

import static org.apache.commons.lang.StringUtils.repeat;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;

/**
 * No streaming because of union of joins -> no need to use ResultSetIterator
 */
public class PermissionIndexerDao {

  private enum RowKind {
    USER, GROUP, ANYONE, NONE
  }

  private static final String SQL_TEMPLATE = "SELECT " +
    "  project_authorization.kind as kind, " +
    "  project_authorization.project as project, " +
    "  project_authorization.user_id as user_id, " +
    "  project_authorization.group_id as group_id, " +
    "  project_authorization.qualifier as qualifier " +
    "FROM ( " +

    // users

    "      SELECT '" + RowKind.USER + "' as kind," +
    "      c.uuid AS project, " +
    "      c.qualifier AS qualifier, " +
    "      user_roles.user_id  AS user_id, " +
    "      NULL  AS group_id " +
    "      FROM components c " +
    "      INNER JOIN user_roles ON user_roles.resource_id = c.id AND user_roles.role = 'user' " +
    "      WHERE " +
    "        (c.qualifier = 'TRK' " +
    "         or  c.qualifier = 'VW' " +
    "         or  c.qualifier = 'APP') " +
    "        AND c.copy_component_uuid is NULL " +
    "        {projectsCondition} " +
    "      UNION " +

    // groups

    "      SELECT '" + RowKind.GROUP + "' as kind," +
    "      c.uuid AS project, " +
    "      c.qualifier AS qualifier, " +
    "      NULL  AS user_id, " +
    "      groups.id  AS group_id " +
    "      FROM components c " +
    "      INNER JOIN group_roles ON group_roles.resource_id = c.id AND group_roles.role = 'user' " +
    "      INNER JOIN groups ON groups.id = group_roles.group_id " +
    "      WHERE " +
    "        (c.qualifier = 'TRK' " +
    "         or  c.qualifier = 'VW' " +
    "         or  c.qualifier = 'APP') " +
    "        AND c.copy_component_uuid is NULL " +
    "        {projectsCondition} " +
    "        AND group_id IS NOT NULL " +
    "      UNION " +

    // public projects are accessible to any one

    "      SELECT '" + RowKind.ANYONE + "' as kind," +
    "      c.uuid AS project, " +
    "      c.qualifier AS qualifier, " +
    "      NULL         AS user_id, " +
    "      NULL     AS group_id " +
    "      FROM components c " +
    "      WHERE " +
    "        (c.qualifier = 'TRK' " +
    "         or  c.qualifier = 'VW' " +
    "         or  c.qualifier = 'APP') " +
    "        AND c.copy_component_uuid is NULL " +
    "        AND c.private = ? " +
    "        {projectsCondition} " +
    "      UNION " +

    // private project is returned when no authorization
    "      SELECT '" + RowKind.NONE + "' as kind," +
    "      c.uuid AS project, " +
    "      c.qualifier AS qualifier, " +
    "      NULL AS user_id, " +
    "      NULL  AS group_id " +
    "      FROM components c " +
    "      WHERE " +
    "        (c.qualifier = 'TRK' " +
    "         or  c.qualifier = 'VW' " +
    "         or  c.qualifier = 'APP') " +
    "        AND c.copy_component_uuid is NULL " +
    "        AND c.private = ? " +
    "        {projectsCondition} " +

    "    ) project_authorization";

  List<IndexPermissions> selectAll(DbClient dbClient, DbSession session) {
    return doSelectByProjects(dbClient, session, Collections.emptyList());
  }

  List<IndexPermissions> selectByUuids(DbClient dbClient, DbSession session, Collection<String> projectOrViewUuids) {
    return executeLargeInputs(projectOrViewUuids, subProjectOrViewUuids -> doSelectByProjects(dbClient, session, subProjectOrViewUuids));
  }

  private static List<IndexPermissions> doSelectByProjects(DbClient dbClient, DbSession session, List<String> projectUuids) {
    try {
      Map<String, IndexPermissions> dtosByProjectUuid = new HashMap<>();
      try (PreparedStatement stmt = createStatement(dbClient, session, projectUuids);
        ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          processRow(rs, dtosByProjectUuid);
        }
        return ImmutableList.copyOf(dtosByProjectUuid.values());
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Fail to select authorizations", e);
    }
  }

  private static PreparedStatement createStatement(DbClient dbClient, DbSession session, List<String> projectUuids) throws SQLException {
    String sql;
    if (projectUuids.isEmpty()) {
      sql = StringUtils.replace(SQL_TEMPLATE, "{projectsCondition}", "");
    } else {
      sql = StringUtils.replace(SQL_TEMPLATE, "{projectsCondition}", " AND c.uuid in (" + repeat("?", ", ", projectUuids.size()) + ")");
    }
    PreparedStatement stmt = dbClient.getMyBatis().newScrollingSelectStatement(session, sql);
    int index = 1;
    // query for RowKind.USER
    index = populateProjectUuidPlaceholders(stmt, projectUuids, index);
    // query for RowKind.GROUP
    index = populateProjectUuidPlaceholders(stmt, projectUuids, index);
    // query for RowKind.ANYONE
    index = setPrivateProjectPlaceHolder(stmt, index, false);
    index = populateProjectUuidPlaceholders(stmt, projectUuids, index);
    // query for RowKind.NONE
    index = setPrivateProjectPlaceHolder(stmt, index, true);
    populateProjectUuidPlaceholders(stmt, projectUuids, index);
    return stmt;
  }

  private static int populateProjectUuidPlaceholders(PreparedStatement stmt, List<String> projectUuids, int index) throws SQLException {
    int newIndex = index;
    for (String projectUuid : projectUuids) {
      stmt.setString(newIndex, projectUuid);
      newIndex++;
    }
    return newIndex;
  }

  private static int setPrivateProjectPlaceHolder(PreparedStatement stmt, int index, boolean isPrivate) throws SQLException {
    int newIndex = index;
    stmt.setBoolean(newIndex, isPrivate);
    newIndex++;
    return newIndex;
  }

  private static void processRow(ResultSet rs, Map<String, IndexPermissions> dtosByProjectUuid) throws SQLException {
    RowKind rowKind = RowKind.valueOf(rs.getString(1));
    String projectUuid = rs.getString(2);

    IndexPermissions dto = dtosByProjectUuid.get(projectUuid);
    if (dto == null) {
      String qualifier = rs.getString(5);
      dto = new IndexPermissions(projectUuid, qualifier);
      dtosByProjectUuid.put(projectUuid, dto);
    }
    switch (rowKind) {
      case NONE:
        break;
      case USER:
        dto.addUserId(rs.getInt(3));
        break;
      case GROUP:
        dto.addGroupId(rs.getInt(4));
        break;
      case ANYONE:
        dto.allowAnyone();
        break;
    }
  }
}
