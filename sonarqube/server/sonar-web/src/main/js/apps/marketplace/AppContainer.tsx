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
import { connect } from 'react-redux';
import AdminContext from '../../app/components/AdminContext';
import { getAppState, getGlobalSettingValue, Store } from '../../store/rootReducer';
import { EditionKey } from '../../types/editions';
import App from './App';

interface OwnProps {
  location: { pathname: string; query: T.RawQuery };
}

interface StateToProps {
  currentEdition?: EditionKey;
  standaloneMode?: boolean;
  updateCenterActive: boolean;
}

const mapStateToProps = (state: Store) => {
  const updateCenterActive = getGlobalSettingValue(state, 'sonar.updatecenter.activate');
  return {
    currentEdition: getAppState(state).edition as EditionKey, // TODO: Fix once AppState is no longer ambiant.
    standaloneMode: getAppState(state).standalone,
    updateCenterActive: Boolean(updateCenterActive && updateCenterActive.value === 'true')
  };
};

const WithAdminContext = (props: StateToProps & OwnProps) => (
  <AdminContext.Consumer>
    {({ fetchPendingPlugins, pendingPlugins }) => (
      <App fetchPendingPlugins={fetchPendingPlugins} pendingPlugins={pendingPlugins} {...props} />
    )}
  </AdminContext.Consumer>
);

export default connect(mapStateToProps)(WithAdminContext);
