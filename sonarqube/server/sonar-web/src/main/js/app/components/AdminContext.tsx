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
import { PluginPendingResult } from '../../api/plugins';

export interface AdminContextInterface {
  fetchSystemStatus: () => void;
  fetchPendingPlugins: () => void;
  pendingPlugins: PluginPendingResult;
  systemStatus: T.SysStatus;
}

export const defaultPendingPlugins = { installing: [], removing: [], updating: [] };
export const defaultSystemStatus = 'UP';

const AdminContext = React.createContext<AdminContextInterface>({
  fetchSystemStatus: () => {},
  fetchPendingPlugins: () => {},
  pendingPlugins: defaultPendingPlugins,
  systemStatus: defaultSystemStatus
});
export default AdminContext;
