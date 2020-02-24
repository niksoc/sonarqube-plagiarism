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
import * as React from 'react';
import { FormattedMessage } from 'react-intl';
import { Link } from 'react-router';
import BoxedGroupAccordion from 'sonar-ui-common/components/controls/BoxedGroupAccordion';
import Radio from 'sonar-ui-common/components/controls/Radio';
import CheckIcon from 'sonar-ui-common/components/icons/CheckIcon';
import { Alert } from 'sonar-ui-common/components/ui/Alert';
import { translate, translateWithParameters } from 'sonar-ui-common/helpers/l10n';
import { colors } from '../../../app/theme';
import { getProjectUrl } from '../../../helpers/urls';
import { BitbucketProject, BitbucketRepository } from '../../../types/alm-integration';
import { CreateProjectModes } from './types';

export interface BitbucketProjectAccordionProps {
  disableRepositories: boolean;
  onClick?: () => void;
  onSelectRepository: (repo: BitbucketRepository) => void;
  open: boolean;
  project: BitbucketProject;
  repositories: BitbucketRepository[];
  selectedRepository?: BitbucketRepository;
  showingAllRepositories: boolean;
}

export default function BitbucketProjectAccordion(props: BitbucketProjectAccordionProps) {
  const {
    disableRepositories,
    open,
    project,
    repositories,
    selectedRepository,
    showingAllRepositories
  } = props;

  const repositoryCount = repositories.length;

  return (
    <BoxedGroupAccordion
      className={classNames('big-spacer-bottom', {
        open,
        'not-clickable': !props.onClick,
        'no-hover': !props.onClick
      })}
      key={project.key}
      onClick={
        props.onClick
          ? props.onClick
          : () => {
              /* noop */
            }
      }
      open={open}
      title={<h3>{project.name}</h3>}>
      {open && (
        <div className="display-flex-wrap">
          {repositoryCount === 0 && (
            <Alert variant="warning">
              <FormattedMessage
                defaultMessage={translate('onboarding.create_project.no_bbs_repos')}
                id="onboarding.create_project.no_bbs_repos"
                values={{
                  link: (
                    <Link
                      to={{
                        pathname: '/projects/create',
                        query: { mode: CreateProjectModes.BitbucketServer, resetPat: 1 }
                      }}>
                      {translate('onboarding.create_project.update_your_token')}
                    </Link>
                  )
                }}
              />
            </Alert>
          )}

          {repositories.map(repo =>
            repo.sqProjectKey ? (
              <div
                className="display-flex-start spacer-right spacer-bottom create-project-import-bbs-repo"
                key={repo.id}>
                <CheckIcon className="spacer-right" fill={colors.green} size={14} />
                <div className="overflow-hidden">
                  <div className="little-spacer-bottom text-ellipsis">
                    <strong title={repo.name}>
                      <Link to={getProjectUrl(repo.sqProjectKey)}>{repo.name}</Link>
                    </strong>
                  </div>
                  <em>{translate('onboarding.create_project.repository_imported')}</em>
                </div>
              </div>
            ) : (
              <Radio
                checked={selectedRepository?.id === repo.id}
                className={classNames(
                  'display-flex-start spacer-right spacer-bottom create-project-import-bbs-repo overflow-hidden',
                  {
                    disabled: disableRepositories,
                    'text-muted': disableRepositories,
                    'link-no-underline': disableRepositories
                  }
                )}
                key={repo.id}
                onCheck={() => props.onSelectRepository(repo)}
                value={String(repo.id)}>
                <strong className="text-ellipsis" title={repo.name}>
                  {repo.name}
                </strong>
              </Radio>
            )
          )}

          {!showingAllRepositories && repositoryCount > 0 && (
            <Alert variant="warning">
              {translateWithParameters(
                'onboarding.create_project.only_showing_X_first_repos',
                repositoryCount
              )}
            </Alert>
          )}
        </div>
      )}
    </BoxedGroupAccordion>
  );
}
