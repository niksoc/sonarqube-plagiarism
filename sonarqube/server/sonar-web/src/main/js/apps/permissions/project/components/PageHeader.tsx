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
import { Button } from 'sonar-ui-common/components/controls/buttons';
import { translate } from 'sonar-ui-common/helpers/l10n';
import ApplyTemplate from './ApplyTemplate';

interface Props {
  component: T.Component;
  loadHolders: () => void;
  loading: boolean;
}

interface State {
  applyTemplateModal: boolean;
}

export default class PageHeader extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { applyTemplateModal: false };

  componentDidMount() {
    this.mounted = true;
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  handleApplyTemplate = () => {
    this.setState({ applyTemplateModal: true });
  };

  handleApplyTemplateClose = () => {
    if (this.mounted) {
      this.setState({ applyTemplateModal: false });
    }
  };

  render() {
    const { component } = this.props;
    const { configuration } = component;
    const canApplyPermissionTemplate =
      configuration != null && configuration.canApplyPermissionTemplate;

    const description = ['VW', 'SVW', 'APP'].includes(component.qualifier)
      ? translate('roles.page.description_portfolio')
      : translate('roles.page.description2');

    const visibilityDescription =
      component.qualifier === 'TRK' && component.visibility
        ? translate('visibility', component.visibility, 'description', component.qualifier)
        : undefined;

    return (
      <header className="page-header">
        <h1 className="page-title">{translate('permissions.page')}</h1>

        {this.props.loading && <i className="spinner" />}

        {canApplyPermissionTemplate && (
          <div className="page-actions">
            <Button className="js-apply-template" onClick={this.handleApplyTemplate}>
              {translate('projects_role.apply_template')}
            </Button>

            {this.state.applyTemplateModal && (
              <ApplyTemplate
                onApply={this.props.loadHolders}
                onClose={this.handleApplyTemplateClose}
                organization={component.organization}
                project={component}
              />
            )}
          </div>
        )}

        <div className="page-description">
          <p>{description}</p>
          {visibilityDescription && <p>{visibilityDescription}</p>}
        </div>
      </header>
    );
  }
}
