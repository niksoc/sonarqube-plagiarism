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
import { mockEvent, waitAndUpdate } from 'sonar-ui-common/helpers/testUtils';
import { setNewCodePeriod } from '../../../../api/newCodePeriod';
import { mockMainBranch } from '../../../../helpers/mocks/branch-like';
import BranchBaselineSettingModal from '../BranchBaselineSettingModal';

jest.mock('../../../../api/newCodePeriod', () => ({
  setNewCodePeriod: jest.fn().mockResolvedValue({})
}));

it('should render correctly', () => {
  expect(shallowRender()).toMatchSnapshot();
});

it('should display the branch analysis list when necessary', () => {
  const wrapper = shallowRender();

  wrapper.setState({ selected: 'SPECIFIC_ANALYSIS' });

  expect(wrapper.find('BranchAnalysisList')).toHaveLength(1);
});

it('should save correctly', async () => {
  const branch = mockMainBranch({ name: 'branchname' });
  const component = 'compKey';
  const wrapper = shallowRender({
    branch,
    component
  });

  wrapper.setState({ analysis: 'analysis572893', selected: 'SPECIFIC_ANALYSIS' });
  await waitAndUpdate(wrapper);

  wrapper.instance().handleSubmit(mockEvent());
  await new Promise(setImmediate);

  expect(setNewCodePeriod).toHaveBeenCalledWith({
    project: component,
    type: 'SPECIFIC_ANALYSIS',
    value: 'analysis572893',
    branch: 'branchname'
  });
});

it('should disable the save button when saving', () => {
  const wrapper = shallowRender();

  wrapper.setState({ saving: true });

  expect(
    wrapper
      .find('SubmitButton')
      .first()
      .prop('disabled')
  ).toBe(true);
});

it('should disable the save button when date is invalid', () => {
  const wrapper = shallowRender();

  wrapper.setState({ days: 'asdf' });

  expect(
    wrapper
      .find('SubmitButton')
      .first()
      .prop('disabled')
  ).toBe(true);
});

function shallowRender(props: Partial<BranchBaselineSettingModal['props']> = {}) {
  return shallow<BranchBaselineSettingModal>(
    <BranchBaselineSettingModal
      branch={mockMainBranch()}
      component="compKey"
      onClose={jest.fn()}
      {...props}
    />
  );
}
