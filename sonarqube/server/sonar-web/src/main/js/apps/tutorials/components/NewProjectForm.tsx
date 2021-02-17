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
import { DeleteButton, SubmitButton } from 'sonar-ui-common/components/controls/buttons';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { createProject, deleteProject } from '../../../api/components';

interface Props {
  onDelete: VoidFunction;
  onDone: (projectKey: string) => void;
  organization?: string;
  projectKey?: string;
}

interface State {
  done: boolean;
  loading: boolean;
  projectKey: string;
}

export default class NewProjectForm extends React.PureComponent<Props, State> {
  mounted = false;

  constructor(props: Props) {
    super(props);
    this.state = {
      done: props.projectKey != null,
      loading: false,
      projectKey: props.projectKey || ''
    };
  }

  componentDidMount() {
    this.mounted = true;
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  stopLoading = () => {
    if (this.mounted) {
      this.setState({ loading: false });
    }
  };

  sanitizeProjectKey = (projectKey: string) => projectKey.replace(/[^-_a-zA-Z0-9.:]/, '');

  handleProjectKeyChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.setState({ projectKey: this.sanitizeProjectKey(event.target.value) });
  };

  handleProjectCreate = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const { projectKey } = this.state;
    const data: {
      name: string;
      project: string;
      organization?: string;
    } = {
      name: projectKey,
      project: projectKey
    };
    if (this.props.organization) {
      data.organization = this.props.organization;
    }
    this.setState({ loading: true });
    createProject(data).then(() => {
      if (this.mounted) {
        this.setState({ done: true, loading: false });
        this.props.onDone(projectKey);
      }
    }, this.stopLoading);
  };

  handleProjectDelete = () => {
    const { projectKey } = this.state;
    this.setState({ loading: true });
    deleteProject(projectKey).then(() => {
      if (this.mounted) {
        this.setState({ done: false, loading: false, projectKey: '' });
        this.props.onDelete();
      }
    }, this.stopLoading);
  };

  render() {
    const { done, loading, projectKey } = this.state;

    const valid = projectKey.length > 0;

    const form = done ? (
      <div>
        <span className="spacer-right text-middle">{projectKey}</span>
        {loading ? (
          <i className="spinner text-middle" />
        ) : (
          <DeleteButton className="button-small text-middle" onClick={this.handleProjectDelete} />
        )}
      </div>
    ) : (
      <form onSubmit={this.handleProjectCreate}>
        <input
          autoFocus={true}
          className="input-large spacer-right text-middle"
          maxLength={400}
          minLength={1}
          onChange={this.handleProjectKeyChange}
          required={true}
          type="text"
          value={projectKey}
        />
        {loading ? (
          <i className="spinner text-middle" />
        ) : (
          <SubmitButton className="text-middle" disabled={!valid}>
            {translate('Done')}
          </SubmitButton>
        )}
        <div className="note spacer-top abs-width-300">
          {translate('onboarding.project_key_requirement')}
        </div>
      </form>
    );

    return (
      <div className="big-spacer-top">
        <h4 className="spacer-bottom">{translate('onboarding.language.project_key')}</h4>
        {form}
      </div>
    );
  }
}
