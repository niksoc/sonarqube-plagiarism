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
package org.sonar.server.qualityprofile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.NewBuiltInQualityProfile;
import org.sonar.api.utils.System2;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.qualityprofile.ActiveRuleDto;
import org.sonar.db.qualityprofile.ActiveRuleParamDto;
import org.sonar.db.qualityprofile.OrgActiveRuleDto;
import org.sonar.db.qualityprofile.QProfileDto;
import org.sonar.db.qualityprofile.RulesProfileDto;
import org.sonar.db.rule.RuleDefinitionDto;
import org.sonar.db.rule.RuleParamDto;
import org.sonar.server.qualityprofile.index.ActiveRuleIndexer;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.util.IntegerTypeValidation;
import org.sonar.server.util.StringTypeValidation;
import org.sonar.server.util.TypeValidations;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.sonar.api.rules.RulePriority.BLOCKER;
import static org.sonar.api.rules.RulePriority.CRITICAL;
import static org.sonar.api.rules.RulePriority.MAJOR;
import static org.sonar.api.rules.RulePriority.MINOR;
import static org.sonar.db.qualityprofile.QualityProfileTesting.newRuleProfileDto;
import static org.sonar.server.qualityprofile.ActiveRuleInheritance.INHERITED;

public class BuiltInQProfileUpdateImplTest {

  private static final long NOW = 1_000;
  private static final long PAST = NOW - 100;

  @Rule
  public BuiltInQProfileRepositoryRule builtInProfileRepository = new BuiltInQProfileRepositoryRule();
  @Rule
  public DbTester db = DbTester.create();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  private System2 system2 = new TestSystem2().setNow(NOW);
  private ActiveRuleIndexer activeRuleIndexer = mock(ActiveRuleIndexer.class);
  private TypeValidations typeValidations = new TypeValidations(asList(new StringTypeValidation(), new IntegerTypeValidation()));
  private RuleActivator ruleActivator = new RuleActivator(system2, db.getDbClient(), typeValidations, userSession);

  private BuiltInQProfileUpdateImpl underTest = new BuiltInQProfileUpdateImpl(db.getDbClient(), ruleActivator, activeRuleIndexer);

  private RulesProfileDto persistedProfile;

  @Before
  public void setUp() {
    persistedProfile = newRuleProfileDto(rp -> rp
      .setIsBuiltIn(true)
      .setLanguage("xoo")
      .setRulesUpdatedAt(null));
    db.getDbClient().qualityProfileDao().insert(db.getSession(), persistedProfile);
    db.commit();
  }

