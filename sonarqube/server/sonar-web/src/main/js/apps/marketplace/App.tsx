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
import { sortBy, uniqBy } from 'lodash';
import * as React from 'react';
import { Helmet } from 'react-helmet-async';
import { translate } from 'sonar-ui-common/helpers/l10n';
import {
  getAvailablePlugins,
  getInstalledPlugins,
  getInstalledPluginsWithUpdates,
  getPluginUpdates,
  Plugin,
  PluginPendingResult
} from '../../api/plugins';
import Suggestions from '../../app/components/embed-docs-modal/Suggestions';
import { Location, Router, withRouter } from '../../components/hoc/withRouter';
import { EditionKey } from '../../types/editions';
import EditionBoxes from './EditionBoxes';
import Footer from './Footer';
import Header from './Header';
import PluginsList from './PluginsList';
import Search from './Search';
import './style.css';
import { filterPlugins, parseQuery, Query, serializeQuery } from './utils';

interface Props {
  currentEdition?: EditionKey;
  fetchPendingPlugins: () => void;
  pendingPlugins: PluginPendingResult;
  location: Location;
  router: Pick<Router, 'push'>;
  standaloneMode?: boolean;
  updateCenterActive: boolean;
}

interface State {
  loadingPlugins: boolean;
  plugins: Plugin[];
}

export class App extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { loadingPlugins: true, plugins: [] };

  componentDidMount() {
    this.mounted = true;
    this.fetchQueryPlugins();
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.location.query.filter !== this.props.location.query.filter) {
      this.fetchQueryPlugins();
    }
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  fetchQueryPlugins = () => {
    const query = parseQuery(this.props.location.query);
    let fetchFunction = this.fetchAllPlugins;

    if (query.filter === 'updates') {
      fetchFunction = getPluginUpdates;
    } else if (query.filter === 'installed') {
      fetchFunction = getInstalledPlugins;
    }

    this.setState({ loadingPlugins: true });
    fetchFunction().then((plugins: Plugin[]) => {
      if (this.mounted) {
        this.setState({
          loadingPlugins: false,
          plugins: sortBy(plugins, 'name')
        });
      }
    }, this.stopLoadingPlugins);
  };

  fetchAllPlugins = (): Promise<Plugin[] | void> => {
    return Promise.all([getInstalledPluginsWithUpdates(), getAvailablePlugins()]).then(
      ([installed, available]) => uniqBy([...installed, ...available.plugins], 'key'),
      this.stopLoadingPlugins
    );
  };

  updateQuery = (newQuery: Partial<Query>) => {
    const query = serializeQuery({ ...parseQuery(this.props.location.query), ...newQuery });
    this.props.router.push({ pathname: this.props.location.pathname, query });
  };

  stopLoadingPlugins = () => {
    if (this.mounted) {
      this.setState({ loadingPlugins: false });
    }
  };

  render() {
    const { currentEdition, standaloneMode, pendingPlugins } = this.props;
    const { loadingPlugins, plugins } = this.state;
    const query = parseQuery(this.props.location.query);
    const filteredPlugins = filterPlugins(plugins, query.search);

    return (
      <div className="page page-limited" id="marketplace-page">
        <Suggestions suggestions="marketplace" />
        <Helmet title={translate('marketplace.page')} />
        <Header currentEdition={currentEdition} />
        <EditionBoxes currentEdition={currentEdition} />
        <header className="page-header">
          <h1 className="page-title">{translate('marketplace.page.open_source_plugins')}</h1>
        </header>
        <Search
          query={query}
          updateCenterActive={this.props.updateCenterActive}
          updateQuery={this.updateQuery}
        />
        {loadingPlugins && <i className="spinner" />}
        {!loadingPlugins && (
          <PluginsList
            pending={pendingPlugins}
            plugins={filteredPlugins}
            readOnly={!standaloneMode}
            refreshPending={this.props.fetchPendingPlugins}
          />
        )}
        {!loadingPlugins && <Footer total={filteredPlugins.length} />}
      </div>
    );
  }
}

export default withRouter(App);
