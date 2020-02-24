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
package org.sonar.server.ce.ws;

import java.util.Collections;
import java.util.Random;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.util.CloseableIterator;
import org.sonar.core.util.UuidFactoryFast;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskCharacteristicDto;
import org.sonar.db.ce.CeTaskMessageDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Ce;
import org.sonarqube.ws.Common;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.core.permission.GlobalPermissions.SCAN_EXECUTION;
import static org.sonar.db.ce.CeTaskCharacteristicDto.BRANCH_KEY;
import static org.sonar.db.ce.CeTaskCharacteristicDto.BRANCH_TYPE_KEY;
import static org.sonar.db.component.BranchType.BRANCH;
import static org.sonar.db.permission.OrganizationPermission.SCAN;

public class TaskActionTest {

  private static final String SOME_TASK_UUID = "TASK_1";

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private OrganizationDto organization;
  private ComponentDto privateProject;
  private ComponentDto publicProject;
  private TaskFormatter formatter = new TaskFormatter(db.getDbClient(), System2.INSTANCE);
  private TaskAction underTest = new TaskAction(db.getDbClient(), formatter, userSession);
  private WsActionTester ws = new WsActionTester(underTest);

  @Before
  public void setUp() {
    organization = db.organizations().insert();
    privateProject = db.components().insertPrivateProject(organization);
    publicProject = db.components().insertPublicProject(organization);
  }

