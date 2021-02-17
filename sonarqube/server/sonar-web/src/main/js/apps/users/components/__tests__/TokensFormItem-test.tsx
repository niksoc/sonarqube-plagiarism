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
import { shallow } from 'enzyme';
import * as React from 'react';
import { click, waitAndUpdate } from 'sonar-ui-common/helpers/testUtils';
import { revokeToken } from '../../../../api/user-tokens';
import TokensFormItem from '../TokensFormItem';

jest.mock('../../../../components/intl/DateFormatter');
jest.mock('../../../../components/intl/DateFromNow');
jest.mock('../../../../components/intl/DateTimeFormatter');

jest.mock('../../../../api/user-tokens', () => ({
  revokeToken: jest.fn().mockResolvedValue(undefined)
}));

const userToken: T.UserToken = {
  name: 'foo',
  createdAt: '2019-01-15T15:06:33+0100',
  lastConnectionDate: '2019-01-18T15:06:33+0100'
};

beforeEach(() => {
  (revokeToken as jest.Mock).mockClear();
});

it('should render correctly', () => {
  expect(shallowRender()).toMatchSnapshot();
  expect(shallowRender({ deleteConfirmation: 'modal' })).toMatchSnapshot();
});

it('should revoke the token using inline confirmation', async () => {
  const onRevokeToken = jest.fn();
  const wrapper = shallowRender({ deleteConfirmation: 'inline', onRevokeToken });
  expect(wrapper.find('Button')).toMatchSnapshot();
  click(wrapper.find('Button'));
  expect(wrapper.find('Button')).toMatchSnapshot();
  click(wrapper.find('Button'));
  expect(wrapper.find('DeferredSpinner').prop('loading')).toBe(true);
  await waitAndUpdate(wrapper);
  expect(revokeToken).toHaveBeenCalledWith({ login: 'luke', name: 'foo' });
  expect(onRevokeToken).toHaveBeenCalledWith(userToken);
});

it('should revoke the token using modal confirmation', async () => {
  const onRevokeToken = jest.fn();
  const wrapper = shallowRender({ deleteConfirmation: 'modal', onRevokeToken });
  wrapper.find('ConfirmButton').prop<Function>('onConfirm')();
  expect(revokeToken).toHaveBeenCalledWith({ login: 'luke', name: 'foo' });
  await waitAndUpdate(wrapper);
  expect(onRevokeToken).toHaveBeenCalledWith(userToken);
});

function shallowRender(props: Partial<TokensFormItem['props']> = {}) {
  return shallow(
    <TokensFormItem
      deleteConfirmation="inline"
      login="luke"
      onRevokeToken={jest.fn()}
      token={userToken}
      {...props}
    />
  );
}
