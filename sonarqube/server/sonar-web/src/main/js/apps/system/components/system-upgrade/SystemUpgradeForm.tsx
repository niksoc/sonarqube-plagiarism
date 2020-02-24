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
import { ResetButtonLink } from 'sonar-ui-common/components/controls/buttons';
import Modal from 'sonar-ui-common/components/controls/Modal';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { withAppState } from '../../../../components/hoc/withAppState';
import { EditionKey } from '../../../../types/editions';
import { SystemUpgrade } from '../../../../types/system';
import SystemUpgradeItem from './SystemUpgradeItem';

interface Props {
  appState: Pick<T.AppState, 'edition'>;
  onClose: () => void;
  systemUpgrades: SystemUpgrade[][];
}

interface State {
  upgrading: boolean;
}

export class SystemUpgradeForm extends React.PureComponent<Props, State> {
  state: State = { upgrading: false };

  render() {
    const { upgrading } = this.state;
    const { appState, systemUpgrades } = this.props;
    const header = translate('system.system_upgrade');
    return (
      <Modal contentLabel={header} onRequestClose={this.props.onClose}>
        <div className="modal-head">
          <h2>{header}</h2>
        </div>
        <div className="modal-body">
          {systemUpgrades.map((upgrades, idx) => (
            <SystemUpgradeItem
              edition={
                appState.edition as EditionKey /* TODO: Fix once AppState is no longer ambiant. */
              }
              key={upgrades[upgrades.length - 1].version}
              systemUpgrades={upgrades}
              type={
                idx === 0 ? translate('system.latest_version') : translate('system.lts_version')
              }
            />
          ))}
        </div>
        <div className="modal-foot">
          {upgrading && <i className="spinner spacer-right" />}
          <a
            className="pull-left"
            href="https://www.sonarqube.org/downloads/"
            rel="noopener noreferrer"
            target="_blank">
            {translate('system.see_sonarqube_downloads')}
          </a>
          <ResetButtonLink onClick={this.props.onClose}>{translate('cancel')}</ResetButtonLink>
        </div>
      </Modal>
    );
  }
}

export default withAppState(SystemUpgradeForm);
