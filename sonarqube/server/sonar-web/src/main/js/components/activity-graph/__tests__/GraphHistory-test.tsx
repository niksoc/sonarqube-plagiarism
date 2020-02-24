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
import { parseDate } from 'sonar-ui-common/helpers/dates';
import GraphHistory from '../GraphHistory';
import { DEFAULT_GRAPH } from '../utils';

const SERIES = [
  {
    name: 'bugs',
    translatedName: 'metric.bugs.name',
    data: [
      { x: parseDate('2016-10-27T16:33:50+0200'), y: 5 },
      { x: parseDate('2016-10-27T12:21:15+0200'), y: 16 },
      { x: parseDate('2016-10-26T12:17:29+0200'), y: 12 }
    ],
    type: 'INT'
  }
];

const DEFAULT_PROPS: GraphHistory['props'] = {
  events: [],
  graph: DEFAULT_GRAPH,
  leakPeriodDate: parseDate('2017-05-16T13:50:02+0200'),
  isCustom: false,
  measuresHistory: [],
  metricsType: 'INT',
  removeCustomMetric: () => {},
  showAreas: true,
  series: SERIES,
  updateGraphZoom: () => {},
  updateSelectedDate: () => {},
  updateTooltip: () => {}
};

it('should correctly render a graph', () => {
  expect(shallow(<GraphHistory {...DEFAULT_PROPS} />)).toMatchSnapshot();
});
