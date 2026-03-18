package com.church.qt.admin;

import com.church.qt.domain.student.Student;
import com.church.qt.domain.student.StudentRepository;
import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRepository;
import com.church.qt.domain.teacher.TeacherRole;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClass;
import com.church.qt.domain.yearclass.YearClassRepository;
import com.church.qt.domain.yearclass.YearClassStudent;
import com.church.qt.domain.yearclass.YearClassStudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminServiceIntegrationTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private YearClassRepository yearClassRepository;

    @Autowired
    private YearClassStudentRepository yearClassStudentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("관리자 bootstrap 조회는 me와 years를 함께 반환한다")
    void getAdminBootstrap_returnsMeAndYears() {
        Teacher admin = saveTeacher("admin_bootstrap_service", TeacherRole.ADMIN, true);
        Year oldYear = saveYear(4101, true, true, true);
        Year newYear = saveYear(4102, true, true, true);
        saveYearClass(oldYear, "old-class", 1, true);
        saveYearClass(newYear, "new-class", 1, true);
        saveStudent("bootstrap-student", 4, true);
        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, CURRENT_TIMESTAMP)",
                admin.getId(),
                "ASSIGN_STUDENT",
                "bootstrap test"
        );

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("admin_bootstrap_service", response.me().loginId());
        assertEquals("v1", response.schemaVersion());
        assertTrue(response.generatedAt() != null && !response.generatedAt().isBlank());
        OffsetDateTime parsedGeneratedAt = OffsetDateTime.parse(response.generatedAt());
        assertNotNull(parsedGeneratedAt);
        assertEquals(TeacherRole.ADMIN, response.me().role());
        assertEquals(4102, response.selectedYear());
        assertTrue(response.years().stream().anyMatch(item -> item.yearValue() == 4102));
        assertTrue(response.years().stream().anyMatch(item -> item.yearValue() == 4101));
        assertTrue(response.auditLogActionTypes().contains("ASSIGN_STUDENT"));
        assertEquals(1, response.yearClasses().size());
        assertEquals(4102, response.yearClasses().get(0).yearValue());
        assertEquals("new-class", response.yearClasses().get(0).className());
        assertTrue(response.teachers().totalCount() >= 1);
        assertTrue(response.students().totalCount() >= 1);
        assertTrue(response.auditLogs().limit() > 0);
        assertTrue(response.pool().activeOnly());
        assertNull(response.audit().actorTeacherId());
    }

    @Test
    @DisplayName("관리자 bootstrap 조회는 year 파라미터 기준으로 yearClasses를 반환한다")
    void getAdminBootstrap_withYearParam_returnsTargetYearClasses() {
        Teacher admin = saveTeacher("admin_bootstrap_service_year", TeacherRole.ADMIN, true);
        Year yearA = saveYear(4201, true, true, true);
        Year yearB = saveYear(4202, true, true, true);
        saveYearClass(yearA, "A-class", 1, true);
        saveYearClass(yearB, "B-class", 1, true);

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                4201,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(4201, response.selectedYear());
        assertEquals(1, response.yearClasses().size());
        assertEquals(4201, response.yearClasses().get(0).yearValue());
        assertEquals("A-class", response.yearClasses().get(0).className());
    }

    @Test
    @DisplayName("관리자 bootstrap 조회에서 존재하지 않는 year 파라미터는 예외를 반환한다")
    void getAdminBootstrap_withMissingYear_throwsException() {
        Teacher admin = saveTeacher("admin_bootstrap_service_missing", TeacherRole.ADMIN, true);
        saveYear(4301, true, true, true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.getAdminBootstrap(admin.getId(), 9999, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
        );

        assertEquals("연도가 존재하지 않습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("관리자 bootstrap 조회는 pool 필터 파라미터를 teachers/students에 반영한다")
    void getAdminBootstrap_withPoolParams_appliesPoolFilter() {
        Teacher admin = saveTeacher("admin_bootstrap_pool", TeacherRole.ADMIN, true);
        saveYear(4401, true, true, true);
        saveTeacher("pool_target_teacher", TeacherRole.TEACHER, true);
        saveTeacher("pool_hidden_teacher", TeacherRole.TEACHER, false);
        saveStudent("pool_target_student", 3, true);
        saveStudent("other_student", 2, true);

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                4401,
                "pool_target",
                true,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(10, response.teachers().limit());
        assertEquals(10, response.students().limit());
        assertEquals("pool_target", response.pool().keyword());
        assertEquals(10, response.pool().limit());
        assertTrue(response.teachers().items().stream().anyMatch(item -> item.loginId().contains("pool_target")));
        assertTrue(response.students().items().stream().anyMatch(item -> item.studentName().contains("pool_target")));
    }

    @Test
    @DisplayName("관리자 bootstrap 조회는 audit 파라미터를 auditLogs에 반영한다")
    void getAdminBootstrap_withAuditParams_appliesAuditFilter() {
        Teacher admin = saveTeacher("admin_bootstrap_audit", TeacherRole.ADMIN, true);
        saveYear(4501, true, true, true);
        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, CURRENT_TIMESTAMP)",
                admin.getId(),
                "MOVE_STUDENT",
                "moved by bootstrap"
        );
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, CURRENT_TIMESTAMP)",
                9999L,
                "MOVE_STUDENT",
                "moved by other actor"
        );

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                4501,
                null,
                null,
                null,
                5,
                0,
                admin.getId(),
                "MOVE_STUDENT",
                "moved",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(5, response.auditLogs().limit());
        assertEquals(admin.getId(), response.audit().actorTeacherId());
        assertEquals("MOVE_STUDENT", response.audit().actionType());
        assertEquals("moved", response.audit().keyword());
        assertEquals(1L, response.auditLogs().totalCount());
        assertEquals(1, response.auditLogs().items().size());
    }

    @Test
    @DisplayName("관리자 bootstrap 조회는 audit_logs 테이블이 없어도 빈 auditLogs로 성공한다")
    void getAdminBootstrap_withoutAuditTable_returnsEmptyAuditLogs() {
        Teacher admin = saveTeacher("admin_bootstrap_no_audit", TeacherRole.ADMIN, true);
        saveYear(4601, true, true, true);
        jdbcTemplate.execute("drop table if exists audit_logs");

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                4601,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(0L, response.auditLogs().totalCount());
        assertTrue(response.auditLogs().items().isEmpty());
    }

    @Test
    @DisplayName("관리자 bootstrap 조회는 include 플래그로 pools/auditLogs를 비포함 처리할 수 있다")
    void getAdminBootstrap_withIncludeFlagsFalse_returnsEmptyPayloadSections() {
        Teacher admin = saveTeacher("admin_bootstrap_exclude", TeacherRole.ADMIN, true);
        saveYear(4701, true, true, true);
        saveStudent("exclude-student", 2, true);
        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, CURRENT_TIMESTAMP)",
                admin.getId(),
                "ASSIGN_STUDENT",
                "exclude test"
        );

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                4701,
                null,
                null,
                10,
                5,
                0,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null
        );

        assertTrue(response.teachers().items().isEmpty());
        assertTrue(response.students().items().isEmpty());
        assertTrue(response.auditLogs().items().isEmpty());
        assertEquals(10, response.pool().limit());
        assertEquals(5, response.audit().limit());
    }

    @Test
    @DisplayName("관리자 bootstrap 조회는 includeYearClasses/includeActionTypes=false 시 해당 섹션을 비운다")
    void getAdminBootstrap_withIncludeYearClassesAndActionTypesFalse_returnsEmptySections() {
        Teacher admin = saveTeacher("admin_bootstrap_sections_off", TeacherRole.ADMIN, true);
        Year year = saveYear(4801, true, true, true);
        saveYearClass(year, "off-class", 1, true);
        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, CURRENT_TIMESTAMP)",
                admin.getId(),
                "MOVE_STUDENT",
                "sections off"
        );

        AdminBootstrapResponse response = adminService.getAdminBootstrap(
                admin.getId(),
                4801,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );

        assertTrue(response.yearClasses().isEmpty());
        assertTrue(response.auditLogActionTypes().isEmpty());
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 배정 API 호출이 실패한다")
    void assignTeacher_requiresAdminRole() {
        Teacher nonAdmin = saveTeacher("teacher01", TeacherRole.TEACHER, true);
        YearClass yearClass = saveYearClass(2026, "사랑반", 1, true);
        Teacher target = saveTeacher("teacher02", TeacherRole.TEACHER, true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.assignTeacherToYearClass(
                        nonAdmin.getId(),
                        yearClass.getId(),
                        new AssignYearClassTeacherRequest(List.of(target.getId()))
                )
        );

        assertEquals("관리자 권한이 없습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("교사 배정은 중복 요청 시 assigned/skipped가 구분된다")
    void assignTeacher_duplicateHandledAsSkipped() {
        Teacher admin = saveTeacher("admin01", TeacherRole.ADMIN, true);
        YearClass yearClass = saveYearClass(2026, "믿음반", 1, true);
        Teacher target = saveTeacher("teacher03", TeacherRole.TEACHER, true);

        YearClassTeacherAssignmentBatchResponse first = adminService.assignTeacherToYearClass(
                admin.getId(),
                yearClass.getId(),
                new AssignYearClassTeacherRequest(List.of(target.getId()))
        );
        YearClassTeacherAssignmentBatchResponse second = adminService.assignTeacherToYearClass(
                admin.getId(),
                yearClass.getId(),
                new AssignYearClassTeacherRequest(List.of(target.getId()))
        );

        assertEquals(List.of(target.getId()), first.assignedTeacherIds());
        assertTrue(first.skippedTeacherIds().isEmpty());
        assertTrue(second.assignedTeacherIds().isEmpty());
        assertEquals(List.of(target.getId()), second.skippedTeacherIds());
    }

    @Test
    @DisplayName("교사는 같은 연도의 다른 반에 중복 배정될 수 없다")
    void assignTeacher_forbiddenWhenAssignedToAnotherClassInSameYear() {
        Teacher admin = saveTeacher("admin_teacher_dup", TeacherRole.ADMIN, true);
        Year year = saveYear(2027, true, true, true);
        YearClass classA = saveYearClass(year, "온유반", 1, true);
        YearClass classB = saveYearClass(year, "충성반", 2, true);
        Teacher target = saveTeacher("teacher_dup_same_year", TeacherRole.TEACHER, true);

        adminService.assignTeacherToYearClass(
                admin.getId(),
                classA.getId(),
                new AssignYearClassTeacherRequest(List.of(target.getId()))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.assignTeacherToYearClass(
                        admin.getId(),
                        classB.getId(),
                        new AssignYearClassTeacherRequest(List.of(target.getId()))
                )
        );

        assertTrue(exception.getMessage().contains("이미 이 연도에 다른 반"));
    }

    @Test
    @DisplayName("학생은 같은 연도의 다른 반에 중복 배정될 수 없다")
    void assignStudent_forbiddenWhenAssignedToAnotherClassInSameYear() {
        Teacher admin = saveTeacher("admin02", TeacherRole.ADMIN, true);
        Year year = saveYear(2026, true, true, true);
        YearClass classA = saveYearClass(year, "소망반", 1, true);
        YearClass classB = saveYearClass(year, "은혜반", 2, true);
        Student student = saveStudent("홍길동", 5, true);

        adminService.assignStudentToYearClass(
                admin.getId(),
                classA.getId(),
                new AssignYearClassStudentRequest(List.of(student.getId()))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.assignStudentToYearClass(
                        admin.getId(),
                        classB.getId(),
                        new AssignYearClassStudentRequest(List.of(student.getId()))
                )
        );

        assertTrue(exception.getMessage().contains("이미 이 연도에 다른 반"));
    }

    @Test
    @DisplayName("학생 이동 API 호출 시 대상 반으로 소속이 변경된다")
    void moveStudents_updatesYearClassStudent() {
        Teacher admin = saveTeacher("admin03", TeacherRole.ADMIN, true);
        Year year = saveYear(2026, true, true, true);
        YearClass source = saveYearClass(year, "진리반", 1, true);
        YearClass target = saveYearClass(year, "평안반", 2, true);
        Student student = saveStudent("김학생", 4, true);

        adminService.assignStudentToYearClass(
                admin.getId(),
                source.getId(),
                new AssignYearClassStudentRequest(List.of(student.getId()))
        );

        YearClassStudentMoveBatchResponse moveResponse = adminService.moveStudentsToYearClass(
                admin.getId(),
                target.getId(),
                new MoveYearClassStudentRequest(List.of(student.getId()))
        );

        List<YearClassStudent> assignments = yearClassStudentRepository.findByYearIdAndStudentIdIn(
                year.getId(),
                List.of(student.getId())
        );

        assertEquals(List.of(student.getId()), moveResponse.movedStudentIds());
        assertTrue(moveResponse.skippedStudentIds().isEmpty());
        assertEquals(1, assignments.size());
        assertEquals(target.getId(), assignments.get(0).getYearClass().getId());
    }

    @Test
    @DisplayName("관리자 교사 생성은 audit_logs에 운영 로그를 남긴다")
    void createTeacher_writesAuditLog() {
        Teacher admin = saveTeacher("admin_audit_writer", TeacherRole.ADMIN, true);

        AdminTeacherResponse response = adminService.createTeacher(
                admin.getId(),
                new CreateTeacherRequest(
                        "teacher_audit_writer",
                        "Password123!",
                        "감사교사",
                        "010-2222-3333",
                        "19900101",
                        "TEACHER",
                        true
                )
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select actor_teacher_id, action_type, detail from audit_logs order by id desc limit 1"
        );

        assertEquals(admin.getId(), ((Number) row.get("actor_teacher_id")).longValue());
        assertEquals("CREATE_TEACHER", row.get("action_type"));
        assertTrue(String.valueOf(row.get("detail")).contains("teacherId=" + response.teacherId()));
    }

    private Teacher saveTeacher(String loginId, TeacherRole role, boolean active) {
        return teacherRepository.save(Teacher.builder()
                .loginId(loginId)
                .passwordHash("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhash")
                .teacherName(loginId + "_name")
                .contactNumber("010-0000-0000")
                .role(role)
                .active(active)
                .build());
    }

    private Student saveStudent(String name, Integer grade, boolean active) {
        return studentRepository.save(Student.builder()
                .studentName(name)
                .contactNumber("010-1111-1111")
                .active(active)
                .build());
    }

    private Year saveYear(Integer yearValue, boolean openToStudents, boolean openToTeachers, boolean active) {
        return yearRepository.save(Year.builder()
                .yearValue(yearValue)
                .openToStudents(openToStudents)
                .openToTeachers(openToTeachers)
                .active(active)
                .build());
    }

    private void createAuditLogsTableIfNeeded() {
        jdbcTemplate.execute("""
                create table if not exists audit_logs (
                    id bigint generated by default as identity primary key,
                    actor_teacher_id bigint,
                    action_type varchar(100),
                    detail varchar(1000),
                    created_at timestamp
                )
                """);
    }

    private YearClass saveYearClass(Integer yearValue, String className, Integer sortOrder, boolean active) {
        Year year = saveYear(yearValue, true, true, true);
        return saveYearClass(year, className, sortOrder, active);
    }

    private YearClass saveYearClass(Year year, String className, Integer sortOrder, boolean active) {
        return yearClassRepository.save(YearClass.builder()
                .year(year)
                .className(className)
                .sortOrder(sortOrder)
                .active(active)
                .build());
    }
}
