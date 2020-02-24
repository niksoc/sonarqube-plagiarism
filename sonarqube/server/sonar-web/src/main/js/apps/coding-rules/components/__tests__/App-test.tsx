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
import { waitAndUpdate } from 'sonar-ui-common/helpers/testUtils';
import { getRulesApp } from '../../../../api/rules';
import ScreenPositionHelper from '../../../../components/common/ScreenPositionHelper';
import { isSonarCloud } from '../../../../helpers/system';
import {
  mockAppState,
  mockCurrentUser,
  mockLocation,
  mockOrganization,
  mockRouter,
  mockRule
} from '../../../../helpers/testMocks';
import { App } from '../App';

jest.mock('../../../../components/common/ScreenPositionHelper');

jest.mock('../../../../api/rules', () => {
  const { mockRule } = jest.requireActual('../../../../helpers/testMocks');
  return {
    getRulesApp: jest.fn().mockResolvedValue({ canWrite: true, repositories: [] }),
    searchRules: jest.fn().mockResolvedValue({
      actives: [],
      rawActives: [],
      facets: [],
      rawFacets: [],
      p: 0,
      ps: 100,
      rules: [mockRule(), mockRule()],
      total: 0
    })
  };
});

jest.mock('../../../../api/quality-profiles', () => ({
  searchQualityProfiles: jest.fn().mockResolvedValue({ profiles: [] })
}));

jest.mock('../../../../helpers/system', () => ({
  isSonarCloud: jest.fn().mockResolvedValue(false)
}));

it('should render correctly', async () => {
  const wrapper = shallowRender();
  expect(wrapper).toMatchSnapshot('loading');

  await waitAndUpdate(wrapper);
  expect(wrapper).toMatchSnapshot('loaded');
  expect(wrapper.find(ScreenPositionHelper).dive()).toMatchSnapshot(
    'loaded (ScreenPositionHelper)'
  );

  wrapper.setState({ openRule: mockRule() });
  expect(wrapper).toMatchSnapshot('open rule');
  expect(wrapper.find(ScreenPositionHelper).dive()).toMatchSnapshot(
    'open rule (ScreenPositionHelper)'
  );

  wrapper.setState({ usingPermalink: true });
  expect(wrapper).toMatchSnapshot('using permalink');
});

describe('renderBulkButton', () => {
  it('should be null when the user is not logged in', () => {
    const wrapper = shallowRender({
      currentUser: mockCurrentUser()
    });
    expect(wrapper.instance().renderBulkButton()).toBeNull();
  });

  it('should be null when on SonarCloud and no organization is given', () => {
    (isSonarCloud as jest.Mock).mockReturnValue(true);

    const wrapper = shallowRender({
      organization: undefined
    });
    expect(wrapper.instance().renderBulkButton()).toBeNull();
  });

  it('should be null when the user does not have the sufficient permission', () => {
    (getRulesApp as jest.Mock).mockReturnValue({ canWrite: false, repositories: [] });

    const wrapper = shallowRender();
    expect(wrapper.instance().renderBulkButton()).toBeNull();
  });

  it('should show bulk change button when everything is fine', async () => {
    (getRulesApp as jest.Mock).mockReturnValue({ canWrite: true, repositories: [] });
    const wrapper = shallowRender();
    await waitAndUpdate(wrapper);

    expect(wrapper.instance().renderBulkButton()).toMatchSnapshot();
  });
});

function shallowRender(props: Partial<App['props']> = {}) {
  const organization = mockOrganization();
  return shallow<App>(
    <App
      appState={mockAppState()}
      currentUser={mockCurrentUser({
        isLoggedIn: true
      })}
      languages={{ js: { key: 'js', name: 'JavaScript' } }}
      location={mockLocation()}
      organization={organization}
      params={{}}
      router={mockRouter()}
      routes={[]}
      userOrganizations={[organization]}
      {...props}
    />
  );
}
