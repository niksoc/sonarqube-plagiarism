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
package org.sonar.db.user;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * @since 4.1
 */
public class GroupMembershipDto {

  private Long id;
  private String name;
  private String description;
  private Integer userId;

  public Long getId() {
    return id;
  }

  public GroupMembershipDto setId(Long id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public GroupMembershipDto setName(String name) {
    this.name = name;
    return this;
  }

  @CheckForNull
  public String getDescription() {
    return description;
  }

  public GroupMembershipDto setDescription(@Nullable String description) {
    this.description = description;
    return this;
  }

  @CheckForNull
  public Integer getUserId() {
    return userId;
  }

  public GroupMembershipDto setUserId(@Nullable Integer userId) {
    this.userId = userId;
    return this;
  }

}
