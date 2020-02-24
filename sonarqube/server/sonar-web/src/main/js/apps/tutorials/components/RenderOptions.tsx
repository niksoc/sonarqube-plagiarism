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
import RadioToggle from 'sonar-ui-common/components/controls/RadioToggle';
import { translate } from 'sonar-ui-common/helpers/l10n';

interface RenderOptionsProps {
  checked: string | undefined;
  name: string;
  onCheck: (checked: string) => void;
  optionLabelKey: string;
  options: string[];
  titleLabelKey?: string;
}

export function RenderOptions({
  checked,
  onCheck,
  optionLabelKey,
  options,
  titleLabelKey
}: RenderOptionsProps) {
  return (
    <div className="big-spacer-top">
      {titleLabelKey && <h4 className="spacer-bottom">{translate(titleLabelKey)}</h4>}

      <RadioToggle
        name={name}
        onCheck={onCheck}
        options={options.map(build => ({
          label: translate(optionLabelKey, build),
          value: build
        }))}
        value={checked}
      />
    </div>
  );
}
