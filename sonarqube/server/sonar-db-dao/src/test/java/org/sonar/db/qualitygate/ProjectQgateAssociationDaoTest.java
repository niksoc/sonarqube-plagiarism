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
package org.sonar.db.qualitygate;

import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ProjectQgateAssociationDaoTest {

  @Rule
  public DbTester db = DbTester.create();

  private DbSession dbSession = db.getSession();
  private ProjectQgateAssociationDao underTest = db.getDbClient().projectQgateAssociationDao();

  @Test
  public void select_all_projects_by_query() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate1 = db.qualityGates().insertQualityGate(organization);
    QGateWithOrgDto qualityGate2 = db.qualityGates().insertQualityGate(organization);
    ComponentDto project1 = db.components().insertPrivateProject(organization);
    ComponentDto project2 = db.components().insertPrivateProject(organization);
    ComponentDto project3 = db.components().insertPrivateProject(organization);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project1), qualityGate1);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project2), qualityGate1);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project3), qualityGate2);

    List<ProjectQgateAssociationDto> result = underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate1)
      .build());

    assertThat(result)
      .extracting(ProjectQgateAssociationDto::getId, ProjectQgateAssociationDto::getKey, ProjectQgateAssociationDto::getName, ProjectQgateAssociationDto::getGateId)
      .containsExactlyInAnyOrder(
        tuple(project1.getId(), project1.getKey(), project1.name(), qualityGate1.getId().toString()),
        tuple(project2.getId(), project2.getKey(), project2.name(), qualityGate1.getId().toString()),
        tuple(project3.getId(), project3.getKey(), project3.name(), null));
  }

  @Test
  public void select_projects_by_query() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ComponentDto project1 = db.components().insertPrivateProject(organization);
    ComponentDto project2 = db.components().insertPrivateProject(organization);
    ComponentDto project3 = db.components().insertPrivateProject(organization);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project1), qualityGate);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project2), qualityGate);

    assertThat(underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate)
      .membership(ProjectQgateAssociationQuery.IN)
      .build()))
      .extracting(ProjectQgateAssociationDto::getId, ProjectQgateAssociationDto::getName, ProjectQgateAssociationDto::getGateId)
      .containsExactlyInAnyOrder(
        tuple(project1.getId(), project1.name(), qualityGate.getId().toString()),
        tuple(project2.getId(), project2.name(), qualityGate.getId().toString()));

    assertThat(underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate)
      .membership(ProjectQgateAssociationQuery.OUT)
      .build()))
      .extracting(ProjectQgateAssociationDto::getId, ProjectQgateAssociationDto::getName, ProjectQgateAssociationDto::getGateId)
      .containsExactlyInAnyOrder(tuple(project3.getId(), project3.name(), null));
  }

  @Test
  public void search_by_project_name() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ComponentDto project1 = db.components().insertPrivateProject(organization, p -> p.setName("Project One"));
    ComponentDto project2 = db.components().insertPrivateProject(organization, p -> p.setName("Project Two"));
    ComponentDto project3 = db.components().insertPrivateProject(organization, p -> p.setName("Project Three"));
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project1), qualityGate);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project2), qualityGate);

    assertThat(underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate)
      .projectSearch("one")
      .build()))
      .extracting(ProjectQgateAssociationDto::getId)
      .containsExactlyInAnyOrder(project1.getId());

    assertThat(underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate)
      .projectSearch("project")
      .build()))
      .extracting(ProjectQgateAssociationDto::getId)
      .containsExactlyInAnyOrder(project1.getId(), project2.getId(), project3.getId());
  }

  @Test
  public void sorted_by_project_name() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ComponentDto project1 = db.components().insertPrivateProject(organization, p -> p.setName("Project One"));
    ComponentDto project2 = db.components().insertPrivateProject(organization, p -> p.setName("Project Two"));
    ComponentDto project3 = db.components().insertPrivateProject(organization, p -> p.setName("Project Three"));

    assertThat(underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate)
      .build()))
      .extracting(ProjectQgateAssociationDto::getId)
      .containsExactly(project1.getId(), project3.getId(), project2.getId());
  }

  @Test
  public void return_only_projects_from_organization() {
    OrganizationDto organization = db.organizations().insert();
    OrganizationDto otherOrganization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    QGateWithOrgDto otherQualityGate = db.qualityGates().insertQualityGate(otherOrganization);
    ComponentDto project = db.components().insertPrivateProject(organization);
    ComponentDto otherProject = db.components().insertPrivateProject(otherOrganization);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project), qualityGate);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(otherProject), otherQualityGate);

    List<ProjectQgateAssociationDto> result = underTest.selectProjects(dbSession, ProjectQgateAssociationQuery.builder()
      .qualityGate(qualityGate)
      .build());

    assertThat(result)
      .extracting(ProjectQgateAssociationDto::getId)
      .containsExactlyInAnyOrder(project.getId());
  }

  @Test
  public void select_qgate_uuid_is_absent() {
    ComponentDto project = db.components().insertPrivateProject();

    Optional<String> result = underTest.selectQGateUuidByProjectUuid(dbSession, project.uuid());

    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void select_qgate_uuid() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate1 = db.qualityGates().insertQualityGate(organization);
    QGateWithOrgDto qualityGate2 = db.qualityGates().insertQualityGate(organization);
    ComponentDto project1 = db.components().insertPrivateProject(organization);
    ComponentDto project2 = db.components().insertPrivateProject(organization);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project1), qualityGate1);
    db.qualityGates().associateProjectToQualityGate(db.components().getProjectDto(project2), qualityGate2);

    Optional<String> result = underTest.selectQGateUuidByProjectUuid(dbSession, project1.uuid());

    assertThat(result).contains(qualityGate1.getUuid());
  }

  @Test
  public void delete_by_project_uuid() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);

    db.qualityGates().associateProjectToQualityGate(project, qualityGate);

    underTest.deleteByProjectUuid(dbSession, project.getUuid());

    Optional<String> deletedQualityGate = db.qualityGates().selectQGateUuidByComponentUuid(project.getUuid());

    assertThat(deletedQualityGate).isEmpty();
  }

  @Test
  public void delete_by_qgate_uuid() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto qualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);

    db.qualityGates().associateProjectToQualityGate(project, qualityGate);

    underTest.deleteByQGateUuid(dbSession, qualityGate.getUuid());

    Optional<String> deletedQualityGate = db.qualityGates().selectQGateUuidByComponentUuid(project.getUuid());

    assertThat(deletedQualityGate).isEmpty();
  }

  @Test
  public void update_project_qgate_association() {
    OrganizationDto organization = db.organizations().insert();
    QGateWithOrgDto firstQualityGate = db.qualityGates().insertQualityGate(organization);
    QGateWithOrgDto secondQualityGate = db.qualityGates().insertQualityGate(organization);
    ProjectDto project = db.components().insertPrivateProjectDto(organization);

    db.qualityGates().associateProjectToQualityGate(project, firstQualityGate);

    underTest.updateProjectQGateAssociation(dbSession, project.getUuid(), secondQualityGate.getUuid());

    Optional<String> updatedQualityGateUuid = db.qualityGates().selectQGateUuidByComponentUuid(project.getUuid());

    assertThat(updatedQualityGateUuid).contains(secondQualityGate.getUuid());
  }
}
