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
import { mockComponent } from '../../../../../../helpers/testMocks';
import {
  ProjectInformationRenderer,
  ProjectInformationRendererProps
} from '../ProjectInformationRenderer';

it('should render correctly', () => {
  expect(shallowRender()).toMatchSnapshot('default');
  expect(shallowRender({ canConfigureNotifications: false })).toMatchSnapshot('with notifications');
  expect(shallowRender({ canUseBadges: false })).toMatchSnapshot('no badges');
  expect(shallowRender({ canConfigureNotifications: false, canUseBadges: false })).toMatchSnapshot(
    'no badges, no notifications'
  );
});

it('should render a private project correctly', () => {
  expect(shallowRender({ component: mockComponent({ visibility: 'private' }) })).toMatchSnapshot();
});

it('should render an app correctly', () => {
  const component = mockComponent({ qualifier: 'APP' });
  expect(shallowRender({ component })).toMatchSnapshot('default');
});

it('should handle missing quality profiles and quality gates', () => {
  expect(
    shallowRender({
      component: mockComponent({ qualityGate: undefined, qualityProfiles: undefined })
    })
  ).toMatchSnapshot();
});

function shallowRender(props: Partial<ProjectInformationRendererProps> = {}) {
  return shallow(
    <ProjectInformationRenderer
      canConfigureNotifications={true}
      canUseBadges={true}
      component={mockComponent({ qualifier: 'TRK', visibility: 'public' })}
      onComponentChange={jest.fn()}
      onPageChange={jest.fn()}
      {...props}
    />
  );
}
