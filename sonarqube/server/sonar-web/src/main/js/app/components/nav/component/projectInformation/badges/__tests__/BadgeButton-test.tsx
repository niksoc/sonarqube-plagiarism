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
import { click } from 'sonar-ui-common/helpers/testUtils';
import BadgeButton from '../BadgeButton';
import { BadgeType } from '../utils';

it('should display correctly', () => {
  expect(getWrapper()).toMatchSnapshot();
  expect(getWrapper({ selected: true })).toMatchSnapshot();
  expect(getWrapper({ type: BadgeType.measure })).toMatchSnapshot();
});

it('should return the badge type on click', () => {
  const onClick = jest.fn();
  const wrapper = getWrapper({ onClick });
  click(wrapper.find('Button'));
  expect(onClick).toHaveBeenCalledWith(BadgeType.marketing);
});

function getWrapper(props = {}) {
  return shallow(
    <BadgeButton
      onClick={jest.fn()}
      selected={false}
      type={BadgeType.marketing}
      url="http://foo.bar"
      {...props}
    />
  );
}
