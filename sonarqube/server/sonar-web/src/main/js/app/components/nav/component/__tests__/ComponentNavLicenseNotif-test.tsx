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
import { isValidLicense } from '../../../../../api/marketplace';
import { ComponentNavLicenseNotif } from '../ComponentNavLicenseNotif';

jest.mock('sonar-ui-common/helpers/l10n', () => ({
  ...jest.requireActual('sonar-ui-common/helpers/l10n'),
  hasMessage: jest.fn().mockReturnValue(true)
}));

jest.mock('../../../../../api/marketplace', () => ({
  isValidLicense: jest.fn().mockResolvedValue({ isValidLicense: false })
}));

beforeEach(() => {
  (isValidLicense as jest.Mock<any>).mockClear();
});

it('renders background task license info correctly', async () => {
  let wrapper = getWrapper({
    currentTask: { status: 'FAILED', errorType: 'LICENSING', errorMessage: 'Foo' } as T.Task
  });
  await waitAndUpdate(wrapper);
  expect(wrapper).toMatchSnapshot();

  wrapper = getWrapper({
    appState: { canAdmin: false },
    currentTask: { status: 'FAILED', errorType: 'LICENSING', errorMessage: 'Foo' } as T.Task
  });
  await waitAndUpdate(wrapper);
  expect(wrapper).toMatchSnapshot();
});

it('renders a different message if the license is valid', async () => {
  (isValidLicense as jest.Mock<any>).mockResolvedValueOnce({ isValidLicense: true });
  const wrapper = getWrapper({
    currentTask: { status: 'FAILED', errorType: 'LICENSING', errorMessage: 'Foo' } as T.Task
  });
  await waitAndUpdate(wrapper);
  expect(wrapper).toMatchSnapshot();
});

it('renders correctly for LICENSING_LOC error', async () => {
  (isValidLicense as jest.Mock<any>).mockResolvedValueOnce({ isValidLicense: true });
  const wrapper = getWrapper({
    currentTask: { status: 'FAILED', errorType: 'LICENSING_LOC', errorMessage: 'Foo' } as T.Task
  });
  await waitAndUpdate(wrapper);
  expect(wrapper).toMatchSnapshot();
});

function getWrapper(props: Partial<ComponentNavLicenseNotif['props']> = {}) {
  return shallow(
    <ComponentNavLicenseNotif
      appState={{ canAdmin: true }}
      currentTask={{ errorMessage: 'Foo', errorType: 'LICENSING' } as T.Task}
      {...props}
    />
  );
}
