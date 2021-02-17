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
package org.sonar.scanner.report;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class CeTaskReportDataHolderTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  CeTaskReportDataHolder underTest = new CeTaskReportDataHolder();

  @Test
  public void should_initialize_field() {
    String ceTaskId = "ceTaskId";
    String ceTaskUrl = "ceTaskUrl";
    String dashboardUrl = "dashboardUrl";
    underTest.init(ceTaskId, ceTaskUrl, dashboardUrl);
    assertThat(underTest.getCeTaskId()).isEqualTo(ceTaskId);
    assertThat(underTest.getCeTaskUrl()).isEqualTo(ceTaskUrl);
    assertThat(underTest.getDashboardUrl()).isEqualTo(dashboardUrl);
  }

  @Test
  public void getCeTaskId_should_fail_if_not_initialized() {
    exception.expect(IllegalStateException.class);
    underTest.getCeTaskId();
  }

  @Test
  public void getCeTaskUrl_should_fail_if_not_initialized() {
    exception.expect(IllegalStateException.class);
    underTest.getCeTaskUrl();
  }

  @Test
  public void getDashboardUrl_should_fail_if_not_initialized() {
    exception.expect(IllegalStateException.class);
    underTest.getDashboardUrl();
  }
}
