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
import Toggler from 'sonar-ui-common/components/controls/Toggler';
import { click } from 'sonar-ui-common/helpers/testUtils';
import { mockSetOfBranchAndPullRequest } from '../../../../../../helpers/mocks/branch-like';
import { mockAppState, mockComponent } from '../../../../../../helpers/testMocks';
import { BranchLikeNavigation, BranchLikeNavigationProps } from '../BranchLikeNavigation';

it('should render correctly', () => {
  const wrapper = shallowRender();
  expect(wrapper).toMatchSnapshot();
});

it('should render the menu trigger if branches are enabled', () => {
  const wrapper = shallowRender({ appState: mockAppState({ branchesEnabled: true }) });
  expect(wrapper).toMatchSnapshot();
});

it('should properly toggle menu opening when clicking the anchor', () => {
  const wrapper = shallowRender({ appState: mockAppState({ branchesEnabled: true }) });
  expect(wrapper.find(Toggler).props().open).toBe(false);

  click(wrapper.find('a'));
  expect(wrapper.find(Toggler).props().open).toBe(true);

  click(wrapper.find('a'));
  expect(wrapper.find(Toggler).props().open).toBe(false);
});

it('should properly close menu when toggler asks for', () => {
  const wrapper = shallowRender({ appState: mockAppState({ branchesEnabled: true }) });
  expect(wrapper.find(Toggler).props().open).toBe(false);

  click(wrapper.find('a'));
  expect(wrapper.find(Toggler).props().open).toBe(true);

  wrapper
    .find(Toggler)
    .props()
    .onRequestClose();
  expect(wrapper.find(Toggler).props().open).toBe(false);
});

function shallowRender(props?: Partial<BranchLikeNavigationProps>) {
  const branchLikes = mockSetOfBranchAndPullRequest();

  return shallow(
    <BranchLikeNavigation
      appState={mockAppState()}
      branchLikes={branchLikes}
      component={mockComponent()}
      currentBranchLike={branchLikes[0]}
      {...props}
    />
  );
}
