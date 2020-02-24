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
package org.sonar.server.authentication;

import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.impl.utils.AlwaysIncreasingSystem2;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.System2;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.authentication.UserRegistration.ExistingEmailStrategy;
import org.sonar.server.authentication.event.AuthenticationEvent;
import org.sonar.server.authentication.event.AuthenticationEvent.Source;
import org.sonar.server.authentication.exception.EmailAlreadyExistsRedirectionException;
import org.sonar.server.es.EsTester;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonar.server.organization.MemberUpdater;
import org.sonar.server.organization.OrganizationUpdater;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.organization.TestOrganizationFlags;
import org.sonar.server.user.NewUserNotifier;
import org.sonar.server.user.UserUpdater;
import org.sonar.server.user.index.UserIndexer;
import org.sonar.server.usergroups.DefaultGroupFinder;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonar.db.user.UserTesting.newUserDto;
import static org.sonar.process.ProcessProperties.Property.ONBOARDING_TUTORIAL_SHOW_TO_NEW_USERS;
import static org.sonar.server.authentication.UserRegistration.ExistingEmailStrategy.FORBID;
import static org.sonar.server.authentication.event.AuthenticationEvent.Method.BASIC;
import static org.sonar.server.authentication.event.AuthenticationExceptionMatcher.authenticationException;

public class UserRegistrarImplTest {

  private System2 system2 = new AlwaysIncreasingSystem2();

  private static String USER_LOGIN = "johndoo";

  private static UserIdentity USER_IDENTITY = UserIdentity.builder()
    .setProviderId("ABCD")
    .setProviderLogin("johndoo")
    .setName("John")
    .setEmail("john@email.com")
    .build();

  private static TestIdentityProvider IDENTITY_PROVIDER = new TestIdentityProvider()
    .setKey("github")
    .setName("name of github")
    .setEnabled(true)
    .setAllowsUsersToSignUp(true);

  private MapSettings settings = new MapSettings();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(new AlwaysIncreasingSystem2());
  @Rule
  public EsTester es = EsTester.create();
  private UserIndexer userIndexer = new UserIndexer(db.getDbClient(), es.client());
  private DefaultOrganizationProvider defaultOrganizationProvider = TestDefaultOrganizationProvider.from(db);
  private OrganizationUpdater organizationUpdater = mock(OrganizationUpdater.class);
  private TestOrganizationFlags organizationFlags = TestOrganizationFlags.standalone();
  private CredentialsLocalAuthentication localAuthentication = new CredentialsLocalAuthentication(db.getDbClient());
  private UserUpdater userUpdater = new UserUpdater(
    system2,
    mock(NewUserNotifier.class),
    db.getDbClient(),
    userIndexer,
    organizationFlags,
    defaultOrganizationProvider,
    new DefaultGroupFinder(db.getDbClient()),
    settings.asConfig(),
    localAuthentication);

  private DefaultGroupFinder defaultGroupFinder = new DefaultGroupFinder(db.getDbClient());

  private UserRegistrarImpl underTest = new UserRegistrarImpl(db.getDbClient(), userUpdater, defaultOrganizationProvider, organizationFlags,
    defaultGroupFinder, new MemberUpdater(db.getDbClient(), defaultGroupFinder, userIndexer));

