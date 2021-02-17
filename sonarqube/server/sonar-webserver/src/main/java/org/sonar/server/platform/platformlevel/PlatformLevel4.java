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
package org.sonar.server.platform.platformlevel;

import java.util.List;
import org.sonar.api.profiles.AnnotationProfileParser;
import org.sonar.api.profiles.XMLProfileParser;
import org.sonar.api.profiles.XMLProfileSerializer;
import org.sonar.api.resources.Languages;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.rules.AnnotationRuleParser;
import org.sonar.api.rules.XMLRuleParser;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;
import org.sonar.auth.github.GitHubModule;
import org.sonar.auth.gitlab.GitLabModule;
import org.sonar.auth.ldap.LdapModule;
import org.sonar.auth.saml.SamlModule;
import org.sonar.ce.task.projectanalysis.notification.ReportAnalysisFailureNotificationModule;
import org.sonar.ce.task.projectanalysis.taskprocessor.ReportTaskProcessor;
import org.sonar.core.component.DefaultResourceTypes;
import org.sonar.core.extension.CoreExtensionsInstaller;
import org.sonar.core.platform.ComponentContainer;
import org.sonar.core.platform.PlatformEditionProvider;
import org.sonar.server.almsettings.MultipleAlmFeatureProvider;
import org.sonar.server.authentication.AuthenticationModule;
import org.sonar.server.authentication.LogOAuthWarning;
import org.sonar.server.authentication.ws.AuthenticationWsModule;
import org.sonar.server.badge.ws.ProjectBadgesWsModule;
import org.sonar.server.batch.BatchWsModule;
import org.sonar.server.branch.BranchFeatureProxyImpl;
import org.sonar.server.branch.pr.ws.PullRequestWsModule;
import org.sonar.server.branch.ws.BranchWsModule;
import org.sonar.server.ce.CeModule;
import org.sonar.server.ce.ws.CeWsModule;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.ComponentService;
import org.sonar.server.component.ComponentUpdater;
import org.sonar.server.component.index.ComponentIndex;
import org.sonar.server.component.index.ComponentIndexDefinition;
import org.sonar.server.component.index.ComponentIndexer;
import org.sonar.server.component.ws.ComponentViewerJsonWriter;
import org.sonar.server.component.ws.ComponentsWsModule;
import org.sonar.server.duplication.ws.DuplicationsParser;
import org.sonar.server.duplication.ws.DuplicationsWs;
import org.sonar.server.duplication.ws.ShowResponseBuilder;
import org.sonar.server.email.ws.EmailsWsModule;
import org.sonar.server.es.IndexCreator;
import org.sonar.server.es.IndexDefinitions;
import org.sonar.server.es.ProjectIndexersImpl;
import org.sonar.server.es.RecoveryIndexer;
import org.sonar.server.es.metadata.EsDbCompatibilityImpl;
import org.sonar.server.es.metadata.MetadataIndexDefinition;
import org.sonar.server.es.metadata.MetadataIndexImpl;
import org.sonar.server.extension.CoreExtensionBootstraper;
import org.sonar.server.extension.CoreExtensionStopper;
import org.sonar.server.favorite.FavoriteModule;
import org.sonar.server.favorite.ws.FavoriteWsModule;
import org.sonar.server.health.NodeHealthModule;
import org.sonar.server.hotspot.ws.HotspotsWsModule;
import org.sonar.server.issue.AddTagsAction;
import org.sonar.server.issue.AssignAction;
import org.sonar.server.issue.CommentAction;
import org.sonar.server.issue.IssueChangePostProcessorImpl;
import org.sonar.server.issue.RemoveTagsAction;
import org.sonar.server.issue.SetSeverityAction;
import org.sonar.server.issue.SetTypeAction;
import org.sonar.server.issue.TransitionAction;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.index.IssueIteratorFactory;
import org.sonar.server.issue.notification.IssuesChangesNotificationModule;
import org.sonar.server.issue.notification.MyNewIssuesEmailTemplate;
import org.sonar.server.issue.notification.MyNewIssuesNotificationHandler;
import org.sonar.server.issue.notification.NewIssuesEmailTemplate;
import org.sonar.server.issue.notification.NewIssuesNotificationHandler;
import org.sonar.server.issue.ws.IssueWsModule;
import org.sonar.server.language.LanguageValidation;
import org.sonar.server.language.ws.LanguageWs;
import org.sonar.server.log.ServerLogging;
import org.sonar.server.measure.custom.ws.CustomMeasuresWsModule;
import org.sonar.server.measure.index.ProjectsEsModule;
import org.sonar.server.measure.live.LiveMeasureModule;
import org.sonar.server.measure.ws.MeasuresWsModule;
import org.sonar.server.metric.CoreCustomMetrics;
import org.sonar.server.metric.DefaultMetricFinder;
import org.sonar.server.metric.ws.MetricsWsModule;
import org.sonar.server.newcodeperiod.ws.NewCodePeriodsWsModule;
import org.sonar.server.notification.NotificationModule;
import org.sonar.server.notification.ws.NotificationWsModule;
import org.sonar.server.organization.BillingValidationsProxyImpl;
import org.sonar.server.organization.OrganizationUpdaterImpl;
import org.sonar.server.organization.OrganizationValidationImpl;
import org.sonar.server.organization.ws.OrganizationsWsModule;
import org.sonar.server.permission.DefaultTemplatesResolverImpl;
import org.sonar.server.permission.GroupPermissionChanger;
import org.sonar.server.permission.PermissionTemplateService;
import org.sonar.server.permission.PermissionUpdater;
import org.sonar.server.permission.UserPermissionChanger;
import org.sonar.server.permission.index.PermissionIndexer;
import org.sonar.server.permission.ws.PermissionsWsModule;
import org.sonar.server.platform.BackendCleanup;
import org.sonar.server.platform.ClusterVerification;
import org.sonar.server.platform.PersistentSettings;
import org.sonar.server.platform.SystemInfoWriterModule;
import org.sonar.server.platform.WebCoreExtensionsInstaller;
import org.sonar.server.platform.web.WebServiceFilter;
import org.sonar.server.platform.web.WebServiceReroutingFilter;
import org.sonar.server.platform.web.requestid.HttpRequestIdModule;
import org.sonar.server.platform.ws.ChangeLogLevelServiceModule;
import org.sonar.server.platform.ws.HealthCheckerModule;
import org.sonar.server.platform.ws.L10nWs;
import org.sonar.server.platform.ws.ServerWs;
import org.sonar.server.platform.ws.SystemWsModule;
import org.sonar.server.plugins.PluginDownloader;
import org.sonar.server.plugins.PluginUninstaller;
import org.sonar.server.plugins.ServerExtensionInstaller;
import org.sonar.server.plugins.ws.AvailableAction;
import org.sonar.server.plugins.ws.CancelAllAction;
import org.sonar.server.plugins.ws.DownloadAction;
import org.sonar.server.plugins.ws.InstallAction;
import org.sonar.server.plugins.ws.InstalledAction;
import org.sonar.server.plugins.ws.PendingAction;
import org.sonar.server.plugins.ws.PluginUpdateAggregator;
import org.sonar.server.plugins.ws.PluginsWs;
import org.sonar.server.plugins.ws.UninstallAction;
import org.sonar.server.plugins.ws.UpdatesAction;
import org.sonar.server.project.ws.ProjectsWsModule;
import org.sonar.server.projectanalysis.ws.ProjectAnalysisWsModule;
import org.sonar.server.projectlink.ws.ProjectLinksModule;
import org.sonar.server.projecttag.ws.ProjectTagsWsModule;
import org.sonar.server.property.InternalPropertiesImpl;
import org.sonar.server.qualitygate.ProjectsInWarningModule;
import org.sonar.server.qualitygate.QualityGateModule;
import org.sonar.server.qualitygate.notification.QGChangeNotificationHandler;
import org.sonar.server.qualitygate.ws.QualityGateWsModule;
import org.sonar.server.qualityprofile.BuiltInQPChangeNotificationHandler;
import org.sonar.server.qualityprofile.BuiltInQPChangeNotificationTemplate;
import org.sonar.server.qualityprofile.BuiltInQProfileDefinitionsBridge;
import org.sonar.server.qualityprofile.BuiltInQProfileRepositoryImpl;
import org.sonar.server.qualityprofile.QProfileBackuperImpl;
import org.sonar.server.qualityprofile.QProfileComparison;
import org.sonar.server.qualityprofile.QProfileCopier;
import org.sonar.server.qualityprofile.QProfileExporters;
import org.sonar.server.qualityprofile.QProfileFactoryImpl;
import org.sonar.server.qualityprofile.QProfileParser;
import org.sonar.server.qualityprofile.QProfileResetImpl;
import org.sonar.server.qualityprofile.QProfileRulesImpl;
import org.sonar.server.qualityprofile.QProfileTreeImpl;
import org.sonar.server.qualityprofile.RuleActivator;
import org.sonar.server.qualityprofile.index.ActiveRuleIndexer;
import org.sonar.server.qualityprofile.ws.QProfilesWsModule;
import org.sonar.server.root.ws.RootWsModule;
import org.sonar.server.rule.CommonRuleDefinitionsImpl;
import org.sonar.server.rule.RuleCreator;
import org.sonar.server.rule.RuleDefinitionsLoader;
import org.sonar.server.rule.RuleUpdater;
import org.sonar.server.rule.WebServerRuleFinderImpl;
import org.sonar.server.rule.index.RuleIndexDefinition;
import org.sonar.server.rule.index.RuleIndexer;
import org.sonar.server.rule.ws.ActiveRuleCompleter;
import org.sonar.server.rule.ws.RepositoriesAction;
import org.sonar.server.rule.ws.RuleMapper;
import org.sonar.server.rule.ws.RuleQueryFactory;
import org.sonar.server.rule.ws.RuleWsSupport;
import org.sonar.server.rule.ws.RulesWs;
import org.sonar.server.rule.ws.TagsAction;
import org.sonar.server.setting.ProjectConfigurationLoaderImpl;
import org.sonar.server.setting.SettingsChangeNotifier;
import org.sonar.server.setting.ws.SettingsWsModule;
import org.sonar.server.source.ws.SourceWsModule;
import org.sonar.server.startup.LogServerId;
import org.sonar.server.telemetry.TelemetryClient;
import org.sonar.server.telemetry.TelemetryDaemon;
import org.sonar.server.telemetry.TelemetryDataJsonWriter;
import org.sonar.server.telemetry.TelemetryDataLoaderImpl;
import org.sonar.server.text.MacroInterpreter;
import org.sonar.server.ui.DeprecatedViews;
import org.sonar.server.ui.PageDecorations;
import org.sonar.server.ui.PageRepository;
import org.sonar.server.ui.WebAnalyticsLoaderImpl;
import org.sonar.server.ui.ws.NavigationWsModule;
import org.sonar.server.updatecenter.UpdateCenterModule;
import org.sonar.server.updatecenter.ws.UpdateCenterWsModule;
import org.sonar.server.user.NewUserNotifier;
import org.sonar.server.user.SecurityRealmFactory;
import org.sonar.server.user.UserSessionFactoryImpl;
import org.sonar.server.user.UserUpdater;
import org.sonar.server.user.index.UserIndex;
import org.sonar.server.user.index.UserIndexDefinition;
import org.sonar.server.user.index.UserIndexer;
import org.sonar.server.user.ws.UsersWsModule;
import org.sonar.server.usergroups.DefaultGroupCreatorImpl;
import org.sonar.server.usergroups.DefaultGroupFinder;
import org.sonar.server.usergroups.ws.UserGroupsModule;
import org.sonar.server.usertoken.UserTokenModule;
import org.sonar.server.usertoken.ws.UserTokenWsModule;
import org.sonar.server.util.TypeValidationModule;
import org.sonar.server.view.index.ViewIndex;
import org.sonar.server.view.index.ViewIndexDefinition;
import org.sonar.server.view.index.ViewIndexer;
import org.sonar.server.webhook.WebhookModule;
import org.sonar.server.webhook.WebhookQGChangeEventListener;
import org.sonar.server.webhook.ws.WebhooksWsModule;
import org.sonar.server.ws.WebServiceEngine;
import org.sonar.server.ws.ws.WebServicesWsModule;