  @Test
  public void task_is_in_queue() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).setRoot();

    CeQueueDto queueDto = new CeQueueDto();
    queueDto.setTaskType(CeTaskTypes.REPORT);
    queueDto.setUuid(SOME_TASK_UUID);
    queueDto.setComponentUuid(privateProject.uuid());
    queueDto.setStatus(CeQueueDto.Status.PENDING);
    queueDto.setSubmitterUuid(user.getUuid());
    persist(queueDto);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);
    assertThat(taskResponse.getTask().getOrganization()).isEqualTo(organization.getKey());
    assertThat(taskResponse.getTask().getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(taskResponse.getTask().getStatus()).isEqualTo(Ce.TaskStatus.PENDING);
    assertThat(taskResponse.getTask().getSubmitterLogin()).isEqualTo(user.getLogin());
    assertThat(taskResponse.getTask().getComponentId()).isEqualTo(privateProject.uuid());
    assertThat(taskResponse.getTask().getComponentKey()).isEqualTo(privateProject.getDbKey());
    assertThat(taskResponse.getTask().getComponentName()).isEqualTo(privateProject.name());
    assertThat(taskResponse.getTask().hasExecutionTimeMs()).isFalse();
    assertThat(taskResponse.getTask().getLogs()).isFalse();
    assertThat(taskResponse.getTask().getWarningCount()).isZero();
    assertThat(taskResponse.getTask().getWarningsList()).isEmpty();
  }

  @Test
  public void no_warning_detail_on_task_in_queue() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).setRoot();
    CeQueueDto queueDto = createAndPersistQueueTask(null, user);
    IntStream.range(0, 1 + new Random().nextInt(5))
      .forEach(i -> db.getDbClient().ceTaskMessageDao().insert(db.getSession(),
        new CeTaskMessageDto()
          .setUuid("u_" + i)
          .setTaskUuid(queueDto.getUuid())
          .setMessage("m_" + i)
          .setCreatedAt(queueDto.getUuid().hashCode() + i)));
    db.commit();

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getWarningCount()).isZero();
    assertThat(task.getWarningsList()).isEmpty();
  }

  @Test
  public void task_is_archived() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).setRoot();

    CeActivityDto activityDto = createActivityDto(SOME_TASK_UUID);
    persist(activityDto);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getOrganization()).isEqualTo(organization.getKey());
    assertThat(task.getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(task.getStatus()).isEqualTo(Ce.TaskStatus.FAILED);
    assertThat(task.getComponentId()).isEqualTo(privateProject.uuid());
    assertThat(task.getComponentKey()).isEqualTo(privateProject.getDbKey());
    assertThat(task.getComponentName()).isEqualTo(privateProject.name());
    assertThat(task.getAnalysisId()).isEqualTo(activityDto.getAnalysisUuid());
    assertThat(task.getExecutionTimeMs()).isEqualTo(500L);
    assertThat(task.getLogs()).isFalse();
    assertThat(task.getWarningCount()).isZero();
    assertThat(task.getWarningsList()).isEmpty();
  }

  @Test
  public void branch_in_past_activity() {
    logInAsRoot();
    ComponentDto project = db.components().insertPrivateProject();
    userSession.addProjectPermission(UserRole.USER, project);
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setBranchType(BRANCH));
    db.components().insertSnapshot(branch);
    CeActivityDto activity = createAndPersistArchivedTask(project);
    insertCharacteristic(activity, BRANCH_KEY, branch.getBranch());
    insertCharacteristic(activity, BRANCH_TYPE_KEY, BRANCH.name());

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);

    assertThat(taskResponse.getTask())
      .extracting(Ce.Task::getId, Ce.Task::getBranch, Ce.Task::getBranchType, Ce.Task::getComponentKey)
      .containsExactlyInAnyOrder(SOME_TASK_UUID, branch.getBranch(), Common.BranchType.BRANCH, branch.getKey());
  }

  @Test
  public void branch_in_queue_analysis() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).setRoot();
    String branch = "my_branch";
    CeQueueDto queueDto = createAndPersistQueueTask(null, user);
    insertCharacteristic(queueDto, BRANCH_KEY, branch);
    insertCharacteristic(queueDto, BRANCH_TYPE_KEY, BRANCH.name());

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);

    assertThat(taskResponse.getTask())
      .extracting(Ce.Task::getId, Ce.Task::getBranch, Ce.Task::getBranchType, Ce.Task::hasComponentKey)
      .containsExactlyInAnyOrder(SOME_TASK_UUID, branch, Common.BranchType.BRANCH, false);
  }

  @Test
  public void return_stacktrace_of_failed_activity_with_stacktrace_when_additionalField_is_set() {
    logInAsRoot();

    CeActivityDto activityDto = createActivityDto(SOME_TASK_UUID)
      .setErrorMessage("error msg")
      .setErrorStacktrace("error stack");
    persist(activityDto);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .setParam("additionalFields", "stacktrace")
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(task.getErrorMessage()).isEqualTo(activityDto.getErrorMessage());
    assertThat(task.hasErrorStacktrace()).isTrue();
    assertThat(task.getErrorStacktrace()).isEqualTo(activityDto.getErrorStacktrace());
  }

  @Test
  public void do_not_return_stacktrace_of_failed_activity_with_stacktrace_when_additionalField_is_not_set() {
    logInAsRoot();

    CeActivityDto activityDto = createActivityDto(SOME_TASK_UUID)
      .setErrorMessage("error msg")
      .setErrorStacktrace("error stack");
    persist(activityDto);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(task.getErrorMessage()).isEqualTo(activityDto.getErrorMessage());
    assertThat(task.hasErrorStacktrace()).isFalse();
  }

  @Test
  public void return_scannerContext_of_activity_with_scannerContext_when_additionalField_is_set() {
    logInAsRoot();

    String scannerContext = "this is some scanner context, yeah!";
    persist(createActivityDto(SOME_TASK_UUID));
    persistScannerContext(SOME_TASK_UUID, scannerContext);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .setParam("additionalFields", "scannerContext")
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(task.getScannerContext()).isEqualTo(scannerContext);
  }

  @Test
  public void do_not_return_scannerContext_of_activity_with_scannerContext_when_additionalField_is_not_set() {
    logInAsRoot();

    String scannerContext = "this is some scanner context, yeah!";
    persist(createActivityDto(SOME_TASK_UUID));
    persistScannerContext(SOME_TASK_UUID, scannerContext);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .setParam("additionalFields", "stacktrace")
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(task.hasScannerContext()).isFalse();
  }

  @Test
  public void do_not_return_stacktrace_of_failed_activity_without_stacktrace() {
    logInAsRoot();

    CeActivityDto activityDto = createActivityDto(SOME_TASK_UUID)
      .setErrorMessage("error msg");
    persist(activityDto);

    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", SOME_TASK_UUID)
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(SOME_TASK_UUID);
    assertThat(task.getErrorMessage()).isEqualTo(activityDto.getErrorMessage());
    assertThat(task.hasErrorStacktrace()).isFalse();
  }

  @Test
  public void throw_NotFoundException_if_id_does_not_exist() {
    logInAsRoot();

    expectedException.expect(NotFoundException.class);

    ws.newRequest()
      .setParam("id", "DOES_NOT_EXIST")
      .execute();
  }

  @Test
  public void get_project_queue_task_with_scan_permission_on_project() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).addProjectPermission(GlobalPermissions.SCAN_EXECUTION, privateProject);
    CeQueueDto task = createAndPersistQueueTask(privateProject, user);

    call(task.getUuid());
  }

  @Test
  public void getting_project_queue_task_of_public_project_fails_with_ForbiddenException() {
    UserDto user = db.users().insertUser();
    userSession.logIn().registerComponents(publicProject);
    CeQueueDto task = createAndPersistQueueTask(publicProject, user);

    expectedException.expect(ForbiddenException.class);

    call(task.getUuid());
  }

  @Test
  public void get_project_queue_task_of_private_project_with_user_permission_fails_with_ForbiddenException() {
    UserDto user = db.users().insertUser();
    userSession.logIn().addProjectPermission(UserRole.USER, privateProject);
    CeQueueDto task = createAndPersistQueueTask(privateProject, user);

    expectedException.expect(ForbiddenException.class);

    call(task.getUuid());
  }

  @Test
  public void get_project_queue_task_on_public_project() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).addProjectPermission(SCAN_EXECUTION, privateProject);
    CeQueueDto task = createAndPersistQueueTask(privateProject, user);

    call(task.getUuid());
  }

  @Test
  public void get_project_queue_task_with_scan_permission_on_organization_but_not_on_project() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).addPermission(SCAN, privateProject.getOrganizationUuid());
    CeQueueDto task = createAndPersistQueueTask(privateProject, user);

    call(task.getUuid());
  }

  @Test
  public void getting_project_queue_task_throws_ForbiddenException_if_no_admin_nor_scan_permissions() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user);
    CeQueueDto task = createAndPersistQueueTask(privateProject, user);

    expectedException.expect(ForbiddenException.class);

    call(task.getUuid());
  }

  @Test
  public void getting_global_queue_task_requires_to_be_system_administrator() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).setSystemAdministrator();
    CeQueueDto task = createAndPersistQueueTask(null, user);

    call(task.getUuid());
  }

  @Test
  public void getting_global_queue_throws_ForbiddenException_if_not_system_administrator() {
    UserDto user = db.users().insertUser();
    userSession.logIn(user).setNonSystemAdministrator();
    CeQueueDto task = createAndPersistQueueTask(null, user);

    expectedException.expect(ForbiddenException.class);

    call(task.getUuid());
  }

  @Test
  public void get_project_archived_task_with_scan_permission_on_project() {
    userSession.logIn().addProjectPermission(GlobalPermissions.SCAN_EXECUTION, privateProject);
    CeActivityDto task = createAndPersistArchivedTask(privateProject);

    call(task.getUuid());
  }

  @Test
  public void getting_archived_task_of_public_project_fails_with_ForbiddenException() {
    userSession.logIn().registerComponents(publicProject);
    CeActivityDto task = createAndPersistArchivedTask(publicProject);

    expectedException.expect(ForbiddenException.class);
    
    call(task.getUuid());
  }

  @Test
  public void get_project_archived_task_with_scan_permission_on_organization_but_not_on_project() {
    userSession.logIn().addPermission(SCAN, privateProject.getOrganizationUuid());
    CeActivityDto task = createAndPersistArchivedTask(privateProject);

    call(task.getUuid());
  }

  @Test
  public void getting_project_archived_task_throws_ForbiddenException_if_no_admin_nor_scan_permissions() {
    userSession.logIn();
    CeActivityDto task = createAndPersistArchivedTask(privateProject);

    expectedException.expect(ForbiddenException.class);

    call(task.getUuid());
  }

  @Test
  public void getting_global_archived_task_requires_to_be_system_administrator() {
    logInAsSystemAdministrator();
    CeActivityDto task = createAndPersistArchivedTask(null);

    call(task.getUuid());
  }

  @Test
  public void getting_global_archived_throws_ForbiddenException_if_not_system_administrator() {
    userSession.logIn().setNonSystemAdministrator();
    CeActivityDto task = createAndPersistArchivedTask(null);

    expectedException.expect(ForbiddenException.class);

    call(task.getUuid());
  }

  @Test
  public void get_warnings_on_global_archived_task_requires_to_be_system_administrator() {
    logInAsSystemAdministrator();

    getWarningsImpl(createAndPersistArchivedTask(null));
  }

  @Test
  public void get_warnings_on_public_project_archived_task_if_not_admin_fails_with_ForbiddenException() {
    userSession.logIn().registerComponents(publicProject);

    expectedException.expect(ForbiddenException.class);

    getWarningsImpl(createAndPersistArchivedTask(publicProject));
  }

  @Test
  public void get_warnings_on_private_project_archived_task_if_user_fails_with_ForbiddenException() {
    userSession.logIn().addProjectPermission(UserRole.USER, privateProject);

    expectedException.expect(ForbiddenException.class);

    getWarningsImpl(createAndPersistArchivedTask(privateProject));
  }

  @Test
  public void get_warnings_on_private_project_archived_task_if_scan() {
    userSession.logIn().addProjectPermission(SCAN_EXECUTION, privateProject);

    getWarningsImpl(createAndPersistArchivedTask(privateProject));
  }

  @Test
  public void get_warnings_on_private_project_archived_task_if_scan_on_organization() {
    userSession.logIn().addPermission(OrganizationPermission.SCAN, organization);

    getWarningsImpl(createAndPersistArchivedTask(privateProject));
  }

  private void getWarningsImpl(CeActivityDto task) {
    String[] warnings = IntStream.range(0, 1 + new Random().nextInt(10))
      .mapToObj(i -> insertWarning(task, i))
      .map(CeTaskMessageDto::getMessage)
      .toArray(String[]::new);

    Ce.Task taskWithWarnings = callWithWarnings(task.getUuid());
    assertThat(taskWithWarnings.getWarningCount()).isEqualTo(warnings.length);
    assertThat(taskWithWarnings.getWarningsList()).containsExactly(warnings);
  }

  private CeTaskMessageDto insertWarning(CeActivityDto task, int i) {
    CeTaskMessageDto res = new CeTaskMessageDto()
      .setUuid(UuidFactoryFast.getInstance().create())
      .setTaskUuid(task.getUuid())
      .setMessage("msg_" + task.getUuid() + "_" + i)
      .setCreatedAt(task.getUuid().hashCode() + i);
    db.getDbClient().ceTaskMessageDao().insert(db.getSession(), res);
    db.getSession().commit();
    return res;
  }

  private CeActivityDto createAndPersistArchivedTask(@Nullable ComponentDto component) {
    CeQueueDto queueDto = new CeQueueDto();
    queueDto.setTaskType(CeTaskTypes.REPORT);
    queueDto.setUuid(SOME_TASK_UUID);
    if (component != null) {
      queueDto.setComponentUuid(component.uuid());
    }
    CeActivityDto activityDto = new CeActivityDto(queueDto);
    activityDto.setStatus(CeActivityDto.Status.FAILED);
    activityDto.setExecutionTimeMs(500L);
    activityDto.setAnalysisUuid(SOME_TASK_UUID + "_u1");
    persist(activityDto);
    return activityDto;
  }

  private CeActivityDto createActivityDto(String uuid) {
    CeQueueDto queueDto = createQueueDto(uuid);
    CeActivityDto activityDto = new CeActivityDto(queueDto);
    activityDto.setStatus(CeActivityDto.Status.FAILED);
    activityDto.setExecutionTimeMs(500L);
    activityDto.setAnalysisUuid(uuid + "u1");
    return activityDto;
  }

  private CeQueueDto createQueueDto(String uuid) {
    CeQueueDto queueDto = new CeQueueDto();
    queueDto.setTaskType(CeTaskTypes.REPORT);
    queueDto.setUuid(uuid);
    queueDto.setComponentUuid(privateProject.uuid());
    return queueDto;
  }

  private CeQueueDto createAndPersistQueueTask(@Nullable ComponentDto component, UserDto user) {
    CeQueueDto dto = new CeQueueDto();
    dto.setTaskType(CeTaskTypes.REPORT);
    dto.setUuid(SOME_TASK_UUID);
    dto.setStatus(CeQueueDto.Status.PENDING);
    dto.setSubmitterUuid(user.getUuid());
    if (component != null) {
      dto.setComponentUuid(component.uuid());
    }
    persist(dto);
    return dto;
  }

  private CeTaskCharacteristicDto insertCharacteristic(CeQueueDto queueDto, String key, String value) {
    return insertCharacteristic(queueDto.getUuid(), key, value);
  }

  private CeTaskCharacteristicDto insertCharacteristic(CeActivityDto activityDto, String key, String value) {
    return insertCharacteristic(activityDto.getUuid(), key, value);
  }

  private CeTaskCharacteristicDto insertCharacteristic(String taskUuid, String key, String value) {
    CeTaskCharacteristicDto dto = new CeTaskCharacteristicDto()
      .setUuid(Uuids.createFast())
      .setTaskUuid(taskUuid)
      .setKey(key)
      .setValue(value);
    db.getDbClient().ceTaskCharacteristicsDao().insert(db.getSession(), Collections.singletonList(dto));
    db.commit();
    return dto;
  }

  private void persist(CeQueueDto queueDto) {
    db.getDbClient().ceQueueDao().insert(db.getSession(), queueDto);
    db.commit();
  }

  private CeActivityDto persist(CeActivityDto activityDto) {
    db.getDbClient().ceActivityDao().insert(db.getSession(), activityDto);
    db.commit();
    return activityDto;
  }

  private void persistScannerContext(String taskUuid, String scannerContext) {
    db.getDbClient().ceScannerContextDao().insert(db.getSession(), taskUuid, CloseableIterator.from(singleton(scannerContext).iterator()));
    db.commit();
  }

  private void logInAsSystemAdministrator() {
    userSession.logIn().setSystemAdministrator();
  }

  private void logInAsRoot() {
    userSession.logIn().setRoot();
  }

  private void call(String taskUuid) {
    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", taskUuid)
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(taskUuid);
  }

  private Ce.Task callWithWarnings(String taskUuid) {
    Ce.TaskResponse taskResponse = ws.newRequest()
      .setParam("id", taskUuid)
      .setParam("additionalFields", "warnings")
      .executeProtobuf(Ce.TaskResponse.class);
    Ce.Task task = taskResponse.getTask();
    assertThat(task.getId()).isEqualTo(taskUuid);
    return task;
  }

}
