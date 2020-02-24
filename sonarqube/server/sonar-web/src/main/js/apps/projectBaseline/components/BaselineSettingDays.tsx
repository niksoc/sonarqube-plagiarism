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
import RadioCard from 'sonar-ui-common/components/controls/RadioCard';
import ValidationInput from 'sonar-ui-common/components/controls/ValidationInput';
import { translate } from 'sonar-ui-common/helpers/l10n';

export interface Props {
  className?: string;
  days: string;
  disabled?: boolean;
  isChanged: boolean;
  isValid: boolean;
  onChangeDays: (value: string) => void;
  onSelect: (selection: T.NewCodePeriodSettingType) => void;
  selected: boolean;
}

export default function BaselineSettingDays(props: Props) {
  const { className, days, disabled, isChanged, isValid, onChangeDays, onSelect, selected } = props;
  return (
    <RadioCard
      className={className}
      disabled={disabled}
      onClick={() => onSelect('NUMBER_OF_DAYS')}
      selected={selected}
      title={translate('baseline.number_days')}>
      <>
        <p className="big-spacer-bottom">{translate('baseline.number_days.description')}</p>
        {selected && (
          <ValidationInput
            error={undefined}
            id="baseline_number_of_days"
            isInvalid={isChanged && !isValid}
            isValid={isChanged && isValid}
            label={translate('baseline.specify_days')}
            required={true}>
            <input onChange={e => onChangeDays(e.currentTarget.value)} type="text" value={days} />
          </ValidationInput>
        )}
      </>
    </RadioCard>
  );
}