import static org.sonar.core.extension.CoreExtensionsInstaller.noAdditionalSideFilter;
import static org.sonar.core.extension.PlatformLevelPredicates.hasPlatformLevel4OrNone;

public class PlatformLevel4 extends PlatformLevel {

  private final List<Object> level4AddedComponents;

  public PlatformLevel4(PlatformLevel parent, List<Object> level4AddedComponents) {
    super("level4", parent);
    this.level4AddedComponents = level4AddedComponents;
  }

  @Override
  protected void configureLevel() {
    addIfStartupLeader(
      IndexCreator.class,
      MetadataIndexDefinition.class,
      MetadataIndexImpl.class,
      EsDbCompatibilityImpl.class);

    addIfCluster(NodeHealthModule.class);

    add(
      ClusterVerification.class,
      LogServerId.class,
      LogOAuthWarning.class,
      PluginDownloader.class,
      PluginUninstaller.class,
      DeprecatedViews.class,
      PageRepository.class,
      ResourceTypes.class,
      DefaultResourceTypes.get(),
      SettingsChangeNotifier.class,
      PageDecorations.class,
      ServerWs.class,
      BackendCleanup.class,
      IndexDefinitions.class,
      WebAnalyticsLoaderImpl.class,

      // batch
      BatchWsModule.class,

      // update center
      UpdateCenterModule.class,
      UpdateCenterWsModule.class,

      // organizations
      OrganizationValidationImpl.class,
      OrganizationUpdaterImpl.class,
      OrganizationsWsModule.class,
      BillingValidationsProxyImpl.class,

      // quality profile
      BuiltInQProfileDefinitionsBridge.class,
      BuiltInQProfileRepositoryImpl.class,
      ActiveRuleIndexer.class,
      XMLProfileParser.class,
      XMLProfileSerializer.class,
      AnnotationProfileParser.class,
      QProfileComparison.class,
      QProfileTreeImpl.class,
      QProfileRulesImpl.class,
      RuleActivator.class,
      QProfileExporters.class,
      QProfileFactoryImpl.class,
      QProfileCopier.class,
      QProfileBackuperImpl.class,
      QProfileParser.class,
      QProfileResetImpl.class,
      QProfilesWsModule.class,

      // rule
      RuleIndexDefinition.class,
      RuleIndexer.class,
      AnnotationRuleParser.class,
      XMLRuleParser.class,
      WebServerRuleFinderImpl.class,
      RuleDefinitionsLoader.class,
      CommonRuleDefinitionsImpl.class,
      RulesDefinitionXmlLoader.class,
      RuleUpdater.class,
      RuleCreator.class,
      org.sonar.server.rule.ws.UpdateAction.class,
      RulesWs.class,
      RuleWsSupport.class,
      org.sonar.server.rule.ws.SearchAction.class,
      org.sonar.server.rule.ws.ShowAction.class,
      org.sonar.server.rule.ws.CreateAction.class,
      org.sonar.server.rule.ws.DeleteAction.class,
      org.sonar.server.rule.ws.ListAction.class,
      TagsAction.class,
      RuleMapper.class,
      ActiveRuleCompleter.class,
      RepositoriesAction.class,
      RuleQueryFactory.class,
      org.sonar.server.rule.ws.AppAction.class,

      // languages
      Languages.class,
      LanguageWs.class,
      LanguageValidation.class,
      org.sonar.server.language.ws.ListAction.class,

      // measure
      MetricsWsModule.class,
      MeasuresWsModule.class,
      CustomMeasuresWsModule.class,
      CoreCustomMetrics.class,
      DefaultMetricFinder.class,

      QualityGateModule.class,
      ProjectsInWarningModule.class,
      QualityGateWsModule.class,

      // web services
      WebServiceEngine.class,
      WebServicesWsModule.class,
      WebServiceFilter.class,
      WebServiceReroutingFilter.class,

      // localization
      L10nWs.class,
      org.sonar.server.platform.ws.IndexAction.class,

      // authentication
      AuthenticationModule.class,
      AuthenticationWsModule.class,
      GitHubModule.class,
      GitLabModule.class,
      LdapModule.class,
      SamlModule.class,

      // users
      UserSessionFactoryImpl.class,
      SecurityRealmFactory.class,
      NewUserNotifier.class,
      UserIndexDefinition.class,
      UserIndexer.class,
      UserIndex.class,
      UserUpdater.class,
      UsersWsModule.class,
      UserTokenModule.class,
      UserTokenWsModule.class,

      // groups
      UserGroupsModule.class,
      DefaultGroupCreatorImpl.class,
      DefaultGroupFinder.class,

      // permissions
      DefaultTemplatesResolverImpl.class,
      PermissionsWsModule.class,
      PermissionTemplateService.class,
      PermissionUpdater.class,
      UserPermissionChanger.class,
      GroupPermissionChanger.class,

      // components
      BranchWsModule.class,
      PullRequestWsModule.class,
      ProjectsWsModule.class,
      ProjectsEsModule.class,
      ProjectTagsWsModule.class,
      ComponentsWsModule.class,
      ComponentService.class,
      ComponentUpdater.class,
      ComponentFinder.class,
      QGChangeNotificationHandler.class,
      QGChangeNotificationHandler.newMetadata(),
      ComponentCleanerService.class,
      ComponentIndexDefinition.class,
      ComponentIndex.class,
      ComponentIndexer.class,
      LiveMeasureModule.class,
      ComponentViewerJsonWriter.class,

      FavoriteModule.class,
      FavoriteWsModule.class,

      // views
      ViewIndexDefinition.class,
      ViewIndexer.class,
      ViewIndex.class,

      // issues
      IssueIndexDefinition.class,
      IssueIndexer.class,
      IssueIteratorFactory.class,
      PermissionIndexer.class,
      IssueWsModule.class,
      NewIssuesEmailTemplate.class,
      MyNewIssuesEmailTemplate.class,
      IssuesChangesNotificationModule.class,
      NewIssuesNotificationHandler.class,
      NewIssuesNotificationHandler.newMetadata(),
      MyNewIssuesNotificationHandler.class,
      MyNewIssuesNotificationHandler.newMetadata(),

      // issues actions
      AssignAction.class,
      SetTypeAction.class,
      SetSeverityAction.class,
      CommentAction.class,
      TransitionAction.class,
      AddTagsAction.class,
      RemoveTagsAction.class,
      IssueChangePostProcessorImpl.class,

      // hotspots
      HotspotsWsModule.class,

      // source
      SourceWsModule.class,

      // Duplications
      DuplicationsParser.class,
      DuplicationsWs.class,
      ShowResponseBuilder.class,
      org.sonar.server.duplication.ws.ShowAction.class,

      // text
      MacroInterpreter.class,

      // Notifications
      // Those class are required in order to be able to send emails during startup
      // Without having two NotificationModule (one in StartupLevel and one in Level4)
      BuiltInQPChangeNotificationTemplate.class,
      BuiltInQPChangeNotificationHandler.class,

      NotificationModule.class,
      NotificationWsModule.class,
      EmailsWsModule.class,

      // Settings
      ProjectConfigurationLoaderImpl.class,
      PersistentSettings.class,
      SettingsWsModule.class,

      TypeValidationModule.class,

      // New Code Periods
      NewCodePeriodsWsModule.class,

      // Project Links
      ProjectLinksModule.class,

      // Project Analyses
      ProjectAnalysisWsModule.class,

      // System
      ServerLogging.class,
      ChangeLogLevelServiceModule.class,
      HealthCheckerModule.class,
      SystemWsModule.class,

      // Plugins WS
      PluginUpdateAggregator.class,
      InstalledAction.class,
      AvailableAction.class,
      DownloadAction.class,
      UpdatesAction.class,
      PendingAction.class,
      InstallAction.class,
      org.sonar.server.plugins.ws.UpdateAction.class,
      UninstallAction.class,
      CancelAllAction.class,
      PluginsWs.class,

      // Branch
      BranchFeatureProxyImpl.class,

      // Project badges
      ProjectBadgesWsModule.class,

      // Core Extensions
      CoreExtensionBootstraper.class,
      CoreExtensionStopper.class,

      MultipleAlmFeatureProvider.class,

      // Compute engine (must be after Views and Developer Cockpit)
      ReportAnalysisFailureNotificationModule.class,
      CeModule.class,
      CeWsModule.class,
      ReportTaskProcessor.class,

      // SonarSource editions
      PlatformEditionProvider.class,

      InternalPropertiesImpl.class,

      // UI
      NavigationWsModule.class,

      // root
      RootWsModule.class,

      // webhooks
      WebhookQGChangeEventListener.class,
      WebhookModule.class,
      WebhooksWsModule.class,

      // Http Request ID
      HttpRequestIdModule.class,

      RecoveryIndexer.class,
      ProjectIndexersImpl.class,

      // telemetry
      TelemetryDataLoaderImpl.class,
      TelemetryDataJsonWriter.class,
      TelemetryDaemon.class,
      TelemetryClient.class

    );

    // system info
    add(SystemInfoWriterModule.class);

    addAll(level4AddedComponents);
  }

  @Override
  public PlatformLevel start() {
    ComponentContainer container = getContainer();
    CoreExtensionsInstaller coreExtensionsInstaller = get(WebCoreExtensionsInstaller.class);
    coreExtensionsInstaller.install(container, hasPlatformLevel4OrNone(), noAdditionalSideFilter());
    ServerExtensionInstaller extensionInstaller = get(ServerExtensionInstaller.class);
    extensionInstaller.installExtensions(container);

    super.start();

    return this;
  }
}
