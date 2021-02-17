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
package org.sonar.db.qualityprofile;

import java.util.LinkedList;
import java.util.List;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleType;
import org.sonar.db.rule.SeverityUtil;

public class ExportRuleDto {
  private Integer activeRuleId = null;
  private String repository = null;
  private String rule = null;
  private String name = null;
  private String description = null;
  private String extendedDescription = null;
  private String template = null;
  private Integer severity = null;
  private Integer type = null;
  private String tags = null;

  private List<ExportRuleParamDto> params;

  public boolean isCustomRule() {
    return template != null;
  }

  public Integer getActiveRuleId() {
    return activeRuleId;
  }

  public RuleKey getRuleKey() {
    return RuleKey.of(repository, rule);
  }

  public RuleKey getTemplateRuleKey() {
    return RuleKey.of(repository, template);
  }

  public String getSeverityString() {
    return SeverityUtil.getSeverityFromOrdinal(severity);
  }

  public String getExtendedDescription() {
    return extendedDescription;
  }

  public RuleType getRuleType() {
    return RuleType.valueOf(type);
  }

  public String getTags() {
    return tags;
  }

  public String getDescription() {
    return description;
  }

  public String getName() {
    return name;
  }

  public List<ExportRuleParamDto> getParams() {
    if (params == null) {
      params = new LinkedList<>();
    }
    return params;
  }

  void setParams(List<ExportRuleParamDto> params) {
    this.params = params;
  }
}
