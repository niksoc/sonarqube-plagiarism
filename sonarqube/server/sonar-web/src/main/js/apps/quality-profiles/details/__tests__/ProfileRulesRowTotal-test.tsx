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
import ProfileRulesRowTotal from '../ProfileRulesRowTotal';

it('should render correctly', () => {
  expect(
    shallow(<ProfileRulesRowTotal count={3} organization="foo" qprofile="bar" total={10} />)
  ).toMatchSnapshot();
});

it('should render correctly if there is 0 rules', () => {
  expect(
    shallow(<ProfileRulesRowTotal count={0} organization={null} qprofile="bar" total={0} />)
  ).toMatchSnapshot();
});

it('should render correctly if there is missing data', () => {
  expect(
    shallow(<ProfileRulesRowTotal count={5} organization={null} qprofile="bar" total={null} />)
  ).toMatchSnapshot();
  expect(
    shallow(<ProfileRulesRowTotal count={null} organization={null} qprofile="foo" total={10} />)
  ).toMatchSnapshot();
});
