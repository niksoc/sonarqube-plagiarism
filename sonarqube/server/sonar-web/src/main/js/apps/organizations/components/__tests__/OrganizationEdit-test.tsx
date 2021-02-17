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
import { mockLoggedInUser } from '../../../../helpers/testMocks';
import { OrganizationEdit } from '../OrganizationEdit';

it('smoke test', () => {
  const organization = { key: 'foo', name: 'Foo' };
  const wrapper = shallow(
    <OrganizationEdit
      currentUser={mockLoggedInUser()}
      organization={organization}
      updateOrganization={jest.fn()}
    />
  );
  expect(wrapper).toMatchSnapshot();

  wrapper.setState({
    avatar: 'foo-avatar',
    avatarImage: 'foo-avatar-image',
    description: 'foo-description',
    name: 'New Foo',
    url: 'foo-url'
  });
  expect(wrapper).toMatchSnapshot();

  wrapper.setState({ loading: true });
  expect(wrapper).toMatchSnapshot();
});
