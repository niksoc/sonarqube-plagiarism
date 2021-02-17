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
import { mockGithubDefinition } from '../../../../../helpers/mocks/alm-settings';
import { AlmKeys, GithubBindingDefinition } from '../../../../../types/alm-settings';
import AlmTabRenderer, { AlmTabRendererProps } from '../AlmTabRenderer';

it('should render correctly for multi-ALM binding', () => {
  expect(shallowRender({ loading: true })).toMatchSnapshot('loading');
  expect(shallowRender()).toMatchSnapshot('loaded');
  expect(shallowRender({ editedDefinition: mockGithubDefinition() })).toMatchSnapshot(
    'editing a definition'
  );
  expect(
    shallowRender({
      features: [
        {
          active: true,
          name: 'Foo',
          description: 'Bar'
        },
        {
          active: false,
          name: 'Baz',
          description: 'Bim'
        }
      ]
    })
  ).toMatchSnapshot('with features');
});

it('should render correctly for single-ALM binding', () => {
  expect(shallowRender({ loading: true, multipleAlmEnabled: false })).toMatchSnapshot();
  expect(shallowRender({ multipleAlmEnabled: false })).toMatchSnapshot();
  expect(
    shallowRender({ definitions: [mockGithubDefinition()], multipleAlmEnabled: false })
  ).toMatchSnapshot();
});

function shallowRender(props: Partial<AlmTabRendererProps<GithubBindingDefinition>> = {}) {
  return shallow(
    <AlmTabRenderer
      additionalColumnsHeaders={['url', 'app_id']}
      additionalColumnsKeys={['url', 'appId']}
      alm={AlmKeys.GitHub}
      defaultBinding={mockGithubDefinition()}
      definitions={[mockGithubDefinition()]}
      form={jest.fn()}
      loading={false}
      multipleAlmEnabled={true}
      onCancel={jest.fn()}
      onCreate={jest.fn()}
      onDelete={jest.fn()}
      onEdit={jest.fn()}
      onSubmit={jest.fn()}
      success={false}
      {...props}
    />
  );
}
