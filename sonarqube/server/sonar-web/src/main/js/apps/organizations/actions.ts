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
import { Dispatch } from 'redux';
import { translate } from 'sonar-ui-common/helpers/l10n';
import * as api from '../../api/organizations';
import { addGlobalSuccessMessage } from '../../store/globalMessages';
import * as actions from '../../store/organizations';

export function updateOrganization(key: string, changes: T.OrganizationBase) {
  return (dispatch: Dispatch<any>) => {
    return api.updateOrganization(key, changes).then(() => {
      dispatch(actions.updateOrganization(key, changes));
      dispatch(addGlobalSuccessMessage(translate('organization.updated')));
    });
  };
}

export function deleteOrganization(key: string) {
  return (dispatch: Dispatch<any>) => {
    return api.deleteOrganization(key).then(() => {
      dispatch(actions.deleteOrganization(key));
    });
  };
}
