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
package org.sonar.server.rule.index;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.sonar.db.rule.RuleExtensionForIndexingDto;
import org.sonar.db.rule.RuleForIndexingDto;
import org.sonar.server.es.BaseDoc;

import static org.sonar.server.rule.index.RuleIndexDefinition.TYPE_RULE_EXTENSION;

public class RuleExtensionDoc extends BaseDoc {

  public RuleExtensionDoc(Map<String, Object> fields) {
    super(TYPE_RULE_EXTENSION, fields);
  }

  public RuleExtensionDoc() {
    super(TYPE_RULE_EXTENSION, new HashMap<>(4));
  }

  @Override
  public String getId() {
    return idOf(getRuleId(), getScope());
  }

  public int getRuleId() {
    return Integer.valueOf(ruleIdAsString());
  }

  private String ruleIdAsString() {
    return getField(RuleIndexDefinition.FIELD_RULE_ID);
  }

  public RuleExtensionDoc setRuleId(int ruleId) {
    String parent = String.valueOf(ruleId);
    setField(RuleIndexDefinition.FIELD_RULE_ID, parent);
    setParent(parent);
    return this;
  }

  public RuleExtensionScope getScope() {
    return RuleExtensionScope.parse(getField(RuleIndexDefinition.FIELD_RULE_EXTENSION_SCOPE));
  }

  public RuleExtensionDoc setScope(RuleExtensionScope scope) {
    setField(RuleIndexDefinition.FIELD_RULE_EXTENSION_SCOPE, scope.getScope());
    return this;
  }

  public Set<String> getTags() {
    return getField(RuleIndexDefinition.FIELD_RULE_EXTENSION_TAGS);
  }

  public RuleExtensionDoc setTags(Set<String> tags) {
    setField(RuleIndexDefinition.FIELD_RULE_EXTENSION_TAGS, tags);
    return this;
  }

  public static RuleExtensionDoc of(RuleForIndexingDto rule) {
    return new RuleExtensionDoc()
      .setRuleId(rule.getId())
      .setScope(RuleExtensionScope.system())
      .setTags(rule.getSystemTags());
  }

  public static RuleExtensionDoc of(RuleExtensionForIndexingDto rule) {
    return new RuleExtensionDoc()
      .setRuleId(rule.getRuleId())
      .setScope(RuleExtensionScope.organization(rule.getOrganizationUuid()))
      .setTags(rule.getTagsAsSet());
  }

  public static String idOf(int ruleId, RuleExtensionScope scope) {
    return ruleId + "|" + scope.getScope();
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
