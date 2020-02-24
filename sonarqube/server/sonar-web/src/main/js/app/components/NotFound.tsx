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
import { Helmet } from 'react-helmet-async';
import { Link } from 'react-router';
import { translate } from 'sonar-ui-common/helpers/l10n';
import SimpleContainer from './SimpleContainer';

interface Props {
  withContainer?: boolean;
}

export default function NotFound({ withContainer = true }: Props) {
  const Container = withContainer ? SimpleContainer : React.Fragment;
  return (
    <Container>
      <Helmet defaultTitle={translate('404_not_found')} defer={false} />
      <div className="page-wrapper-simple" id="bd">
        <div className="page-simple" id="nonav">
          <h2 className="big-spacer-bottom">{translate('page_not_found')}</h2>
          <p className="spacer-bottom">{translate('address_mistyped_or_page_moved')}</p>
          <p>
            <Link to="/">{translate('go_back_to_homepage')}</Link>
          </p>
        </div>
      </div>
    </Container>
  );
}
