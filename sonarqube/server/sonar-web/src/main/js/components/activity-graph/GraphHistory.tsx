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
import * as React from 'react';
import { AutoSizer } from 'react-virtualized/dist/commonjs/AutoSizer';
import AdvancedTimeline from 'sonar-ui-common/components/charts/AdvancedTimeline';
import { formatMeasure } from 'sonar-ui-common/helpers/measures';
import { getShortType } from '../../helpers/measures';
import { MeasureHistory, Serie } from '../../types/project-activity';
import GraphsLegendCustom from './GraphsLegendCustom';
import GraphsLegendStatic from './GraphsLegendStatic';
import GraphsTooltips from './GraphsTooltips';

interface Props {
  events: T.AnalysisEvent[];
  graph: string;
  graphEndDate?: Date;
  graphStartDate?: Date;
  leakPeriodDate?: Date;
  isCustom?: boolean;
  measuresHistory: MeasureHistory[];
  metricsType: string;
  removeCustomMetric?: (metric: string) => void;
  showAreas: boolean;
  series: Serie[];
  selectedDate?: Date;
  updateGraphZoom?: (from?: Date, to?: Date) => void;
  updateSelectedDate?: (selectedDate?: Date) => void;
  updateTooltip: (selectedDate?: Date) => void;
}

interface State {
  tooltipIdx?: number;
  tooltipXPos?: number;
}

export default class GraphHistory extends React.PureComponent<Props, State> {
  state: State = {};

  formatValue = (tick: string | number) => {
    return formatMeasure(tick, getShortType(this.props.metricsType));
  };

  formatTooltipValue = (tick: string | number) => {
    return formatMeasure(tick, this.props.metricsType);
  };

  updateTooltip = (selectedDate?: Date, tooltipXPos?: number, tooltipIdx?: number) => {
    this.props.updateTooltip(selectedDate);
    this.setState({ tooltipXPos, tooltipIdx });
  };

  render() {
    const {
      events,
      graph,
      graphEndDate,
      graphStartDate,
      isCustom,
      leakPeriodDate,
      measuresHistory,
      metricsType,
      selectedDate,
      series,
      showAreas
    } = this.props;
    const { tooltipIdx, tooltipXPos } = this.state;

    return (
      <div className="activity-graph-container flex-grow display-flex-column display-flex-stretch display-flex-justify-center">
        {isCustom && this.props.removeCustomMetric ? (
          <GraphsLegendCustom removeMetric={this.props.removeCustomMetric} series={series} />
        ) : (
          <GraphsLegendStatic series={series} />
        )}
        <div className="flex-1">
          <AutoSizer>
            {({ height, width }) => (
              <div>
                <AdvancedTimeline
                  endDate={graphEndDate}
                  formatYTick={this.formatValue}
                  height={height}
                  leakPeriodDate={leakPeriodDate}
                  metricType={metricsType}
                  selectedDate={selectedDate}
                  series={series}
                  showAreas={showAreas}
                  startDate={graphStartDate}
                  updateSelectedDate={this.props.updateSelectedDate}
                  updateTooltip={this.updateTooltip}
                  updateZoom={this.props.updateGraphZoom}
                  width={width}
                />
                {selectedDate !== undefined &&
                  tooltipIdx !== undefined &&
                  tooltipXPos !== undefined && (
                    <GraphsTooltips
                      events={events}
                      formatValue={this.formatTooltipValue}
                      graph={graph}
                      graphWidth={width}
                      measuresHistory={measuresHistory}
                      selectedDate={selectedDate}
                      series={series}
                      tooltipIdx={tooltipIdx}
                      tooltipPos={tooltipXPos}
                    />
                  )}
              </div>
            )}
          </AutoSizer>
        </div>
      </div>
    );
  }
}
