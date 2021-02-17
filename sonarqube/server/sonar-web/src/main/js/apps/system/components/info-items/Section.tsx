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
import { map } from 'lodash';
import * as React from 'react';
import SysInfoItem from './SysInfoItem';

interface Props {
  name?: string;
  items: T.SysInfoValueObject;
}

export default function Section({ name, items }: Props) {
  return (
    <div className="system-info-section">
      {name && <h4 className="spacer-bottom">{name}</h4>}
      <table className="data zebra" id={name}>
        <tbody>
          {map(items, (value, name) => {
            return (
              <tr key={name}>
                <td className="thin">
                  <div className="system-info-section-item-name">{name}</div>
                </td>
                <td style={{ wordBreak: 'break-all' }}>
                  <SysInfoItem name={name} value={value} />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
