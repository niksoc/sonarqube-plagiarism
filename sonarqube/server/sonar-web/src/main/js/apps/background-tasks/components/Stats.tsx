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
import StatPendingCount from './StatPendingCount';
import StatPendingTime from './StatPendingTime';
import StatStillFailing from './StatStillFailing';

export interface Props {
  component?: Pick<T.Component, 'key'>;
  failingCount?: number;
  onCancelAllPending: () => void;
  onShowFailing: () => void;
  pendingCount?: number;
  pendingTime?: number;
}

export default function Stats({ component, pendingCount, pendingTime, ...props }: Props) {
  return (
    <section className="big-spacer-top big-spacer-bottom">
      <StatPendingCount onCancelAllPending={props.onCancelAllPending} pendingCount={pendingCount} />
      {!component && (
        <StatPendingTime
          className="huge-spacer-left"
          pendingCount={pendingCount}
          pendingTime={pendingTime}
        />
      )}
      {!component && (
        <StatStillFailing
          className="huge-spacer-left"
          failingCount={props.failingCount}
          onShowFailing={props.onShowFailing}
        />
      )}
    </section>
  );
}