  @Test
  public void authenticate_new_user() {
    organizationFlags.setEnabled(true);

    UserDto createdUser = underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.realm(BASIC, IDENTITY_PROVIDER.getName()))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    UserDto user = db.users().selectUserByLogin(createdUser.getLogin()).get();
    assertThat(user).isNotNull();
    assertThat(user.isActive()).isTrue();
    assertThat(user.getName()).isEqualTo("John");
    assertThat(user.getEmail()).isEqualTo("john@email.com");
    assertThat(user.getExternalLogin()).isEqualTo("johndoo");
    assertThat(user.getExternalIdentityProvider()).isEqualTo("github");
    assertThat(user.getExternalId()).isEqualTo("ABCD");
    assertThat(user.isRoot()).isFalse();
    checkGroupMembership(user);
  }

  @Test
  public void authenticate_new_user_with_sq_identity() {
    organizationFlags.setEnabled(false);
    GroupDto defaultGroup = insertDefaultGroup();

    TestIdentityProvider sqIdentityProvider = new TestIdentityProvider()
      .setKey("sonarqube")
      .setName("sonarqube identity name")
      .setEnabled(true)
      .setAllowsUsersToSignUp(true);

    UserDto createdUser = underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(sqIdentityProvider)
      .setSource(Source.realm(BASIC, sqIdentityProvider.getName()))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    UserDto user = db.users().selectUserByLogin(createdUser.getLogin()).get();
    assertThat(user).isNotNull();
    assertThat(user.isActive()).isTrue();
    assertThat(user.getLogin()).isEqualTo("johndoo");
    assertThat(user.getName()).isEqualTo("John");
    assertThat(user.getEmail()).isEqualTo("john@email.com");
    assertThat(user.getExternalLogin()).isEqualTo("johndoo");
    assertThat(user.getExternalIdentityProvider()).isEqualTo("sonarqube");
    assertThat(user.getExternalId()).isEqualTo("ABCD");
    assertThat(user.isLocal()).isFalse();
    assertThat(user.isRoot()).isFalse();
    checkGroupMembership(user, defaultGroup);
  }

  @Test
  public void authenticate_new_user_generate_login_when_no_login_provided() {
    organizationFlags.setEnabled(true);

    underTest.register(UserRegistration.builder()
      .setUserIdentity(UserIdentity.builder()
        .setProviderId("ABCD")
        .setProviderLogin("johndoo")
        .setName("John Doe")
        .setEmail("john@email.com")
        .build())
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.realm(BASIC, IDENTITY_PROVIDER.getName()))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    UserDto user = db.getDbClient().userDao().selectByEmail(db.getSession(), "john@email.com").get(0);
    assertThat(user).isNotNull();
    assertThat(user.isActive()).isTrue();
    assertThat(user.getLogin()).isNotEqualTo("John Doe").startsWith("john-doe");
    assertThat(user.getEmail()).isEqualTo("john@email.com");
    assertThat(user.getExternalLogin()).isEqualTo("johndoo");
    assertThat(user.getExternalIdentityProvider()).isEqualTo("github");
    assertThat(user.getExternalId()).isEqualTo("ABCD");
  }

  @Test
  public void authenticate_new_user_with_groups() {
    organizationFlags.setEnabled(true);
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto group2 = db.users().insertGroup(db.getDefaultOrganization(), "group2");

    UserDto loggedInUser = authenticate(USER_LOGIN, "group1", "group2", "group3");

    Optional<UserDto> user = db.users().selectUserByLogin(loggedInUser.getLogin());
    checkGroupMembership(user.get(), group1, group2);
  }

  @Test
  public void authenticate_new_user_and_force_default_group_when_organizations_are_disabled() {
    organizationFlags.setEnabled(false);
    UserDto user = db.users().insertUser();
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto defaultGroup = insertDefaultGroup();
    db.users().insertMember(group1, user);
    db.users().insertMember(defaultGroup, user);

    authenticate(user.getLogin(), "group1");

    checkGroupMembership(user, group1, defaultGroup);
  }

  @Test
  public void does_not_force_default_group_when_authenticating_new_user_if_organizations_are_enabled() {
    organizationFlags.setEnabled(true);
    UserDto user = db.users().insertUser(newUserDto()
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey())
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
    );
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto defaultGroup = insertDefaultGroup();
    db.users().insertMember(group1, user);
    db.users().insertMember(defaultGroup, user);

    authenticate(user.getExternalLogin(), "group1");

    checkGroupMembership(user, group1);
  }

  @Test
  public void authenticate_new_user_sets_onboarded_flag_to_false_when_onboarding_setting_is_set_to_true() {
    organizationFlags.setEnabled(true);
    settings.setProperty(ONBOARDING_TUTORIAL_SHOW_TO_NEW_USERS.getKey(), true);

    UserDto user = underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.users().selectUserByLogin(user.getLogin()).get().isOnboarded()).isFalse();
  }

  @Test
  public void authenticate_new_user_sets_onboarded_flag_to_true_when_onboarding_setting_is_set_to_false() {
    organizationFlags.setEnabled(true);
    settings.setProperty(ONBOARDING_TUTORIAL_SHOW_TO_NEW_USERS.getKey(), false);

    UserDto user = underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.users().selectUserByLogin(user.getLogin()).get().isOnboarded()).isTrue();
  }

  @Test
  public void external_id_is_set_to_provider_login_when_null() {
    organizationFlags.setEnabled(true);
    UserIdentity newUser = UserIdentity.builder()
      .setProviderId(null)
      .setProviderLogin("johndoo")
      .setName("JOhn")
      .build();

    UserDto user = underTest.register(UserRegistration.builder()
      .setUserIdentity(newUser)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.users().selectUserByLogin(user.getLogin()).get())
      .extracting(UserDto::getLogin, UserDto::getExternalId, UserDto::getExternalLogin)
      .contains(user.getLogin(), "johndoo", "johndoo");
  }

  @Test
  public void authenticate_new_user_update_existing_user_email_when_strategy_is_ALLOW() {
    organizationFlags.setEnabled(true);
    UserDto existingUser = db.users().insertUser(u -> u.setEmail("john@email.com"));
    UserIdentity newUser = UserIdentity.builder()
      .setProviderLogin("johndoo")
      .setName(existingUser.getName())
      .setEmail(existingUser.getEmail())
      .build();

    UserDto user = underTest.register(UserRegistration.builder()
      .setUserIdentity(newUser)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.ALLOW)
      .build());

    UserDto newUserReloaded = db.users().selectUserByLogin(user.getLogin()).get();
    assertThat(newUserReloaded.getEmail()).isEqualTo(existingUser.getEmail());
    UserDto existingUserReloaded = db.users().selectUserByLogin(existingUser.getLogin()).get();
    assertThat(existingUserReloaded.getEmail()).isNull();
  }

  @Test
  public void throw_EmailAlreadyExistException_when_authenticating_new_user_when_email_already_exists_and_strategy_is_WARN() {
    organizationFlags.setEnabled(true);
    UserDto existingUser = db.users().insertUser(u -> u.setEmail("john@email.com"));
    UserIdentity newUser = UserIdentity.builder()
      .setProviderLogin("johndoo")
      .setName(existingUser.getName())
      .setEmail(existingUser.getEmail())
      .build();

    expectedException.expect(EmailAlreadyExistsRedirectionException.class);

    underTest.register(UserRegistration.builder()
      .setUserIdentity(newUser)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.WARN)
      .build());
  }

  @Test
  public void throw_AuthenticationException_when_authenticating_new_user_when_email_already_exists_and_strategy_is_FORBID() {
    db.users().insertUser(u -> u.setEmail("john@email.com"));
    Source source = Source.realm(AuthenticationEvent.Method.FORM, IDENTITY_PROVIDER.getName());

    expectedException.expect(authenticationException().from(source)
      .withLogin(USER_IDENTITY.getProviderLogin())
      .andPublicMessage("You can't sign up because email 'john@email.com' is already used by an existing user. " +
        "This means that you probably already registered with another account."));
    expectedException.expectMessage("Email 'john@email.com' is already used");

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(source)
      .setExistingEmailStrategy(FORBID)
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());
  }

  @Test
  public void throw_AuthenticationException_when_authenticating_new_user_and_email_already_exists_multiple_times() {
    db.users().insertUser(u -> u.setEmail("john@email.com"));
    db.users().insertUser(u -> u.setEmail("john@email.com"));
    Source source = Source.realm(AuthenticationEvent.Method.FORM, IDENTITY_PROVIDER.getName());

    expectedException.expect(authenticationException().from(source)
      .withLogin(USER_IDENTITY.getProviderLogin())
      .andPublicMessage("You can't sign up because email 'john@email.com' is already used by an existing user. " +
        "This means that you probably already registered with another account."));
    expectedException.expectMessage("Email 'john@email.com' is already used");

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(source)
      .setExistingEmailStrategy(FORBID)
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());
  }

  @Test
  public void fail_to_authenticate_new_user_when_allow_users_to_signup_is_false() {
    TestIdentityProvider identityProvider = new TestIdentityProvider()
      .setKey("github")
      .setName("Github")
      .setEnabled(true)
      .setAllowsUsersToSignUp(false);
    Source source = Source.realm(AuthenticationEvent.Method.FORM, identityProvider.getName());

    expectedException.expect(authenticationException().from(source).withLogin(USER_IDENTITY.getProviderLogin()).andPublicMessage("'github' users are not allowed to sign up"));
    expectedException.expectMessage("User signup disabled for provider 'github'");

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(identityProvider)
      .setSource(source)
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());
  }

  @Test
  public void authenticate_and_update_existing_user_matching_login() {
    insertDefaultGroup();
    db.users().insertUser(u -> u
      .setName("Old name")
      .setEmail("Old email")
      .setExternalId("old id")
      .setExternalLogin("old identity")
      .setExternalIdentityProvider("old provide"));

    UserDto user = underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.users().selectUserByLogin(user.getLogin()).get())
      .extracting(UserDto::getName, UserDto::getEmail, UserDto::getExternalId, UserDto::getExternalLogin, UserDto::getExternalIdentityProvider, UserDto::isActive)
      .contains("John", "john@email.com", "ABCD", "johndoo", "github", true);
  }

  @Test
  public void authenticate_and_update_existing_user_matching_external_id() {
    UserDto user = db.users().insertUser(u -> u
      .setLogin("Old login")
      .setName("Old name")
      .setEmail("Old email")
      .setExternalId(USER_IDENTITY.getProviderId())
      .setExternalLogin("old identity")
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.getDbClient().userDao().selectByUuid(db.getSession(), user.getUuid()))
      .extracting(UserDto::getLogin, UserDto::getName, UserDto::getEmail, UserDto::getExternalId, UserDto::getExternalLogin, UserDto::getExternalIdentityProvider,
        UserDto::isActive)
      .contains(USER_LOGIN, "John", "john@email.com", "ABCD", "johndoo", "github", true);
  }

  @Test
  public void authenticate_and_update_existing_user_matching_external_login() {
    UserDto user = db.users().insertUser(u -> u
      .setLogin("Old login")
      .setName("Old name")
      .setEmail(USER_IDENTITY.getEmail())
      .setExternalId("Old id")
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.getDbClient().userDao().selectByUuid(db.getSession(), user.getUuid()))
      .extracting(UserDto::getName, UserDto::getEmail, UserDto::getExternalId, UserDto::getExternalLogin, UserDto::getExternalIdentityProvider,
        UserDto::isActive)
      .contains("John", "john@email.com", "ABCD", "johndoo", "github", true);
  }

  @Test
  public void authenticate_existing_user_and_login_should_not_be_updated() {
    UserDto user = db.users().insertUser(u -> u
      .setLogin("old login")
      .setName(USER_IDENTITY.getName())
      .setEmail(USER_IDENTITY.getEmail())
      .setExternalId(USER_IDENTITY.getProviderId())
      .setExternalLogin("old identity")
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    //no new user should be created
    assertThat(db.countRowsOfTable(db.getSession(), "users")).isEqualTo(1);
    assertThat(db.getDbClient().userDao().selectByUuid(db.getSession(), user.getUuid()))
      .extracting(UserDto::getLogin, UserDto::getName, UserDto::getEmail, UserDto::getExternalId, UserDto::getExternalLogin, UserDto::getExternalIdentityProvider,
        UserDto::isActive)
      .containsExactlyInAnyOrder("old login", USER_IDENTITY.getName(), USER_IDENTITY.getEmail(), USER_IDENTITY.getProviderId(), USER_IDENTITY.getProviderLogin(),
        IDENTITY_PROVIDER.getKey(),
        true);
  }

  @Test
  public void authenticate_existing_user_matching_external_login_when_external_id_is_null() {
    UserDto user = db.users().insertUser(u -> u
      .setName("Old name")
      .setEmail("Old email")
      .setExternalId("Old id")
      .setExternalLogin("johndoo")
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(UserIdentity.builder()
        .setProviderId(null)
        .setProviderLogin("johndoo")
        .setName("John")
        .setEmail("john@email.com")
        .build())
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.getDbClient().userDao().selectByUuid(db.getSession(), user.getUuid()))
      .extracting(UserDto::getLogin, UserDto::getName, UserDto::getEmail, UserDto::getExternalId, UserDto::getExternalLogin, UserDto::getExternalIdentityProvider,
        UserDto::isActive)
      .contains(user.getLogin(), "John", "john@email.com", "johndoo", "johndoo", "github", true);
  }

  @Test
  public void authenticate_existing_user_when_login_is_not_provided() {
    UserDto user = db.users().insertUser(u -> u.setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(UserIdentity.builder()
        .setProviderId(user.getExternalId())
        .setProviderLogin(user.getExternalLogin())
        // No login provided
        .setName(user.getName())
        .setEmail(user.getEmail())
        .build())
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    // No new user is created
    assertThat(db.countRowsOfTable(db.getSession(), "users")).isEqualTo(1);
    assertThat(db.getDbClient().userDao().selectByUuid(db.getSession(), user.getUuid()))
      .extracting(UserDto::getLogin, UserDto::getName, UserDto::getEmail, UserDto::getExternalId, UserDto::getExternalLogin, UserDto::getExternalIdentityProvider,
        UserDto::isActive)
      .contains(user.getExternalLogin(), user.getName(), user.getEmail(), user.getExternalId(), user.getExternalLogin(), user.getExternalIdentityProvider(), true);
  }

  @Test
  public void authenticate_existing_user_with_login_update_and_strategy_is_ALLOW() {
    UserDto user = db.users().insertUser(u -> u
      .setLogin("Old login")
      .setExternalId(USER_IDENTITY.getProviderId())
      .setExternalLogin("old identity")
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    assertThat(db.getDbClient().userDao().selectByUuid(db.getSession(), user.getUuid()))
      .extracting(UserDto::getLogin, UserDto::getExternalLogin)
      .contains(USER_LOGIN, USER_IDENTITY.getProviderLogin());
  }

  @Test
  public void authenticate_existing_disabled_user() {
    organizationFlags.setEnabled(true);
    db.users().insertUser(u -> u
      .setLogin(USER_LOGIN)
      .setActive(false)
      .setName("Old name")
      .setEmail("Old email")
      .setExternalId("Old id")
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));

    underTest.register(UserRegistration.builder()
      .setUserIdentity(USER_IDENTITY)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    UserDto userDto = db.users().selectUserByLogin(USER_LOGIN).get();
    assertThat(userDto.isActive()).isTrue();
    assertThat(userDto.getName()).isEqualTo("John");
    assertThat(userDto.getEmail()).isEqualTo("john@email.com");
    assertThat(userDto.getExternalId()).isEqualTo("ABCD");
    assertThat(userDto.getExternalLogin()).isEqualTo("johndoo");
    assertThat(userDto.getExternalIdentityProvider()).isEqualTo("github");
    assertThat(userDto.isRoot()).isFalse();
  }

  @Test
  public void authenticate_existing_user_when_email_already_exists_and_strategy_is_ALLOW() {
    organizationFlags.setEnabled(true);
    UserDto existingUser = db.users().insertUser(u -> u.setEmail("john@email.com"));
    UserDto currentUser = db.users().insertUser(u -> u.setExternalLogin("johndoo").setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()).setEmail(null));

    UserIdentity userIdentity = UserIdentity.builder()
      .setProviderLogin(currentUser.getExternalLogin())
      .setName("John")
      .setEmail("john@email.com")
      .build();

    currentUser = underTest.register(UserRegistration.builder()
      .setUserIdentity(userIdentity)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.ALLOW)
      .build());

    UserDto existingUserReloaded = db.users().selectUserByLogin(existingUser.getLogin()).get();
    assertThat(existingUserReloaded.getEmail()).isNull();

    UserDto currentUserReloaded = db.users().selectUserByLogin(currentUser.getLogin()).get();
    assertThat(currentUserReloaded.getEmail()).isEqualTo("john@email.com");

  }

  @Test
  public void throw_EmailAlreadyExistException_when_authenticating_existing_user_when_email_already_exists_and_strategy_is_WARN() {
    organizationFlags.setEnabled(true);
    UserDto existingUser = db.users().insertUser(u -> u.setEmail("john@email.com"));
    UserDto currentUser = db.users().insertUser(u -> u.setEmail(null));
    UserIdentity userIdentity = UserIdentity.builder()
      .setProviderLogin("johndoo")
      .setName("John")
      .setEmail("john@email.com")
      .build();

    expectedException.expect(EmailAlreadyExistsRedirectionException.class);

    underTest.register(UserRegistration.builder()
      .setUserIdentity(userIdentity)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.WARN)
      .build());
  }

  @Test
  public void throw_AuthenticationException_when_authenticating_existing_user_when_email_already_exists_and_strategy_is_FORBID() {
    organizationFlags.setEnabled(true);
    UserDto existingUser = db.users().insertUser(u -> u.setEmail("john@email.com"));
    UserDto currentUser = db.users().insertUser(u -> u.setEmail(null));
    UserIdentity userIdentity = UserIdentity.builder()
      .setProviderLogin("johndoo")
      .setName("John")
      .setEmail("john@email.com")
      .build();

    expectedException.expect(authenticationException().from(Source.realm(AuthenticationEvent.Method.FORM, IDENTITY_PROVIDER.getName()))
      .withLogin(userIdentity.getProviderLogin())
      .andPublicMessage("You can't sign up because email 'john@email.com' is already used by an existing user. " +
        "This means that you probably already registered with another account."));
    expectedException.expectMessage("Email 'john@email.com' is already used");

    underTest.register(UserRegistration.builder()
      .setUserIdentity(userIdentity)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.realm(AuthenticationEvent.Method.FORM, IDENTITY_PROVIDER.getName()))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());
  }

  @Test
  public void does_not_fail_to_authenticate_user_when_email_has_not_changed_and_strategy_is_FORBID() {
    organizationFlags.setEnabled(true);
    UserDto currentUser = db.users().insertUser(u -> u.setEmail("john@email.com")
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey()));
    UserIdentity userIdentity = UserIdentity.builder()
      .setProviderId(currentUser.getExternalId())
      .setProviderLogin(currentUser.getExternalLogin())
      .setName("John")
      .setEmail("john@email.com")
      .build();

    underTest.register(UserRegistration.builder()
      .setUserIdentity(userIdentity)
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    UserDto currentUserReloaded = db.users().selectUserByLogin(currentUser.getLogin()).get();
    assertThat(currentUserReloaded.getEmail()).isEqualTo("john@email.com");
  }

  @Test
  public void authenticate_existing_user_and_add_new_groups() {
    organizationFlags.setEnabled(true);
    UserDto user = db.users().insertUser(newUserDto()
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey())
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
      .setActive(true)
      .setName("John"));
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto group2 = db.users().insertGroup(db.getDefaultOrganization(), "group2");

    authenticate(USER_IDENTITY.getProviderLogin(), "group1", "group2", "group3");

    checkGroupMembership(user, group1, group2);
  }

  @Test
  public void authenticate_existing_user_and_remove_groups() {
    organizationFlags.setEnabled(true);
    UserDto user = db.users().insertUser(newUserDto()
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey())
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
      .setActive(true)
      .setName("John"));
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto group2 = db.users().insertGroup(db.getDefaultOrganization(), "group2");
    db.users().insertMember(group1, user);
    db.users().insertMember(group2, user);

    authenticate(USER_IDENTITY.getProviderLogin(), "group1");

    checkGroupMembership(user, group1);
  }

  @Test
  public void authenticate_existing_user_and_remove_all_groups_expect_default_when_organizations_are_disabled() {
    organizationFlags.setEnabled(false);
    UserDto user = db.users().insertUser(newUserDto()
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey())
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
    );
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto group2 = db.users().insertGroup(db.getDefaultOrganization(), "group2");
    GroupDto defaultGroup = insertDefaultGroup();
    db.users().insertMember(group1, user);
    db.users().insertMember(group2, user);
    db.users().insertMember(defaultGroup, user);

    authenticate(user.getExternalLogin());

    checkGroupMembership(user, defaultGroup);
  }

  @Test
  public void does_not_force_default_group_when_authenticating_existing_user_when_organizations_are_enabled() {
    organizationFlags.setEnabled(true);
    UserDto user = db.users().insertUser(newUserDto()
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey())
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
    );
    GroupDto group1 = db.users().insertGroup(db.getDefaultOrganization(), "group1");
    GroupDto defaultGroup = insertDefaultGroup();
    db.users().insertMember(group1, user);
    db.users().insertMember(defaultGroup, user);

    authenticate(user.getExternalLogin(), "group1");

    checkGroupMembership(user, group1);
  }

  @Test
  public void ignore_groups_on_non_default_organizations() {
    organizationFlags.setEnabled(true);
    OrganizationDto org = db.organizations().insert();
    UserDto user = db.users().insertUser(newUserDto()
      .setExternalIdentityProvider(IDENTITY_PROVIDER.getKey())
      .setExternalLogin(USER_IDENTITY.getProviderLogin())
      .setActive(true)
      .setName("John"));
    String groupName = "a-group";
    GroupDto groupInDefaultOrg = db.users().insertGroup(db.getDefaultOrganization(), groupName);
    GroupDto groupInOrg = db.users().insertGroup(org, groupName);

    // adding a group with the same name than in non-default organization
    underTest.register(UserRegistration.builder()
      .setUserIdentity(UserIdentity.builder()
        .setProviderLogin("johndoo")
        .setName(user.getName())
        .setGroups(newHashSet(groupName))
        .build())
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());

    checkGroupMembership(user, groupInDefaultOrg);
  }

  private UserDto authenticate(String providerLogin, String... groups) {
    return underTest.register(UserRegistration.builder()
      .setUserIdentity(UserIdentity.builder()
        .setProviderLogin(providerLogin)
        .setName("John")
        // No group
        .setGroups(stream(groups).collect(MoreCollectors.toSet()))
        .build())
      .setProvider(IDENTITY_PROVIDER)
      .setSource(Source.local(BASIC))
      .setExistingEmailStrategy(ExistingEmailStrategy.FORBID)
      .build());
  }

  private void checkGroupMembership(UserDto user, GroupDto... expectedGroups) {
    assertThat(db.users().selectGroupIdsOfUser(user)).containsOnly(stream(expectedGroups).map(GroupDto::getId).collect(Collectors.toList()).toArray(new Integer[]{}));
  }

  private GroupDto insertDefaultGroup() {
    return db.users().insertDefaultGroup(db.getDefaultOrganization(), "sonar-users");
  }

}
