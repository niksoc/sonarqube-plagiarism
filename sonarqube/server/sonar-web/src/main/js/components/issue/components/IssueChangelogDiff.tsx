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
import { translate, translateWithParameters } from 'sonar-ui-common/helpers/l10n';
import { formatMeasure } from 'sonar-ui-common/helpers/measures';

interface Props {
  diff: T.IssueChangelogDiff;
}

export default function IssueChangelogDiff({ diff }: Props) {
  if (diff.key === 'file') {
    return (
      <p>
        {translateWithParameters(
          'issue.change.file_move',
          diff.oldValue || '',
          diff.newValue || ''
        )}
      </p>
    );
  } else if (['from_long_branch', 'from_branch'].includes(diff.key)) {
    return (
      <p>
        {translateWithParameters(
          'issue.change.from_branch',
          diff.oldValue || '',
          diff.newValue || ''
        )}
      </p>
    );
  } else if (diff.key === 'from_short_branch') {
    // Applies to both legacy short lived branch and pull request
    return (
      <p>
        {translateWithParameters(
          'issue.change.from_non_branch',
          diff.oldValue || '',
          diff.newValue || ''
        )}
      </p>
    );
  } else if (diff.key === 'line') {
    return <p>{translateWithParameters('issue.changelog.line_removed_X', diff.oldValue || '')}</p>;
  }

  let message;
  if (diff.newValue != null) {
    let { newValue } = diff;
    if (diff.key === 'effort') {
      newValue = formatMeasure(diff.newValue, 'WORK_DUR');
    }
    message = translateWithParameters(
      'issue.changelog.changed_to',
      translate('issue.changelog.field', diff.key),
      newValue
    );
  } else {
    message = translateWithParameters(
      'issue.changelog.removed',
      translate('issue.changelog.field', diff.key)
    );
  }

  if (diff.oldValue != null) {
    let { oldValue } = diff;
    if (diff.key === 'effort') {
      oldValue = formatMeasure(diff.oldValue, 'WORK_DUR');
    }
    message += ` (${translateWithParameters('issue.changelog.was', oldValue)})`;
  }
  return <p>{message}</p>;
}
