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
import { mockUser } from '../../../../../helpers/testMocks';
import AssigneeSelectionRenderer, {
  HotspotAssigneeSelectRendererProps
} from '../AssigneeSelectionRenderer';

it('should render correctly', () => {
  expect(shallowRender()).toMatchSnapshot();
  expect(shallowRender({ loading: true })).toMatchSnapshot('loading');
  expect(shallowRender({ open: true })).toMatchSnapshot('open');

  const highlightedUser = mockUser({ login: 'highlighted' }) as T.UserActive;
  expect(
    shallowRender({
      highlighted: highlightedUser,
      open: true,
      suggestedUsers: [mockUser() as T.UserActive, highlightedUser]
    })
  ).toMatchSnapshot('open with results');
});

it('should call onSelect when clicked', () => {
  const user = mockUser() as T.UserActive;
  const onSelect = jest.fn();
  const wrapper = shallowRender({
    open: true,
    onSelect,
    suggestedUsers: [user]
  });

  wrapper
    .find('li')
    .at(0)
    .simulate('click');

  expect(onSelect).toBeCalledWith(user);
});

function shallowRender(props?: Partial<HotspotAssigneeSelectRendererProps>) {
  return shallow(
    <AssigneeSelectionRenderer
      loading={false}
      onKeyDown={jest.fn()}
      onSearch={jest.fn()}
      onSelect={jest.fn()}
      open={false}
      {...props}
    />
  );
}
