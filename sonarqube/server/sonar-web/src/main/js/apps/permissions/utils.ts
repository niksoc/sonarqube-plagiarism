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
import { hasMessage, translate } from 'sonar-ui-common/helpers/l10n';
import { isSonarCloud } from '../../helpers/system';

export const PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE = [
  'user',
  'codeviewer',
  'issueadmin',
  'securityhotspotadmin',
  'admin',
  'scan'
];

export const PERMISSIONS_ORDER_GLOBAL = [
  'admin',
  { category: 'administer', permissions: ['gateadmin', 'profileadmin'] },
  'scan',
  { category: 'creator', permissions: ['provisioning'] }
];

export const PERMISSIONS_ORDER_GLOBAL_GOV = [
  'admin',
  { category: 'administer', permissions: ['gateadmin', 'profileadmin'] },
  'scan',
  { category: 'creator', permissions: ['provisioning', 'applicationcreator', 'portfoliocreator'] }
];

export const PERMISSIONS_ORDER_FOR_VIEW = ['user', 'admin'];

export const PERMISSIONS_ORDER_FOR_DEV = ['user', 'admin'];

export const PERMISSIONS_ORDER_BY_QUALIFIER: T.Dict<string[]> = {
  TRK: PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE,
  VW: PERMISSIONS_ORDER_FOR_VIEW,
  SVW: PERMISSIONS_ORDER_FOR_VIEW,
  APP: PERMISSIONS_ORDER_FOR_VIEW,
  DEV: PERMISSIONS_ORDER_FOR_DEV
};

function convertToPermissionDefinition(permission: string, l10nPrefix: string) {
  const getMessage = (messageKey: string) => {
    const cloudMessageKey = `${messageKey}.sonarcloud`;
    return isSonarCloud() && hasMessage(cloudMessageKey)
      ? translate(cloudMessageKey)
      : translate(messageKey);
  };

  const name = getMessage(`${l10nPrefix}.${permission}`);
  const description = getMessage(`${l10nPrefix}.${permission}.desc`);

  return {
    key: permission,
    name,
    description
  };
}

export function convertToPermissionDefinitions(
  permissions: Array<string | { category: string; permissions: string[] }>,
  l10nPrefix: string
): Array<T.PermissionDefinition | T.PermissionDefinitionGroup> {
  return permissions.map(permission => {
    if (typeof permission === 'object') {
      return {
        category: permission.category,
        permissions: permission.permissions.map(permission =>
          convertToPermissionDefinition(permission, l10nPrefix)
        )
      };
    }
    return convertToPermissionDefinition(permission, l10nPrefix);
  });
}

export function isPermissionDefinitionGroup(
  permission?: T.PermissionDefinition | T.PermissionDefinitionGroup
): permission is T.PermissionDefinitionGroup {
  return Boolean(permission && (permission as T.PermissionDefinitionGroup).category);
}
