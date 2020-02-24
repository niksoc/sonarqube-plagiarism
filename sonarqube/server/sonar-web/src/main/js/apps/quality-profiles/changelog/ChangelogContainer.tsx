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
import { WithRouterProps } from 'react-router';
import { parseDate, toShortNotSoISOString } from 'sonar-ui-common/helpers/dates';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getProfileChangelog } from '../../../api/quality-profiles';
import { withRouter } from '../../../components/hoc/withRouter';
import { Profile, ProfileChangelogEvent } from '../types';
import { getProfileChangelogPath } from '../utils';
import Changelog from './Changelog';
import ChangelogEmpty from './ChangelogEmpty';
import ChangelogSearch from './ChangelogSearch';

interface Props extends Pick<WithRouterProps, 'router' | 'location'> {
  organization: string | null;
  profile: Profile;
}

interface State {
  events?: ProfileChangelogEvent[];
  loading: boolean;
  page?: number;
  total?: number;
}

export class ChangelogContainer extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { loading: true };

  componentDidMount() {
    this.mounted = true;
    this.loadChangelog();
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.location !== this.props.location) {
      this.loadChangelog();
    }
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  stopLoading() {
    if (this.mounted) {
      this.setState({ loading: false });
    }
  }

  loadChangelog() {
    this.setState({ loading: true });
    const {
      location: { query },
      profile
    } = this.props;

    getProfileChangelog(query.since, query.to, profile)
      .then((r: any) => {
        if (this.mounted) {
          this.setState({
            events: r.events,
            total: r.total,
            page: r.p,
            loading: false
          });
        }
      })
      .catch(this.stopLoading);
  }

  loadMore(event: React.SyntheticEvent<HTMLElement>) {
    event.preventDefault();
    event.currentTarget.blur();

    if (this.state.page != null) {
      this.setState({ loading: true });
      const {
        location: { query },
        profile
      } = this.props;

      getProfileChangelog(query.since, query.to, profile, this.state.page + 1)
        .then((r: any) => {
          if (this.mounted && this.state.events) {
            this.setState({
              events: [...this.state.events, ...r.events],
              total: r.total,
              page: r.p,
              loading: false
            });
          }
        })
        .catch(this.stopLoading);
    }
  }

  handleDateRangeChange = ({ from, to }: { from?: Date; to?: Date }) => {
    const path = getProfileChangelogPath(
      this.props.profile.name,
      this.props.profile.language,
      this.props.organization,
      {
        since: from && toShortNotSoISOString(from),
        to: to && toShortNotSoISOString(to)
      }
    );
    this.props.router.push(path);
  };

  handleReset = () => {
    const path = getProfileChangelogPath(
      this.props.profile.name,
      this.props.profile.language,
      this.props.organization
    );
    this.props.router.push(path);
  };

  render() {
    const { query } = this.props.location;

    const shouldDisplayFooter =
      this.state.events != null &&
      this.state.total != null &&
      this.state.events.length < this.state.total;

    return (
      <div className="boxed-group boxed-group-inner js-profile-changelog">
        <header className="spacer-bottom">
          <ChangelogSearch
            dateRange={{
              from: query.since ? parseDate(query.since) : undefined,
              to: query.to ? parseDate(query.to) : undefined
            }}
            onDateRangeChange={this.handleDateRangeChange}
            onReset={this.handleReset}
          />

          {this.state.loading && <i className="spinner spacer-left" />}
        </header>

        {this.state.events != null && this.state.events.length === 0 && <ChangelogEmpty />}

        {this.state.events != null && this.state.events.length > 0 && (
          <Changelog events={this.state.events} organization={this.props.organization} />
        )}

        {shouldDisplayFooter && (
          <footer className="text-center spacer-top small">
            <a href="#" onClick={this.loadMore.bind(this)}>
              {translate('show_more')}
            </a>
          </footer>
        )}
      </div>
    );
  }
}

export default withRouter(ChangelogContainer);
