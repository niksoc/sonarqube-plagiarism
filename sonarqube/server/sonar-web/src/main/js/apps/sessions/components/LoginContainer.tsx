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
import { Location } from 'history';
import * as React from 'react';
import { connect } from 'react-redux';
import { getReturnUrl } from 'sonar-ui-common/helpers/urls';
import { getIdentityProviders } from '../../../api/users';
import { doLogin } from '../../../store/rootActions';
import Login from './Login';

interface OwnProps {
  location: Pick<Location, 'hash' | 'pathname' | 'query'> & {
    query: { advanced?: string; return_to?: string };
  };
}

interface DispatchToProps {
  doLogin: (login: string, password: string) => Promise<void>;
}

type Props = OwnProps & DispatchToProps;

interface State {
  identityProviders?: T.IdentityProvider[];
}

export class LoginContainer extends React.PureComponent<Props, State> {
  mounted = false;

  state: State = {};

  componentDidMount() {
    this.mounted = true;
    getIdentityProviders().then(
      ({ identityProviders }) => {
        if (this.mounted) {
          this.setState({ identityProviders });
        }
      },
      () => {}
    );
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  handleSuccessfulLogin = () => {
    window.location.href = getReturnUrl(this.props.location);
  };

  handleSubmit = (login: string, password: string) => {
    return this.props.doLogin(login, password).then(this.handleSuccessfulLogin, () => {});
  };

  render() {
    const { location } = this.props;
    const { identityProviders } = this.state;

    if (!identityProviders) {
      return null;
    }

    return (
      <Login
        identityProviders={identityProviders}
        onSubmit={this.handleSubmit}
        returnTo={getReturnUrl(location)}
      />
    );
  }
}

const mapStateToProps = null;
const mapDispatchToProps = { doLogin: doLogin as any };

export default connect(mapStateToProps, mapDispatchToProps)(LoginContainer);
