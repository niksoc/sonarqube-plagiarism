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
import * as classNames from 'classnames';
import { groupBy } from 'lodash';
import * as React from 'react';
import ListFooter from 'sonar-ui-common/components/controls/ListFooter';
import SecurityHotspotIcon from 'sonar-ui-common/components/icons/SecurityHotspotIcon';
import { translate, translateWithParameters } from 'sonar-ui-common/helpers/l10n';
import { HotspotStatusFilter, RawHotspot, RiskExposure } from '../../../types/security-hotspots';
import { groupByCategory, RISK_EXPOSURE_LEVELS } from '../utils';
import HotspotCategory from './HotspotCategory';
import './HotspotList.css';

interface Props {
  hotspots: RawHotspot[];
  hotspotsTotal?: number;
  isStaticListOfHotspots: boolean;
  loadingMore: boolean;
  onHotspotClick: (hotspot: RawHotspot) => void;
  onLoadMore: () => void;
  securityCategories: T.StandardSecurityCategories;
  selectedHotspot: RawHotspot;
  statusFilter: HotspotStatusFilter;
}

interface State {
  expandedCategories: T.Dict<boolean>;
  groupedHotspots: Array<{
    risk: RiskExposure;
    categories: Array<{ key: string; hotspots: RawHotspot[]; title: string }>;
  }>;
}

export default class HotspotList extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      expandedCategories: { [props.selectedHotspot.securityCategory]: true },
      groupedHotspots: this.groupHotspots(props.hotspots, props.securityCategories)
    };
  }

  componentDidUpdate(prevProps: Props) {
    // Force open the category of selected hotspot
    if (
      this.props.selectedHotspot.securityCategory !== prevProps.selectedHotspot.securityCategory
    ) {
      this.handleToggleCategory(this.props.selectedHotspot.securityCategory, true);
    }

    // Compute the hotspot tree from the list
    if (
      this.props.hotspots !== prevProps.hotspots ||
      this.props.securityCategories !== prevProps.securityCategories
    ) {
      const groupedHotspots = this.groupHotspots(
        this.props.hotspots,
        this.props.securityCategories
      );
      this.setState({ groupedHotspots });
    }
  }

  groupHotspots = (hotspots: RawHotspot[], securityCategories: T.StandardSecurityCategories) => {
    const risks = groupBy(hotspots, h => h.vulnerabilityProbability);

    return RISK_EXPOSURE_LEVELS.map(risk => ({
      risk,
      categories: groupByCategory(risks[risk], securityCategories)
    })).filter(risk => risk.categories.length > 0);
  };

  handleToggleCategory = (categoryKey: string, value: boolean) => {
    this.setState(({ expandedCategories }) => ({
      expandedCategories: { ...expandedCategories, [categoryKey]: value }
    }));
  };

  render() {
    const {
      hotspots,
      hotspotsTotal,
      isStaticListOfHotspots,
      loadingMore,
      selectedHotspot,
      statusFilter
    } = this.props;

    const { expandedCategories, groupedHotspots } = this.state;

    return (
      <div className="huge-spacer-bottom">
        <h1 className="hotspot-list-header bordered-bottom">
          <SecurityHotspotIcon className="spacer-right" />
          {translateWithParameters(
            isStaticListOfHotspots ? 'hotspots.list_title' : `hotspots.list_title.${statusFilter}`,
            hotspots.length
          )}
        </h1>
        <ul className="big-spacer-bottom">
          {groupedHotspots.map(riskGroup => (
            <li className="big-spacer-bottom" key={riskGroup.risk}>
              <div className="hotspot-risk-header little-spacer-left">
                <span>{translate('hotspots.risk_exposure')}:</span>
                <div className={classNames('hotspot-risk-badge', 'spacer-left', riskGroup.risk)}>
                  {translate('risk_exposure', riskGroup.risk)}
                </div>
              </div>
              <ul>
                {riskGroup.categories.map(cat => (
                  <li className="spacer-bottom" key={cat.key}>
                    <HotspotCategory
                      categoryKey={cat.key}
                      expanded={expandedCategories[cat.key]}
                      hotspots={cat.hotspots}
                      onHotspotClick={this.props.onHotspotClick}
                      onToggleExpand={this.handleToggleCategory}
                      selectedHotspot={selectedHotspot}
                      title={cat.title}
                    />
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
        <ListFooter
          count={hotspots.length}
          loadMore={!loadingMore ? this.props.onLoadMore : undefined}
          loading={loadingMore}
          total={hotspotsTotal}
        />
      </div>
    );
  }
}
