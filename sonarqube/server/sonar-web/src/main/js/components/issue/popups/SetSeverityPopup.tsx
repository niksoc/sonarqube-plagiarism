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
import { DropdownOverlay } from 'sonar-ui-common/components/controls/Dropdown';
import SeverityIcon from 'sonar-ui-common/components/icons/SeverityIcon';
import { translate } from 'sonar-ui-common/helpers/l10n';
import SelectList from '../../common/SelectList';
import SelectListItem from '../../common/SelectListItem';

type Props = {
  issue: Pick<T.Issue, 'severity'>;
  onSelect: (severity: string) => void;
};

const SEVERITY = ['BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'INFO'];

export default function SetSeverityPopup({ issue, onSelect }: Props) {
  return (
    <DropdownOverlay>
      <SelectList currentItem={issue.severity} items={SEVERITY} onSelect={onSelect}>
        {SEVERITY.map(severity => (
          <SelectListItem className="display-flex-center" item={severity} key={severity}>
            <SeverityIcon className="little-spacer-right" severity={severity} />
            {translate('severity', severity)}
          </SelectListItem>
        ))}
      </SelectList>
    </DropdownOverlay>
  );
}
