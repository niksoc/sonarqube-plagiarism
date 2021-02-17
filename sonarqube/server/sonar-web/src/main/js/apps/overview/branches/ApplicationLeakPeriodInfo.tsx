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
import HelpTooltip from 'sonar-ui-common/components/controls/HelpTooltip';
import { translateWithParameters } from 'sonar-ui-common/helpers/l10n';
import DateFromNow from '../../../components/intl/DateFromNow';
import { ApplicationPeriod } from '../../../types/application';

export interface ApplicationLeakPeriodInfoProps {
  leakPeriod: ApplicationPeriod;
}

export function ApplicationLeakPeriodInfo({ leakPeriod }: ApplicationLeakPeriodInfoProps) {
  return (
    <div className="note spacer-top display-inline-flex-center">
      <DateFromNow date={leakPeriod.date}>
        {fromNow => translateWithParameters('overview.started_x', fromNow)}
      </DateFromNow>
      <HelpTooltip
        className="little-spacer-left"
        overlay={translateWithParameters(
          'overview.max_new_code_period_from_x',
          leakPeriod.projectName
        )}
      />
    </div>
  );
}

export default React.memo(ApplicationLeakPeriodInfo);
