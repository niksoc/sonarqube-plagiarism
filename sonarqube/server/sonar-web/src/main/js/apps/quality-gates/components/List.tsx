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
import { Link } from 'react-router';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getQualityGateUrl } from '../../../helpers/urls';
import BuiltInQualityGateBadge from './BuiltInQualityGateBadge';

interface Props {
  organization?: string;
  qualityGates: T.QualityGate[];
}

export default function List({ organization, qualityGates }: Props) {
  return (
    <div className="list-group">
      {qualityGates.map(qualityGate => (
        <Link
          activeClassName="active"
          className="list-group-item display-flex-center"
          data-id={qualityGate.id}
          key={qualityGate.id}
          to={getQualityGateUrl(String(qualityGate.id), organization)}>
          <span className="flex-1">{qualityGate.name}</span>
          {qualityGate.isDefault && (
            <span className="badge little-spacer-left">{translate('default')}</span>
          )}
          {qualityGate.isBuiltIn && <BuiltInQualityGateBadge className="little-spacer-left" />}
        </Link>
      ))}
    </div>
  );
}
