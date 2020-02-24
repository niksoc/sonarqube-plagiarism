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
import * as classNames from 'classnames';
import * as React from 'react';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { formatMeasure } from 'sonar-ui-common/helpers/measures';
import { findMeasure } from '../../../helpers/measures';
import { getMeasurementAfterMergeMetricKey, MeasurementType } from '../utils';

export interface AfterMergeEstimateProps {
  className?: string;
  measures: T.MeasureEnhanced[];
  type: MeasurementType;
}

export function AfterMergeEstimate({ className, measures, type }: AfterMergeEstimateProps) {
  const afterMergeMetric = getMeasurementAfterMergeMetricKey(type);

  const measure = findMeasure(measures, afterMergeMetric);

  if (!measure || measure.value === undefined) {
    return null;
  }

  return (
    <div className={classNames(className, 'display-flex-center')}>
      <span className="huge">{formatMeasure(measure.value, 'PERCENT')}</span>
      <span className="label flex-1 spacer-left text-right">
        {translate('component_measures.facet_category.overall_category.estimated')}
      </span>
    </div>
  );
}

export default React.memo(AfterMergeEstimate);
