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
import GlobalNavMenu from '../GlobalNavMenu';

it('should work with extensions', () => {
  const appState = {
    globalPages: [{ key: 'foo', name: 'Foo' }],
    qualifiers: ['TRK']
  };
  const currentUser = {
    isLoggedIn: false
  };
  const wrapper = shallow(
    <GlobalNavMenu appState={appState} currentUser={currentUser} location={{ pathname: '' }} />
  );
  expect(wrapper.find('Dropdown')).toMatchSnapshot();
});

it('should show administration menu if the user has the rights', () => {
  const appState = {
    canAdmin: true,
    globalPages: [],
    qualifiers: ['TRK']
  };
  const currentUser = {
    isLoggedIn: false
  };
  const wrapper = shallow(
    <GlobalNavMenu appState={appState} currentUser={currentUser} location={{ pathname: '' }} />
  );
  expect(wrapper).toMatchSnapshot();
});
