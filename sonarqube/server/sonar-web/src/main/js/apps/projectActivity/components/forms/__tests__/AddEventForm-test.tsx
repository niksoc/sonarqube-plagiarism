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
import AddEventForm from '../AddEventForm';

it('should render correctly', () => {
  expect(
    shallow(
      <AddEventForm
        addEvent={jest.fn()}
        addEventButtonText="add"
        analysis={{
          key: '1',
          date: new Date('2019-01-14T15:44:51.000Z'),
          events: [{ key: '2', category: 'VERSION', name: '1.0' }],
          projectVersion: '1.0',
          manualNewCodePeriodBaseline: false
        }}
        onClose={jest.fn()}
      />
    )
  ).toMatchSnapshot();
});
