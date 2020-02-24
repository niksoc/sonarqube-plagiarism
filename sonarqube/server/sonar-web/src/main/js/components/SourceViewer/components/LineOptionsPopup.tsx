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
import { DropdownOverlay } from 'sonar-ui-common/components/controls/Dropdown';
import { PopupPlacement } from 'sonar-ui-common/components/ui/popups';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getCodeUrl } from '../../../helpers/urls';
import { SourceViewerContext } from '../SourceViewerContext';

interface Props {
  line: T.SourceLine;
}

export default function LineOptionsPopup({ line }: Props) {
  return (
    <SourceViewerContext.Consumer>
      {({ branchLike, file }) => (
        <DropdownOverlay placement={PopupPlacement.RightTop}>
          <div className="source-viewer-bubble-popup nowrap">
            <Link
              className="js-get-permalink"
              onClick={event => {
                event.stopPropagation();
              }}
              rel="noopener noreferrer"
              target="_blank"
              to={getCodeUrl(file.project, branchLike, file.key, line.line)}>
              {translate('component_viewer.get_permalink')}
            </Link>
          </div>
        </DropdownOverlay>
      )}
    </SourceViewerContext.Consumer>
  );
}
