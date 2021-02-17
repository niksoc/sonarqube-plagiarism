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
package org.sonar.ce.task.projectanalysis.component;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.MessageException;
import org.sonar.ce.task.projectanalysis.analysis.AnalysisMetadataHolderRule;
import org.sonar.ce.task.projectanalysis.analysis.Branch;
import org.sonar.db.component.BranchDto;
import org.sonar.scanner.protocol.output.ScannerReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BranchLoaderTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public AnalysisMetadataHolderRule metadataHolder = new AnalysisMetadataHolderRule();

  @Test
  public void throw_ME_if_both_delegate_absent_and_has_branch_parameters() {
    ScannerReport.Metadata metadata = ScannerReport.Metadata.newBuilder()
      .setBranchName("bar")
      .build();

    expectedException.expect(MessageException.class);
    expectedException.expectMessage("Current edition does not support branch feature");

    new BranchLoader(metadataHolder).load(metadata);
  }

  @Test
  public void regular_analysis_of_project_is_enabled_if_delegate_is_absent() {
    ScannerReport.Metadata metadata = ScannerReport.Metadata.newBuilder()
      .build();

    new BranchLoader(metadataHolder).load(metadata);

    assertThat(metadataHolder.getBranch()).isNotNull();

    Branch branch = metadataHolder.getBranch();
    assertThat(branch.isMain()).isTrue();
    assertThat(branch.getName()).isEqualTo(BranchDto.DEFAULT_MAIN_BRANCH_NAME);
  }

  @Test
  public void default_support_of_branches_is_enabled_if_delegate_is_present_for_main_branch() {
    ScannerReport.Metadata metadata = ScannerReport.Metadata.newBuilder()
      .build();
    BranchLoaderDelegate delegate = mock(BranchLoaderDelegate.class);

    new BranchLoader(metadataHolder, delegate).load(metadata);

    verify(delegate, times(1)).load(metadata);
  }
}
