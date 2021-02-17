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
import * as classNames from 'classnames';
import * as React from 'react';
import { FormattedMessage } from 'react-intl';
import DeferredSpinner from 'sonar-ui-common/components/ui/DeferredSpinner';
import { translate } from 'sonar-ui-common/helpers/l10n';

interface Props {
  allowMore: boolean;
  loadingMore?: string;
  onMoreClick: (qualifier: string) => void;
  onSelect: (qualifier: string) => void;
  qualifier: string;
  selected: boolean;
}

export default class SearchShowMore extends React.PureComponent<Props> {
  handleMoreClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.blur();
    const { qualifier } = event.currentTarget.dataset;
    if (qualifier) {
      this.props.onMoreClick(qualifier);
    }
  };

  handleMoreMouseEnter = (event: React.MouseEvent<HTMLAnchorElement>) => {
    const { qualifier } = event.currentTarget.dataset;
    if (qualifier) {
      this.props.onSelect(`qualifier###${qualifier}`);
    }
  };

  render() {
    const { loadingMore, qualifier, selected } = this.props;

    return (
      <li className={classNames('menu-footer', { active: selected })} key={`more-${qualifier}`}>
        <DeferredSpinner className="navbar-search-icon" loading={loadingMore === qualifier}>
          <a
            className={classNames({ 'cursor-not-allowed': !this.props.allowMore })}
            data-qualifier={qualifier}
            href="#"
            onClick={this.handleMoreClick}
            onMouseEnter={this.handleMoreMouseEnter}>
            <div className="pull-right text-muted-2 menu-footer-note">
              <FormattedMessage
                defaultMessage={translate('search.show_more.hint')}
                id="search.show_more.hint"
                values={{
                  key: <span className="shortcut-button shortcut-button-small">Enter</span>
                }}
              />
            </div>
            <span>{translate('show_more')}</span>
          </a>
        </DeferredSpinner>
      </li>
    );
  }
}
