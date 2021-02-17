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
/* eslint-disable sonarjs/no-duplicate-string */
import { shallow } from 'enzyme';
import * as React from 'react';
import { parseDate } from 'sonar-ui-common/helpers/dates';
import GraphsTooltips from '../GraphsTooltips';
import { DEFAULT_GRAPH } from '../utils';

const SERIES_ISSUES = [
  {
    name: 'bugs',
    translatedName: 'Bugs',
    data: [
      {
        x: parseDate('2011-10-01T22:01:00.000Z'),
        y: 3
      },
      {
        x: parseDate('2011-10-25T10:27:41.000Z'),
        y: 0
      }
    ],
    type: 'INT'
  },
  {
    name: 'code_smells',
    translatedName: 'Code Smells',
    data: [
      {
        x: parseDate('2011-10-01T22:01:00.000Z'),
        y: 18
      },
      {
        x: parseDate('2011-10-25T10:27:41.000Z'),
        y: 15
      }
    ],
    type: 'INT'
  },
  {
    name: 'vulnerabilities',
    translatedName: 'Vulnerabilities',
    data: [
      {
        x: parseDate('2011-10-01T22:01:00.000Z'),
        y: 0
      },
      {
        x: parseDate('2011-10-25T10:27:41.000Z'),
        y: 1
      }
    ],
    type: 'INT'
  }
];

const DEFAULT_PROPS: GraphsTooltips['props'] = {
  events: [],
  formatValue: val => 'Formated.' + val,
  graph: DEFAULT_GRAPH,
  graphWidth: 500,
  measuresHistory: [],
  selectedDate: parseDate('2011-10-01T22:01:00.000Z'),
  series: SERIES_ISSUES,
  tooltipIdx: 0,
  tooltipPos: 666
};

it('should render correctly for issues graphs', () => {
  expect(shallow(<GraphsTooltips {...DEFAULT_PROPS} />)).toMatchSnapshot();
});

it('should render correctly for random graphs', () => {
  expect(
    shallow(
      <GraphsTooltips
        {...DEFAULT_PROPS}
        graph="random"
        selectedDate={parseDate('2011-10-25T10:27:41.000Z')}
        tooltipIdx={1}
      />
    )
  ).toMatchSnapshot();
});

it('should not add separators if not needed', () => {
  expect(
    shallow(<GraphsTooltips {...DEFAULT_PROPS} graph="coverage" series={[]} />)
  ).toMatchSnapshot();
});
