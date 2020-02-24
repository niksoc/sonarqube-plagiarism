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
import LockIcon from 'sonar-ui-common/components/icons/LockIcon';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { colors } from '../../../../app/theme';
import { DefaultSpecializedInputProps } from '../../utils';

interface State {
  changing: boolean;
}

export default class InputForPassword extends React.PureComponent<
  DefaultSpecializedInputProps,
  State
> {
  state: State = {
    changing: !this.props.value
  };

  componentWillReceiveProps(nextProps: DefaultSpecializedInputProps) {
    /*
     * Reset `changing` if:
     *  - the value is reset (valueChanged -> !valueChanged)
     *     or
     *  - the value changes from outside the input (i.e. store update/reset/cancel)
     */
    if (
      (this.props.hasValueChanged || this.props.value !== nextProps.value) &&
      !nextProps.hasValueChanged
    ) {
      this.setState({ changing: !nextProps.value });
    }
  }

  handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.onChange(event.target.value);
  };

  handleChangeClick = () => {
    this.setState({ changing: true });
  };

  renderInput() {
    return (
      <form>
        <input className="hidden" type="password" />
        <input
          autoComplete="off"
          autoFocus={this.state.changing && this.props.value}
          className="js-password-input settings-large-input text-top"
          name={this.props.name}
          onChange={this.handleInputChange}
          type="password"
          value={this.props.value}
        />
      </form>
    );
  }

  render() {
    if (this.state.changing) {
      return this.renderInput();
    }

    return (
      <>
        <LockIcon className="text-middle big-spacer-right" fill={colors.gray60} />
        <Button className="text-middle" onClick={this.handleChangeClick}>
          {translate('change_verb')}
        </Button>
      </>
    );
  }
}
