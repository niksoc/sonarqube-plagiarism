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
import AlertSuccessIcon from 'sonar-ui-common/components/icons/AlertSuccessIcon';
import { translate } from 'sonar-ui-common/helpers/l10n';
import Step from '../../tutorials/components/Step';

interface Props {
  children: React.ReactNode;
  finished: boolean;
  onOpen: () => void;
  open: boolean;
  organization?: T.Organization;
  stepTitle?: string;
}
export default class OrganizationDetailsStep extends React.PureComponent<Props> {
  renderForm = () => {
    return <div className="boxed-group-inner">{this.props.children}</div>;
  };

  renderResult = () => {
    const { organization } = this.props;
    return organization ? (
      <div className="boxed-group-actions display-flex-center">
        <AlertSuccessIcon className="spacer-right" />
        <strong className="text-limited">{organization.key}</strong>
      </div>
    ) : null;
  };

  render() {
    return (
      <Step
        finished={this.props.finished}
        onOpen={this.props.onOpen}
        open={this.props.open}
        renderForm={this.renderForm}
        renderResult={this.renderResult}
        stepNumber={1}
        stepTitle={
          this.props.stepTitle || translate('onboarding.create_organization.enter_org_details')
        }
      />
    );
  }
}
