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
import { FormattedMessage } from 'react-intl';
import Tooltip from 'sonar-ui-common/components/controls/Tooltip';
import { translate } from 'sonar-ui-common/helpers/l10n';

interface Props {
  license?: string;
}

export default function PluginLicense({ license }: Props) {
  if (!license) {
    return null;
  }
  return (
    <Tooltip overlay={license}>
      <li className="little-spacer-bottom marketplace-plugin-license">
        <FormattedMessage
          defaultMessage={translate('marketplace.licensed_under_x')}
          id="marketplace.licensed_under_x"
          values={{
            license: <span className="js-plugin-license">{license}</span>
          }}
        />
      </li>
    </Tooltip>
  );
}