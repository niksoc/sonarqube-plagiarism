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
import { translate } from 'sonar-ui-common/helpers/l10n';
import PrivacyBadgeContainer from '../../../../../components/common/PrivacyBadgeContainer';
import { ComponentQualifier } from '../../../../../types/component';
import DrawerLink from './DrawerLink';
import MetaKey from './meta/MetaKey';
import MetaLinks from './meta/MetaLinks';
import MetaQualityGate from './meta/MetaQualityGate';
import MetaQualityProfiles from './meta/MetaQualityProfiles';
import MetaSize from './meta/MetaSize';
import MetaTags from './meta/MetaTags';
import { ProjectInformationPages } from './ProjectInformationPages';

export interface ProjectInformationRendererProps {
  canConfigureNotifications: boolean;
  canUseBadges: boolean;
  component: T.Component;
  measures?: T.Measure[];
  onComponentChange: (changes: {}) => void;
  onPageChange: (page: ProjectInformationPages) => void;
}

export function ProjectInformationRenderer(props: ProjectInformationRendererProps) {
  const { canConfigureNotifications, canUseBadges, component, measures = [] } = props;

  const isApp = component.qualifier === ComponentQualifier.Application;

  return (
    <>
      <div>
        <h2 className="big-padded bordered-bottom">
          {translate(isApp ? 'application' : 'project', 'info.title')}
        </h2>
      </div>

      <div className="overflow-y-auto">
        {(component.description || !isApp) && (
          <div className="big-padded bordered-bottom">
            <div className="display-flex-center">
              <h3 className="spacer-right">{translate('project.info.description')}</h3>
              {component.visibility && (
                <PrivacyBadgeContainer
                  organization={undefined}
                  qualifier={component.qualifier}
                  tooltipProps={{ projectKey: component.key }}
                  visibility={component.visibility}
                />
              )}
            </div>

            {component.description && <p className="spacer-bottom">{component.description}</p>}

            {!isApp && (
              <MetaTags component={component} onComponentChange={props.onComponentChange} />
            )}
          </div>
        )}

        <div className="big-padded bordered-bottom it__project-loc-value">
          <MetaSize component={component} measures={measures} />
        </div>

        {(component.qualityGate ||
          (component.qualityProfiles && component.qualityProfiles.length > 0)) && (
          <>
            <div className="big-padded bordered-bottom">
              {component.qualityGate && <MetaQualityGate qualityGate={component.qualityGate} />}

              {component.qualityProfiles && component.qualityProfiles.length > 0 && (
                <MetaQualityProfiles
                  headerClassName={component.qualityGate ? 'big-spacer-top' : undefined}
                  profiles={component.qualityProfiles}
                />
              )}
            </div>
          </>
        )}

        {!isApp && <MetaLinks component={component} />}

        <div className="big-padded bordered-bottom">
          <MetaKey componentKey={component.key} qualifier={component.qualifier} />
        </div>

        {canUseBadges && (
          <DrawerLink
            label={translate('overview.badges.get_badge', component.qualifier)}
            onPageChange={props.onPageChange}
            to={ProjectInformationPages.badges}
          />
        )}
        {canConfigureNotifications && (
          <DrawerLink
            label={translate('project.info.to_notifications')}
            onPageChange={props.onPageChange}
            to={ProjectInformationPages.notifications}
          />
        )}
      </div>
    </>
  );
}

export default React.memo(ProjectInformationRenderer);
