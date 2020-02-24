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
import * as React from 'react';
import Dropdown from 'sonar-ui-common/components/controls/Dropdown';
import DropdownIcon from 'sonar-ui-common/components/icons/DropdownIcon';
import FilterIcon from 'sonar-ui-common/components/icons/FilterIcon';
import IssueTypeIcon from 'sonar-ui-common/components/icons/IssueTypeIcon';
import TagsIcon from 'sonar-ui-common/components/icons/TagsIcon';
import { translate } from 'sonar-ui-common/helpers/l10n';
import SeverityHelper from '../../../components/shared/SeverityHelper';
import { Query } from '../query';

interface Props {
  onFilterChange: (changes: Partial<Query>) => void;
  rule: T.Rule;
}

export default class SimilarRulesFilter extends React.PureComponent<Props> {
  handleLanguageClick = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    event.currentTarget.blur();
    if (this.props.rule.lang) {
      this.props.onFilterChange({ languages: [this.props.rule.lang] });
    }
  };

  handleTypeClick = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    event.currentTarget.blur();
    this.props.onFilterChange({ types: [this.props.rule.type] });
  };

  handleSeverityClick = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    event.currentTarget.blur();
    if (this.props.rule.severity) {
      this.props.onFilterChange({ severities: [this.props.rule.severity] });
    }
  };

  handleTagClick = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    event.currentTarget.blur();
    const { tag } = event.currentTarget.dataset;
    if (tag) {
      this.props.onFilterChange({ tags: [tag] });
    }
  };

  render() {
    const { rule } = this.props;
    const { tags = [], sysTags = [], severity } = rule;
    const allTags = [...tags, ...sysTags];

    return (
      <Dropdown
        className="display-inline-block"
        overlay={
          <>
            <ul className="menu">
              <li className="menu-header">{translate('coding_rules.filter_similar_rules')}</li>
              <li>
                <a
                  data-test="coding-rules__similar-language"
                  href="#"
                  onClick={this.handleLanguageClick}>
                  {rule.langName}
                </a>
              </li>

              <li>
                <a
                  className="display-flex-center"
                  data-test="coding-rules__similar-type"
                  href="#"
                  onClick={this.handleTypeClick}>
                  <IssueTypeIcon query={rule.type} />
                  <span className="little-spacer-left">{translate('issue.type', rule.type)}</span>
                </a>
              </li>

              {severity && (
                <li>
                  <a
                    data-test="coding-rules__similar-severity"
                    href="#"
                    onClick={this.handleSeverityClick}>
                    <SeverityHelper className="display-flex-center" severity={rule.severity} />
                  </a>
                </li>
              )}

              {allTags.length > 0 && <li className="divider" />}
              {allTags.map(tag => (
                <li key={tag}>
                  <a
                    data-tag={tag}
                    data-test="coding-rules__similar-tag"
                    href="#"
                    onClick={this.handleTagClick}>
                    <TagsIcon className="icon-half-transparent little-spacer-right text-middle" />
                    <span className="text-middle">{tag}</span>
                  </a>
                </li>
              ))}
            </ul>
          </>
        }>
        <a
          className="js-rule-filter link-no-underline spacer-left dropdown-toggle"
          href="#"
          title={translate('coding_rules.filter_similar_rules')}>
          <FilterIcon className="icon-half-transparent" />
          <DropdownIcon className="icon-half-transparent" />
        </a>
      </Dropdown>
    );
  }
}
