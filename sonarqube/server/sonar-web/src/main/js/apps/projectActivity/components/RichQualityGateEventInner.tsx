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
import { Link } from 'react-router';
import { ResetButtonLink } from 'sonar-ui-common/components/controls/buttons';
import DropdownIcon from 'sonar-ui-common/components/icons/DropdownIcon';
import Level from 'sonar-ui-common/components/ui/Level';
import { translate, translateWithParameters } from 'sonar-ui-common/helpers/l10n';
import { getProjectUrl } from '../../../helpers/urls';

export type RichQualityGateEvent = T.AnalysisEvent & Required<Pick<T.AnalysisEvent, 'qualityGate'>>;

export function isRichQualityGateEvent(event: T.AnalysisEvent): event is RichQualityGateEvent {
  return event.category === 'QUALITY_GATE' && event.qualityGate !== undefined;
}

interface Props {
  event: RichQualityGateEvent;
}

interface State {
  expanded: boolean;
}

export class RichQualityGateEventInner extends React.PureComponent<Props, State> {
  state: State = { expanded: false };

  stopPropagation = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.stopPropagation();
  };

  toggleProjectsList = () => {
    this.setState(state => ({ expanded: !state.expanded }));
  };

  render() {
    const { event } = this.props;
    const { expanded } = this.state;
    return (
      <>
        <span className="note spacer-right">{translate('event.category', event.category)}:</span>
        {event.qualityGate.stillFailing ? (
          <FormattedMessage
            defaultMessage={translate('event.quality_gate.still_x')}
            id="event.quality_gate.still_x"
            values={{ status: <Level level={event.qualityGate.status} small={true} /> }}
          />
        ) : (
          <Level level={event.qualityGate.status} small={true} />
        )}

        <div>
          {event.qualityGate.failing.length > 0 && (
            <ResetButtonLink
              className="project-activity-event-inner-more-link"
              onClick={this.toggleProjectsList}
              stopPropagation={true}>
              {expanded ? translate('hide') : translate('more')}
              <DropdownIcon className="little-spacer-left" turned={expanded} />
            </ResetButtonLink>
          )}
        </div>

        {expanded && (
          <ul className="spacer-left spacer-top">
            {event.qualityGate.failing.map(project => (
              <li className="display-flex-center spacer-top" key={project.key}>
                <Level
                  aria-label={translate('quality_gates.status')}
                  className="spacer-right"
                  level={event.qualityGate.status}
                  small={true}
                />
                <div className="flex-1 text-ellipsis">
                  <Link
                    onClick={this.stopPropagation}
                    title={project.name}
                    to={getProjectUrl(project.key, project.branch)}>
                    <span aria-label={translateWithParameters('project_x', project.name)}>
                      {project.name}
                    </span>
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </>
    );
  }
}
