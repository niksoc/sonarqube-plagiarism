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
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.impl.utils.AlwaysIncreasingSystem2;
import org.sonar.api.impl.utils.JUnitTempFolder;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.qualityprofile.QProfileDto;
import org.sonar.db.qualityprofile.QualityProfileTesting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class QProfileCopierTest {

  private static final String BACKUP = "<backup/>";
  private System2 system2 = new AlwaysIncreasingSystem2();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(system2);
  @Rule
  public JUnitTempFolder temp = new JUnitTempFolder();

  private DummyProfileFactory profileFactory = new DummyProfileFactory();
  private QProfileBackuper backuper = mock(QProfileBackuper.class);
  private QProfileCopier underTest = new QProfileCopier(db.getDbClient(), profileFactory, backuper);

  @Test
  public void create_target_profile_and_copy_rules() {
    OrganizationDto organization = db.organizations().insert();
    QProfileDto source = db.qualityProfiles().insert(organization);

    QProfileDto target = underTest.copyToName(db.getSession(), source, "foo");

    assertThat(target.getLanguage()).isEqualTo(source.getLanguage());
    assertThat(target.getName()).isEqualTo("foo");
    assertThat(target.getParentKee()).isNull();

    verify(backuper).copy(db.getSession(), source, target);
  }

  @Test
  public void create_target_profile_with_same_parent_than_source_profile() {
    OrganizationDto organization = db.organizations().insert();
    QProfileDto parent = db.qualityProfiles().insert(organization);
    QProfileDto source = db.qualityProfiles().insert(organization, p -> p.setParentKee(parent.getKee()));

    QProfileDto target = underTest.copyToName(db.getSession(), source, "foo");

    assertThat(target.getLanguage()).isEqualTo(source.getLanguage());
    assertThat(target.getName()).isEqualTo("foo");
    assertThat(target.getParentKee()).isEqualTo(parent.getKee());

    verify(backuper).copy(db.getSession(), source, target);
  }

  @Test
  public void fail_to_copy_on_self() {
    OrganizationDto organization = db.organizations().insert();
    QProfileDto source = db.qualityProfiles().insert(organization);

    try {
      underTest.copyToName(db.getSession(), source, source.getName());
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Source and target profiles are equal: " + source.getName());
      verifyZeroInteractions(backuper);
    }
  }

  @Test
  public void copy_to_existing_profile() {
    OrganizationDto organization = db.organizations().insert();
    QProfileDto profile1 = db.qualityProfiles().insert(organization);
    QProfileDto profile2 = db.qualityProfiles().insert(organization, p -> p.setLanguage(profile1.getLanguage()));

    QProfileDto target = underTest.copyToName(db.getSession(), profile1, profile2.getName());

    assertThat(profileFactory.createdProfile).isNull();
    assertThat(target.getLanguage()).isEqualTo(profile2.getLanguage());
    assertThat(target.getName()).isEqualTo(profile2.getName());

    verify(backuper).copy(eq(db.getSession()), eq(profile1), argThat(a -> a.getKee().equals(profile2.getKee())));
  }

  private static class DummyProfileFactory implements QProfileFactory {
    private QProfileDto createdProfile;

    @Override
    public QProfileDto getOrCreateCustom(DbSession dbSession, OrganizationDto organization, QProfileName key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public QProfileDto checkAndCreateCustom(DbSession dbSession, OrganizationDto organization, QProfileName key) {
      throw new UnsupportedOperationException();
    }

    @Override public QProfileDto createCustom(DbSession dbSession, OrganizationDto organization, QProfileName key, @Nullable String parentKey) {
      createdProfile = QualityProfileTesting.newQualityProfileDto()
        .setOrganizationUuid(organization.getUuid())
        .setLanguage(key.getLanguage())
        .setParentKee(parentKey)
        .setName(key.getName());
      return createdProfile;
    }

    @Override
    public void delete(DbSession dbSession, Collection<QProfileDto> profiles) {
      throw new UnsupportedOperationException();
    }
  }
}