  @Test
  public void activate_new_rules() {
    RuleDefinitionDto rule1 = db.rules().insert(r -> r.setLanguage("xoo"));
    RuleDefinitionDto rule2 = db.rules().insert(r -> r.setLanguage("xoo"));
    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", "xoo");
    newQp.activateRule(rule1.getRepositoryKey(), rule1.getRuleKey()).overrideSeverity(Severity.CRITICAL);
    newQp.activateRule(rule2.getRepositoryKey(), rule2.getRuleKey()).overrideSeverity(Severity.MAJOR);
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile("xoo", "Sonar way"), rule1, rule2);

    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(2);
    assertThatRuleIsNewlyActivated(activeRules, rule1, CRITICAL);
    assertThatRuleIsNewlyActivated(activeRules, rule2, MAJOR);
    assertThatProfileIsMarkedAsUpdated(persistedProfile);
  }

  @Test
  public void already_activated_rule_is_updated_in_case_of_differences() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setLanguage("xoo"));
    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", "xoo");
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey()).overrideSeverity(Severity.CRITICAL);
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile("xoo", "Sonar way"), rule);

    activateRuleInDb(persistedProfile, rule, BLOCKER);

    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(1);
    assertThatRuleIsUpdated(activeRules, rule, CRITICAL);
    assertThatProfileIsMarkedAsUpdated(persistedProfile);
  }

  @Test
  public void already_activated_rule_is_not_touched_if_no_differences() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setLanguage("xoo"));
    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", "xoo");
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey()).overrideSeverity(Severity.CRITICAL);
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile("xoo", "Sonar way"), rule);

    activateRuleInDb(persistedProfile, rule, CRITICAL);

    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(1);
    assertThatRuleIsUntouched(activeRules, rule, CRITICAL);
    assertThatProfileIsNotMarkedAsUpdated(persistedProfile);
  }

  @Test
  public void deactivate_rule_that_is_not_in_built_in_definition_anymore() {
    RuleDefinitionDto rule1 = db.rules().insert(r -> r.setLanguage("xoo"));
    RuleDefinitionDto rule2 = db.rules().insert(r -> r.setLanguage("xoo"));
    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", "xoo");
    newQp.activateRule(rule2.getRepositoryKey(), rule2.getRuleKey()).overrideSeverity(Severity.MAJOR);
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile("xoo", "Sonar way"), rule1, rule2);

    // built-in definition contains only rule2
    // so rule1 must be deactivated
    activateRuleInDb(persistedProfile, rule1, CRITICAL);

    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(1);
    assertThatRuleIsDeactivated(activeRules, rule1);
    assertThatProfileIsMarkedAsUpdated(persistedProfile);
  }

  @Test
  public void activate_deactivate_and_update_three_rules_at_the_same_time() {
    RuleDefinitionDto rule1 = db.rules().insert(r -> r.setLanguage("xoo"));
    RuleDefinitionDto rule2 = db.rules().insert(r -> r.setLanguage("xoo"));
    RuleDefinitionDto rule3 = db.rules().insert(r -> r.setLanguage("xoo"));

    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", "xoo");
    newQp.activateRule(rule1.getRepositoryKey(), rule1.getRuleKey()).overrideSeverity(Severity.CRITICAL);
    newQp.activateRule(rule2.getRepositoryKey(), rule2.getRuleKey()).overrideSeverity(Severity.MAJOR);
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile("xoo", "Sonar way"), rule1, rule2);

    // rule1 must be updated (blocker to critical)
    // rule2 must be activated
    // rule3 must be deactivated
    activateRuleInDb(persistedProfile, rule1, BLOCKER);
    activateRuleInDb(persistedProfile, rule3, BLOCKER);

    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(2);
    assertThatRuleIsUpdated(activeRules, rule1, CRITICAL);
    assertThatRuleIsNewlyActivated(activeRules, rule2, MAJOR);
    assertThatRuleIsDeactivated(activeRules, rule3);
    assertThatProfileIsMarkedAsUpdated(persistedProfile);
  }

  // SONAR-10473
  @Test
  public void activate_rule_on_built_in_profile_resets_severity_to_default_if_not_overridden() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setSeverity(Severity.MAJOR).setLanguage("xoo"));

    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", "xoo");
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey());
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile("xoo", "Sonar way"), rule);
    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThatRuleIsNewlyActivated(activeRules, rule, MAJOR);

    // emulate an upgrade of analyzer that changes the default severity of the rule
    rule.setSeverity(Severity.MINOR);
    db.rules().update(rule);

    underTest.update(db.getSession(), builtIn, persistedProfile);
    activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThatRuleIsNewlyActivated(activeRules, rule, MINOR);
  }

  @Test
  public void activate_rule_on_built_in_profile_resets_params_to_default_if_not_overridden() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setLanguage("xoo"));
    RuleParamDto ruleParam = db.rules().insertRuleParam(rule, p -> p.setName("min").setDefaultValue("10"));

    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile("Sonar way", rule.getLanguage());
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey());
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile(newQp.language(), newQp.name()), rule);
    underTest.update(db.getSession(), builtIn, persistedProfile);

    List<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(1);
    assertThatRuleHasParams(db, activeRules.get(0), tuple("min", "10"));

    // emulate an upgrade of analyzer that changes the default value of parameter min
    ruleParam.setDefaultValue("20");
    db.getDbClient().ruleDao().updateRuleParam(db.getSession(), rule, ruleParam);

    underTest.update(db.getSession(), builtIn, persistedProfile);
    activeRules = db.getDbClient().activeRuleDao().selectByRuleProfile(db.getSession(), persistedProfile);
    assertThat(activeRules).hasSize(1);
    assertThatRuleHasParams(db, activeRules.get(0), tuple("min", "20"));
  }

  @Test
  public void propagate_activation_to_descendant_profiles() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setLanguage("xoo"));

    QProfileDto profile = db.qualityProfiles().insert(db.getDefaultOrganization(),
      p -> p.setLanguage(rule.getLanguage()).setIsBuiltIn(true));
    QProfileDto childProfile = createChildProfile(profile);
    QProfileDto grandchildProfile = createChildProfile(childProfile);

    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile(profile.getName(), profile.getLanguage());
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey());
    newQp.done();
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile(profile.getLanguage(), profile.getName()), rule);
    List<ActiveRuleChange> changes = underTest.update(db.getSession(), builtIn, RulesProfileDto.from(profile));

    assertThat(changes).hasSize(3);
    assertThatRuleIsActivated(profile, rule, changes, rule.getSeverityString(), null, emptyMap());
    assertThatRuleIsActivated(childProfile, rule, changes, rule.getSeverityString(), INHERITED, emptyMap());
    assertThatRuleIsActivated(grandchildProfile, rule, changes, rule.getSeverityString(), INHERITED, emptyMap());
  }

  @Test
  public void do_not_load_descendants_if_no_changes() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setLanguage("xoo"));

    QProfileDto profile = db.qualityProfiles().insert(db.getDefaultOrganization(),
      p -> p.setLanguage(rule.getLanguage()).setIsBuiltIn(true));
    QProfileDto childProfile = createChildProfile(profile);

    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile(profile.getName(), profile.getLanguage());
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey());
    newQp.done();

    // first run
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile(profile.getLanguage(), profile.getName()), rule);
    List<ActiveRuleChange> changes = underTest.update(db.getSession(), builtIn, RulesProfileDto.from(profile));
    assertThat(changes).hasSize(2).extracting(ActiveRuleChange::getType).containsOnly(ActiveRuleChange.Type.ACTIVATED);

    // second run, without any input changes
    RuleActivator ruleActivatorWithoutDescendants = new RuleActivator(system2, db.getDbClient(), typeValidations, userSession) {
      @Override
      DescendantProfilesSupplier createDescendantProfilesSupplier(DbSession dbSession) {
        return (profiles, ruleIds) -> {
          throw new IllegalStateException("BOOM - descendants should not be loaded");
        };
      }
    };
    changes = new BuiltInQProfileUpdateImpl(db.getDbClient(), ruleActivatorWithoutDescendants, activeRuleIndexer).update(db.getSession(), builtIn, RulesProfileDto.from(profile));
    assertThat(changes).isEmpty();
  }

  @Test
  public void propagate_deactivation_to_descendant_profiles() {
    RuleDefinitionDto rule = db.rules().insert(r -> r.setLanguage("xoo"));

    QProfileDto profile = db.qualityProfiles().insert(db.getDefaultOrganization(),
      p -> p.setLanguage(rule.getLanguage()).setIsBuiltIn(true));
    QProfileDto childProfile = createChildProfile(profile);
    QProfileDto grandChildProfile = createChildProfile(childProfile);

    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile newQp = context.createBuiltInQualityProfile(profile.getName(), profile.getLanguage());
    newQp.activateRule(rule.getRepositoryKey(), rule.getRuleKey());
    newQp.done();

    // first run to activate the rule
    BuiltInQProfile builtIn = builtInProfileRepository.create(context.profile(profile.getLanguage(), profile.getName()), rule);
    List<ActiveRuleChange> changes = underTest.update(db.getSession(), builtIn, RulesProfileDto.from(profile));
    assertThat(changes).hasSize(3).extracting(ActiveRuleChange::getType).containsOnly(ActiveRuleChange.Type.ACTIVATED);

    // second run to deactivate the rule
    context = new BuiltInQualityProfilesDefinition.Context();
    NewBuiltInQualityProfile updatedQp = context.createBuiltInQualityProfile(profile.getName(), profile.getLanguage());
    updatedQp.done();
    builtIn = builtInProfileRepository.create(context.profile(profile.getLanguage(), profile.getName()), rule);
    changes = underTest.update(db.getSession(), builtIn, RulesProfileDto.from(profile));
    assertThat(changes).hasSize(3).extracting(ActiveRuleChange::getType).containsOnly(ActiveRuleChange.Type.DEACTIVATED);

    assertThatRuleIsDeactivated(profile, rule);
    assertThatRuleIsDeactivated(childProfile, rule);
    assertThatRuleIsDeactivated(grandChildProfile, rule);
  }

  private QProfileDto createChildProfile(QProfileDto parent) {
    return db.qualityProfiles().insert(db.getDefaultOrganization(), p -> p
      .setLanguage(parent.getLanguage())
      .setParentKee(parent.getKee())
      .setName("Child of " + parent.getName()))
      .setIsBuiltIn(false);
  }

  private void assertThatRuleIsActivated(QProfileDto profile, RuleDefinitionDto rule, @Nullable List<ActiveRuleChange> changes,
    String expectedSeverity, @Nullable ActiveRuleInheritance expectedInheritance, Map<String, String> expectedParams) {
    OrgActiveRuleDto activeRule = db.getDbClient().activeRuleDao().selectByProfile(db.getSession(), profile)
      .stream()
      .filter(ar -> ar.getRuleKey().equals(rule.getKey()))
      .findFirst()
      .orElseThrow(IllegalStateException::new);

    assertThat(activeRule.getSeverityString()).isEqualTo(expectedSeverity);
    assertThat(activeRule.getInheritance()).isEqualTo(expectedInheritance != null ? expectedInheritance.name() : null);
    assertThat(activeRule.getCreatedAt()).isNotNull();
    assertThat(activeRule.getUpdatedAt()).isNotNull();

    List<ActiveRuleParamDto> params = db.getDbClient().activeRuleDao().selectParamsByActiveRuleId(db.getSession(), activeRule.getId());
    assertThat(params).hasSize(expectedParams.size());

    if (changes != null) {
      ActiveRuleChange change = changes.stream()
        .filter(c -> c.getActiveRule().getId().equals(activeRule.getId()))
        .findFirst().orElseThrow(IllegalStateException::new);
      assertThat(change.getInheritance()).isEqualTo(expectedInheritance);
      assertThat(change.getSeverity()).isEqualTo(expectedSeverity);
      assertThat(change.getType()).isEqualTo(ActiveRuleChange.Type.ACTIVATED);
    }
  }

  private static void assertThatRuleHasParams(DbTester db, ActiveRuleDto activeRule, Tuple... expectedParams) {
    assertThat(db.getDbClient().activeRuleDao().selectParamsByActiveRuleId(db.getSession(), activeRule.getId()))
      .extracting(ActiveRuleParamDto::getKey, ActiveRuleParamDto::getValue)
      .containsExactlyInAnyOrder(expectedParams);
  }

  private static void assertThatRuleIsNewlyActivated(List<ActiveRuleDto> activeRules, RuleDefinitionDto rule, RulePriority severity) {
    ActiveRuleDto activeRule = findRule(activeRules, rule).get();

    assertThat(activeRule.getInheritance()).isNull();
    assertThat(activeRule.getSeverityString()).isEqualTo(severity.name());
    assertThat(activeRule.getCreatedAt()).isEqualTo(NOW);
    assertThat(activeRule.getUpdatedAt()).isEqualTo(NOW);
  }

  private static void assertThatRuleIsUpdated(List<ActiveRuleDto> activeRules, RuleDefinitionDto rule, RulePriority severity) {
    ActiveRuleDto activeRule = findRule(activeRules, rule).get();

    assertThat(activeRule.getInheritance()).isNull();
    assertThat(activeRule.getSeverityString()).isEqualTo(severity.name());
    assertThat(activeRule.getCreatedAt()).isEqualTo(PAST);
    assertThat(activeRule.getUpdatedAt()).isEqualTo(NOW);
  }

  private static void assertThatRuleIsUntouched(List<ActiveRuleDto> activeRules, RuleDefinitionDto rule, RulePriority severity) {
    ActiveRuleDto activeRule = findRule(activeRules, rule).get();

    assertThat(activeRule.getInheritance()).isNull();
    assertThat(activeRule.getSeverityString()).isEqualTo(severity.name());
    assertThat(activeRule.getCreatedAt()).isEqualTo(PAST);
    assertThat(activeRule.getUpdatedAt()).isEqualTo(PAST);
  }

  private void assertThatRuleIsDeactivated(QProfileDto profile, RuleDefinitionDto rule) {
    Collection<ActiveRuleDto> activeRules = db.getDbClient().activeRuleDao().selectByRulesAndRuleProfileUuids(db.getSession(), singletonList(rule.getId()), singletonList(profile.getRulesProfileUuid()));
    assertThat(activeRules).isEmpty();
  }

  private static void assertThatRuleIsDeactivated(List<ActiveRuleDto> activeRules, RuleDefinitionDto rule) {
    assertThat(findRule(activeRules, rule)).isEmpty();
  }

  private void assertThatProfileIsMarkedAsUpdated(RulesProfileDto dto) {
    RulesProfileDto reloaded = db.getDbClient().qualityProfileDao().selectBuiltInRuleProfiles(db.getSession())
      .stream()
      .filter(p -> p.getKee().equals(dto.getKee()))
      .findFirst()
      .get();
    assertThat(reloaded.getRulesUpdatedAt()).isNotEmpty();
  }

  private void assertThatProfileIsNotMarkedAsUpdated(RulesProfileDto dto) {
    RulesProfileDto reloaded = db.getDbClient().qualityProfileDao().selectBuiltInRuleProfiles(db.getSession())
      .stream()
      .filter(p -> p.getKee().equals(dto.getKee()))
      .findFirst()
      .get();
    assertThat(reloaded.getRulesUpdatedAt()).isNull();
  }

  private static Optional<ActiveRuleDto> findRule(List<ActiveRuleDto> activeRules, RuleDefinitionDto rule) {
    return activeRules.stream()
      .filter(ar -> ar.getRuleKey().equals(rule.getKey()))
      .findFirst();
  }

  private void activateRuleInDb(RulesProfileDto profile, RuleDefinitionDto rule, RulePriority severity) {
    ActiveRuleDto dto = new ActiveRuleDto()
      .setProfileId(profile.getId())
      .setSeverity(severity.name())
      .setRuleId(rule.getId())
      .setCreatedAt(PAST)
      .setUpdatedAt(PAST);
    db.getDbClient().activeRuleDao().insert(db.getSession(), dto);
    db.commit();
  }
}
