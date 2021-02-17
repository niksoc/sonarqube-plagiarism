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
package org.sonar.server.issue.ws;

import org.sonar.core.platform.Module;
import org.sonar.server.issue.AvatarResolverImpl;
import org.sonar.server.issue.IssueChangeWSSupport;
import org.sonar.server.issue.IssueFieldsSetter;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.issue.TextRangeResponseFormatter;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.issue.WebIssueStorage;
import org.sonar.server.issue.index.IssueQueryFactory;
import org.sonar.server.issue.workflow.FunctionExecutor;
import org.sonar.server.issue.workflow.IssueWorkflow;
import org.sonar.server.qualitygate.changeevent.QGChangeEventListenersImpl;

public class IssueWsModule extends Module {
  @Override
  protected void configureModule() {
    add(
      IssueUpdater.class,
      IssueFinder.class,
      TransitionService.class,
      WebIssueStorage.class,
      IssueFieldsSetter.class,
      FunctionExecutor.class,
      IssueWorkflow.class,
      IssueQueryFactory.class,
      IssuesWs.class,
      AvatarResolverImpl.class,
      IssueChangeWSSupport.class,
      SearchResponseLoader.class,
      TextRangeResponseFormatter.class,
      UserResponseFormatter.class,
      SearchResponseFormat.class,
      OperationResponseWriter.class,
      AddCommentAction.class,
      EditCommentAction.class,
      DeleteCommentAction.class,
      AssignAction.class,
      DoTransitionAction.class,
      SearchAction.class,
      SetSeverityAction.class,
      TagsAction.class,
      SetTagsAction.class,
      SetTypeAction.class,
      ComponentTagsAction.class,
      AuthorsAction.class,
      ChangelogAction.class,
      BulkChangeAction.class,
      QGChangeEventListenersImpl.class);
  }
}
