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
import { flatMap } from 'lodash';
import * as React from 'react';
import { translate } from 'sonar-ui-common/helpers/l10n';
import CoverageFilter from '../filters/CoverageFilter';
import DuplicationsFilter from '../filters/DuplicationsFilter';
import LanguagesFilterContainer from '../filters/LanguagesFilterContainer';
import MaintainabilityFilter from '../filters/MaintainabilityFilter';
import NewCoverageFilter from '../filters/NewCoverageFilter';
import NewDuplicationsFilter from '../filters/NewDuplicationsFilter';
import NewLinesFilter from '../filters/NewLinesFilter';
import NewMaintainabilityFilter from '../filters/NewMaintainabilityFilter';
import NewReliabilityFilter from '../filters/NewReliabilityFilter';
import NewSecurityFilter from '../filters/NewSecurityFilter';
import QualityGateFilter from '../filters/QualityGateFilter';
import ReliabilityFilter from '../filters/ReliabilityFilter';
import SecurityFilter from '../filters/SecurityFilter';
import SecurityReviewFilter from '../filters/SecurityReviewFilter';
import SizeFilter from '../filters/SizeFilter';
import TagsFilter from '../filters/TagsFilter';
import { hasFilterParams } from '../query';
import { Facets } from '../types';
import ClearAll from './ClearAll';
import FavoriteFilterContainer from './FavoriteFilterContainer';

interface Props {
  facets?: Facets;
  onClearAll: () => void;
  onQueryChange: (change: T.RawQuery) => void;
  organization?: { key: string };
  query: T.RawQuery;
  showFavoriteFilter: boolean;
  view: string;
  visualization: string;
}

export default function PageSidebar(props: Props) {
  const { facets, onQueryChange, query, organization, view, visualization } = props;
  const isFiltered = hasFilterParams(query);
  const isLeakView = view === 'leak';
  const maxFacetValue = getMaxFacetValue(facets);
  const facetProps = { onQueryChange, maxFacetValue, organization, query };

  let linkQuery: T.RawQuery | undefined = undefined;
  if (view !== 'overall') {
    linkQuery = { view };

    if (view === 'visualizations') {
      linkQuery.visualization = visualization;
    }
  }

  return (
    <div>
      {props.showFavoriteFilter && (
        <FavoriteFilterContainer organization={organization} query={linkQuery} />
      )}

      <div className="projects-facets-header clearfix">
        {isFiltered && <ClearAll onClearAll={props.onClearAll} />}

        <h3>{translate('filters')}</h3>
      </div>
      <QualityGateFilter {...facetProps} facet={getFacet(facets, 'gate')} value={query.gate} />
      {!isLeakView && (
        <>
          <ReliabilityFilter
            {...facetProps}
            facet={getFacet(facets, 'reliability')}
            value={query.reliability}
          />
          <SecurityFilter
            {...facetProps}
            facet={getFacet(facets, 'security')}
            value={query.security}
          />

          <SecurityReviewFilter
            {...facetProps}
            facet={getFacet(facets, 'security_review_rating')}
            value={query.security_review_rating}
          />

          <MaintainabilityFilter
            {...facetProps}
            facet={getFacet(facets, 'maintainability')}
            value={query.maintainability}
          />
          <CoverageFilter
            {...facetProps}
            facet={getFacet(facets, 'coverage')}
            value={query.coverage}
          />
          <DuplicationsFilter
            {...facetProps}
            facet={getFacet(facets, 'duplications')}
            value={query.duplications}
          />
          <SizeFilter {...facetProps} facet={getFacet(facets, 'size')} value={query.size} />
        </>
      )}
      {isLeakView && (
        <>
          <NewReliabilityFilter
            {...facetProps}
            facet={getFacet(facets, 'new_reliability')}
            value={query.new_reliability}
          />
          <NewSecurityFilter
            {...facetProps}
            facet={getFacet(facets, 'new_security')}
            value={query.new_security}
          />
          <SecurityReviewFilter
            {...facetProps}
            className="leak-facet-box"
            facet={getFacet(facets, 'new_security_review_rating')}
            property="new_security_review_rating"
            value={query.new_security_review_rating}
          />
          <NewMaintainabilityFilter
            {...facetProps}
            facet={getFacet(facets, 'new_maintainability')}
            value={query.new_maintainability}
          />
          <NewCoverageFilter
            {...facetProps}
            facet={getFacet(facets, 'new_coverage')}
            value={query.new_coverage}
          />
          <NewDuplicationsFilter
            {...facetProps}
            facet={getFacet(facets, 'new_duplications')}
            value={query.new_duplications}
          />
          <NewLinesFilter
            {...facetProps}
            facet={getFacet(facets, 'new_lines')}
            value={query.new_lines}
          />
        </>
      )}
      <LanguagesFilterContainer
        {...facetProps}
        facet={getFacet(facets, 'languages')}
        value={query.languages}
      />
      <TagsFilter {...facetProps} facet={getFacet(facets, 'tags')} value={query.tags} />
    </div>
  );
}

function getFacet(facets: Facets | undefined, name: string) {
  return facets && facets[name];
}

function getMaxFacetValue(facets?: Facets) {
  return facets && Math.max(...flatMap(Object.values(facets), facet => Object.values(facet)));
}
