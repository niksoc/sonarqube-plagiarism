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
import Header, { Props } from '../Header';

jest.mock('../../../helpers/system', () => ({ isSonarCloud: jest.fn().mockReturnValue(false) }));

const organization: T.Organization = {
  key: 'org',
  name: 'org',
  projectVisibility: 'public'
};

it('renders', () => {
  expect(shallowRender()).toMatchSnapshot();
});

it('creates project', () => {
  const onProjectCreate = jest.fn();
  const wrapper = shallowRender({ onProjectCreate });
  click(wrapper.find('#create-project'));
  expect(onProjectCreate).toBeCalledWith();
});

it('changes default visibility', () => {
  const onVisibilityChange = jest.fn();
  const wrapper = shallowRender({ onVisibilityChange });

  click(wrapper.find('.js-change-visibility'));

  const modalWrapper = wrapper.find('ChangeDefaultVisibilityForm');
  expect(modalWrapper).toMatchSnapshot();
  modalWrapper.prop<Function>('onConfirm')('private');
  expect(onVisibilityChange).toBeCalledWith('private');

  modalWrapper.prop<Function>('onClose')();
  wrapper.update();
  expect(wrapper.find('ChangeDefaultVisibilityForm').exists()).toBeFalsy();
});

function shallowRender(props?: { [P in keyof Props]?: Props[P] }) {
  return shallow(
    <Header
      hasProvisionPermission={true}
      onProjectCreate={jest.fn()}
      onVisibilityChange={jest.fn()}
      organization={organization}
      {...props}
    />
  );
}
