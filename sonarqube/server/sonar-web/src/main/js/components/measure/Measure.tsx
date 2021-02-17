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
import Tooltip from 'sonar-ui-common/components/controls/Tooltip';
import Level from 'sonar-ui-common/components/ui/Level';
import Rating from 'sonar-ui-common/components/ui/Rating';
import { formatMeasure } from 'sonar-ui-common/helpers/measures';
import { getRatingTooltip } from './utils';

interface Props {
  className?: string;
  decimals?: number | null;
  metricKey: string;
  metricType: string;
  small?: boolean;
  value: string | undefined;
}

export default function Measure({
  className,
  decimals,
  metricKey,
  metricType,
  small,
  value
}: Props) {
  if (value === undefined) {
    return <span className={className}>–</span>;
  }

  if (metricType === 'LEVEL') {
    return <Level className={className} level={value} small={small} />;
  }

  if (metricType !== 'RATING') {
    const formattedValue = formatMeasure(value, metricType, { decimals });
    return <span className={className}>{formattedValue != null ? formattedValue : '–'}</span>;
  }

  const tooltip = getRatingTooltip(metricKey, Number(value));
  const rating = <Rating small={small} value={value} />;
  if (tooltip) {
    return (
      <Tooltip overlay={tooltip}>
        <span className={className}>{rating}</span>
      </Tooltip>
    );
  }
  return rating;
}
