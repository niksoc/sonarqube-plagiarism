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
package org.sonar.server.es;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.Collection;
import org.sonar.db.DbSession;

public class TestProjectIndexers implements ProjectIndexers {

  private final ListMultimap<String, ProjectIndexer.Cause> calls = ArrayListMultimap.create();

  @Override
  public void commitAndIndexByProjectUuids(DbSession dbSession, Collection<String> projectUuids, ProjectIndexer.Cause cause) {
    dbSession.commit();
    projectUuids.forEach(projectUuid -> calls.put(projectUuid, cause));

  }

  public boolean hasBeenCalled(String projectUuid, ProjectIndexer.Cause expectedCause) {
    return calls.get(projectUuid).contains(expectedCause);
  }

  public boolean hasBeenCalled(String projectUuid) {
    return calls.containsKey(projectUuid);
  }
}
