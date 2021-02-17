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
import { chunk, flatMap, groupBy, sortBy } from 'lodash';
import { getLocalizedMetricName, translate } from 'sonar-ui-common/helpers/l10n';
import { get, save } from 'sonar-ui-common/helpers/storage';
import { localizeMetric } from '../../helpers/measures';
import { MetricKey } from '../../types/metrics';
import { GraphType, MeasureHistory, Serie } from '../../types/project-activity';

export const DEFAULT_GRAPH = GraphType.issues;

const GRAPHS_METRICS_DISPLAYED: T.Dict<string[]> = {
  [GraphType.issues]: [MetricKey.bugs, MetricKey.code_smells, MetricKey.vulnerabilities],
  [GraphType.coverage]: [MetricKey.lines_to_cover, MetricKey.uncovered_lines],
  [GraphType.duplications]: [MetricKey.ncloc, MetricKey.duplicated_lines]
};

const GRAPHS_METRICS: T.Dict<string[]> = {
  [GraphType.issues]: GRAPHS_METRICS_DISPLAYED[GraphType.issues].concat([
    MetricKey.reliability_rating,
    MetricKey.security_rating,
    MetricKey.sqale_rating
  ]),
  [GraphType.coverage]: [...GRAPHS_METRICS_DISPLAYED[GraphType.coverage], MetricKey.coverage],
  [GraphType.duplications]: [
    ...GRAPHS_METRICS_DISPLAYED[GraphType.duplications],
    MetricKey.duplicated_lines_density
  ]
};

export function isCustomGraph(graph: GraphType) {
  return graph === GraphType.custom;
}

export function getGraphTypes(ignoreCustom = false) {
  const graphs = [GraphType.issues, GraphType.coverage, GraphType.duplications];
  return ignoreCustom ? graphs : [...graphs, GraphType.custom];
}

export function hasDataValues(serie: Serie) {
  return serie.data.some(point => Boolean(point.y || point.y === 0));
}

export function hasHistoryData(series: Serie[]) {
  return series.some(serie => serie.data && serie.data.length > 1);
}

export function getSeriesMetricType(series: Serie[]) {
  return series.length > 0 ? series[0].type : 'INT';
}

export function getDisplayedHistoryMetrics(graph: GraphType, customMetrics: string[]) {
  return isCustomGraph(graph) ? customMetrics : GRAPHS_METRICS_DISPLAYED[graph];
}

export function getHistoryMetrics(graph: GraphType, customMetrics: string[]) {
  return isCustomGraph(graph) ? customMetrics : GRAPHS_METRICS[graph];
}

export function hasHistoryDataValue(series: Serie[]) {
  return series.some(serie => serie.data && serie.data.length > 1 && hasDataValues(serie));
}

export function splitSeriesInGraphs(series: Serie[], maxGraph: number, maxSeries: number) {
  return flatMap(
    groupBy(series, serie => serie.type),
    type => chunk(type, maxSeries)
  ).slice(0, maxGraph);
}

export function generateCoveredLinesMetric(
  uncoveredLines: MeasureHistory,
  measuresHistory: MeasureHistory[]
) {
  const linesToCover = measuresHistory.find(measure => measure.metric === MetricKey.lines_to_cover);
  return {
    data: linesToCover
      ? uncoveredLines.history.map((analysis, idx) => ({
          x: analysis.date,
          y: Number(linesToCover.history[idx].value) - Number(analysis.value)
        }))
      : [],
    name: 'covered_lines',
    translatedName: translate('project_activity.custom_metric.covered_lines'),
    type: 'INT'
  };
}

export function generateSeries(
  measuresHistory: MeasureHistory[],
  graph: GraphType,
  metrics: T.Metric[] | T.Dict<T.Metric>,
  displayedMetrics: string[]
): Serie[] {
  if (displayedMetrics.length <= 0 || measuresHistory === undefined) {
    return [];
  }
  return sortBy(
    measuresHistory
      .filter(measure => displayedMetrics.indexOf(measure.metric) >= 0)
      .map(measure => {
        if (measure.metric === MetricKey.uncovered_lines && !isCustomGraph(graph)) {
          return generateCoveredLinesMetric(measure, measuresHistory);
        }
        const metric = findMetric(measure.metric, metrics);
        return {
          data: measure.history.map(analysis => ({
            x: analysis.date,
            y: metric && metric.type === 'LEVEL' ? analysis.value : Number(analysis.value)
          })),
          name: measure.metric,
          translatedName: metric ? getLocalizedMetricName(metric) : localizeMetric(measure.metric),
          type: metric ? metric.type : 'INT'
        };
      }),
    serie =>
      displayedMetrics.indexOf(serie.name === 'covered_lines' ? 'uncovered_lines' : serie.name)
  );
}

export function saveActivityGraph(
  namespace: string,
  project: string,
  graph: GraphType,
  metrics: string[] = []
) {
  save(namespace, graph, project);
  if (isCustomGraph(graph)) {
    save(`${namespace}.custom`, metrics.join(','), project);
  }
}

export function getActivityGraph(
  namespace: string,
  project: string
): { graph: GraphType; customGraphs: string[] } {
  const customGraphs = get(`${namespace}.custom`, project);
  return {
    graph: (get(namespace, project) as GraphType) || DEFAULT_GRAPH,
    customGraphs: customGraphs ? customGraphs.split(',') : []
  };
}

function findMetric(key: string, metrics: T.Metric[] | T.Dict<T.Metric>) {
  if (Array.isArray(metrics)) {
    return metrics.find(metric => metric.key === key);
  }
  return metrics[key];
}
