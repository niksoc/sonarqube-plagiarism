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
import { connect } from 'react-redux';
import Level from 'sonar-ui-common/components/ui/Level';
import { getBranchStatusByBranchLike, Store } from '../../store/rootReducer';
import { BranchLike } from '../../types/branch-like';

interface Props {
  branchLike: BranchLike;
  component: string;
  status?: string;
}

export function BranchStatus({ status }: Props) {
  if (!status) {
    return null;
  }

  return <Level level={status} small={true} />;
}

const mapStateToProps = (state: Store, { branchLike, component }: Props) => {
  const { status } = getBranchStatusByBranchLike(state, component, branchLike);
  return { status };
};

export default connect(mapStateToProps)(BranchStatus);
