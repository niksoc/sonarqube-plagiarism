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
import OrganizationUrlInput from '../OrganizationUrlInput';

it('should render correctly', () => {
  const wrapper = shallow(
    <OrganizationUrlInput initialValue="http://my.website" onChange={jest.fn()} />
  );
  expect(wrapper).toMatchSnapshot();
  wrapper.setState({ touched: true });
  expect(wrapper.find('ValidationInput').prop('isValid')).toMatchSnapshot();
});

it('should have an error when the url is invalid', () => {
  expect(
    shallow(<OrganizationUrlInput initialValue="whatever" onChange={jest.fn()} />)
      .find('ValidationInput')
      .prop('isInvalid')
  ).toBe(true);
});
