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
import { getJSON, post } from 'sonar-ui-common/helpers/request';
import throwGlobalError from '../app/utils/throwGlobalError';

export function getNewCodePeriod(data?: {
  project?: string;
  branch?: string;
}): Promise<T.Omit<T.NewCodePeriod, 'effectiveValue'>> {
  return getJSON('/api/new_code_periods/show', data).catch(throwGlobalError);
}

export function setNewCodePeriod(data: {
  project?: string;
  branch?: string;
  type: T.NewCodePeriodSettingType;
  value?: string;
}): Promise<void> {
  return post('/api/new_code_periods/set', data).catch(throwGlobalError);
}

export function resetNewCodePeriod(data: { project?: string; branch?: string }): Promise<void> {
  return post('/api/new_code_periods/unset', data).catch(throwGlobalError);
}

export function listBranchesNewCodePeriod(data: {
  project: string;
}): Promise<{ newCodePeriods: T.NewCodePeriodBranch[] }> {
  return getJSON('/api/new_code_periods/list', data).catch(throwGlobalError);
}
