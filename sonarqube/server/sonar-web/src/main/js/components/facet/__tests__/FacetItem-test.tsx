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
import FacetItem, { Props } from '../FacetItem';

it('should render active', () => {
  expect(renderFacetItem({ active: true })).toMatchSnapshot();
});

it('should render inactive', () => {
  expect(renderFacetItem({ active: false })).toMatchSnapshot();
});

it('should render stat', () => {
  expect(renderFacetItem({ stat: '13' })).toMatchSnapshot();
});

it('should render disabled', () => {
  expect(renderFacetItem({ disabled: true })).toMatchSnapshot();
});

it('should render half width', () => {
  expect(renderFacetItem({ halfWidth: true })).toMatchSnapshot();
});

it('should call onClick', () => {
  const onClick = jest.fn();
  const wrapper = renderFacetItem({ onClick });
  click(wrapper.find('a'), { currentTarget: { blur() {}, dataset: { value: 'bar' } } });
  expect(onClick).toHaveBeenCalled();
});

function renderFacetItem(props?: Partial<Props>) {
  return shallow(
    <FacetItem
      active={false}
      name="foo"
      onClick={jest.fn()}
      stat={null}
      tooltip="foo"
      value="bar"
      {...props}
    />
  );
}
