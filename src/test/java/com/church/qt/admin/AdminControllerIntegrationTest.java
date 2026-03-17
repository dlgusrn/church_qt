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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final AtomicInteger YEAR_SEQUENCE = new AtomicInteger(3000);

    @LocalServerPort
    private int port;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private YearClassRepository yearClassRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("관리자 JWT로 관리자 연도 조회 API 호출이 성공한다")
    void getYears_withAdminToken_returnsOk() throws Exception {
        saveTeacher("admin_login", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_login", "pass1234");

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/years",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode(), "login failed body=" + response.body());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 관리자 연도 조회 API 호출 시 401을 반환한다")
    void getYears_withoutAuthorizationHeader_returnsUnauthorized() throws Exception {
        HttpResponse<String> response = request(
                "GET",
                "/api/admin/years",
                null,
                null
        );

        assertEquals(401, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("인증 헤더가 필요합니다."));
    }

    @Test
    @DisplayName("교사 JWT로 관리자 API 호출 시 권한 오류를 반환한다")
    void getYears_withTeacherToken_returnsBadRequest() throws Exception {
        saveTeacher("teacher_login", "pass1234", TeacherRole.TEACHER, true);
        String teacherToken = loginAndGetAccessToken("teacher_login", "pass1234");

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/years",
                null,
                teacherToken
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("관리자 권한이 없습니다."));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/me 호출 시 관리자 정보를 반환한다")
    void getAdminMe_withAdminToken_returnsAdminInfo() throws Exception {
        saveTeacher("admin_me", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_me", "pass1234");

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/me",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"loginId\":\"admin_me\""));
        assertTrue(response.body().contains("\"role\":\"ADMIN\""));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap 호출 시 me와 years를 함께 반환한다")
    void getAdminBootstrap_withAdminToken_returnsMeAndYears() throws Exception {
        saveTeacher("admin_bootstrap_api", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_api", "pass1234");
        saveYear(nextYearValue(), true, true, true);
        saveYear(nextYearValue(), true, true, true);

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"me\""));
        assertTrue(response.body().contains("\"schemaVersion\":\"v1\""));
        assertTrue(response.body().contains("\"generatedAt\""));
        assertTrue(response.body().contains("\"years\""));
        assertTrue(response.body().contains("\"auditLogActionTypes\""));
        assertTrue(response.body().contains("\"selectedYear\""));
        assertTrue(response.body().contains("\"yearClasses\""));
        assertTrue(response.body().contains("\"teachers\""));
        assertTrue(response.body().contains("\"students\""));
        assertTrue(response.body().contains("\"auditLogs\""));
        assertTrue(response.body().contains("\"pool\""));
        assertTrue(response.body().contains("\"audit\""));
        assertTrue(response.body().contains("\"actorTeacherId\":null"));
        assertTrue(response.body().contains("\"loginId\":\"admin_bootstrap_api\""));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap?year=YYYY 호출 시 해당 연도 반 목록을 반환한다")
    void getAdminBootstrap_withYearParam_returnsTargetYearClasses() throws Exception {
        saveTeacher("admin_bootstrap_year", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_year", "pass1234");
        Year yearA = saveYear(nextYearValue(), true, true, true);
        Year yearB = saveYear(nextYearValue(), true, true, true);
        saveYearClass(yearA, "A반", 1, true);
        saveYearClass(yearB, "B반", 1, true);

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap?year=" + yearA.getYearValue(),
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"yearClasses\""));
        assertTrue(response.body().contains("\"selectedYear\":" + yearA.getYearValue()));
        assertTrue(response.body().contains("\"className\":\"A반\""));
        assertTrue(!response.body().contains("\"className\":\"B반\""));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap?year=존재하지않음 호출 시 400을 반환한다")
    void getAdminBootstrap_withMissingYear_returnsBadRequest() throws Exception {
        saveTeacher("admin_bootstrap_missing", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_missing", "pass1234");
        saveYear(nextYearValue(), true, true, true);

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap?year=9999",
                null,
                adminToken
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("연도가 존재하지 않습니다."));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap 호출 시 pool 파라미터가 teachers/students에 반영된다")
    void getAdminBootstrap_withPoolParams_appliesPoolFilter() throws Exception {
        saveTeacher("admin_bootstrap_pool_api", "pass1234", TeacherRole.ADMIN, true);
        saveTeacher("pool_target_teacher_api", "pass1234", TeacherRole.TEACHER, true);
        saveStudent("pool_target_student_api", 4, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_pool_api", "pass1234");
        saveYear(nextYearValue(), true, true, true);

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap?poolKeyword=pool_target&poolActiveOnly=true&poolLimit=10",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"teachers\""));
        assertTrue(response.body().contains("\"students\""));
        assertTrue(response.body().contains("\"limit\":10"));
        assertTrue(response.body().contains("\"keyword\":\"pool_target\""));
        assertTrue(response.body().contains("pool_target_teacher_api"));
        assertTrue(response.body().contains("pool_target_student_api"));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap 호출 시 audit 파라미터가 auditLogs에 반영된다")
    void getAdminBootstrap_withAuditParams_appliesAuditFilter() throws Exception {
        saveTeacher("admin_bootstrap_audit_api", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_audit_api", "pass1234");
        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, ?)",
                1L, "MOVE_STUDENT", "bootstrap audit filter", "2026-03-08 09:00:00"
        );
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, ?)",
                2L, "MOVE_STUDENT", "bootstrap audit other actor", "2026-03-08 10:00:00"
        );
        saveYear(nextYearValue(), true, true, true);

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap?auditLimit=5&auditOffset=0&auditActorTeacherId=1&auditActionType=MOVE_STUDENT&auditKeyword=bootstrap&auditFromAt=2026-03-01T00:00&auditToAt=2026-03-31T23:59",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"auditLogs\""));
        assertTrue(response.body().contains("\"limit\":5"));
        assertTrue(response.body().contains("\"actorTeacherId\":1"));
        assertTrue(response.body().contains("\"audit\":{\"limit\":5,\"offset\":0,\"actorTeacherId\":1"));
        assertTrue(response.body().contains("\"actionType\":\"MOVE_STUDENT\""));
        assertTrue(response.body().contains("bootstrap audit filter"));
        assertTrue(!response.body().contains("bootstrap audit other actor"));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap 호출 시 audit_logs 테이블이 없어도 200을 반환한다")
    void getAdminBootstrap_withoutAuditTable_returnsOk() throws Exception {
        saveTeacher("admin_bootstrap_no_audit_api", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_no_audit_api", "pass1234");
        saveYear(nextYearValue(), true, true, true);
        jdbcTemplate.execute("drop table if exists audit_logs");

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"auditLogs\""));
        assertTrue(response.body().contains("\"totalCount\":0"));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap 호출 시 include 플래그로 pools/auditLogs를 비포함 처리할 수 있다")
    void getAdminBootstrap_withIncludeFlagsFalse_returnsEmptySections() throws Exception {
        saveTeacher("admin_bootstrap_exclude_api", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_exclude_api", "pass1234");
        saveYear(nextYearValue(), true, true, true);

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap?poolLimit=10&auditLimit=5&includePools=false&includeAuditLogs=false",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"pool\""));
        assertTrue(response.body().contains("\"audit\""));
        assertTrue(response.body().contains("\"limit\":10"));
        assertTrue(response.body().contains("\"limit\":5"));
        assertTrue(response.body().contains("\"items\":[]"));
    }

    @Test
    @DisplayName("관리자 JWT로 /api/admin/bootstrap 호출 시 includeYearClasses/includeActionTypes=false를 반영한다")
    void getAdminBootstrap_withSectionFlagsFalse_returnsEmptyYearClassesAndActionTypes() throws Exception {
        saveTeacher("admin_bootstrap_sections_api", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_bootstrap_sections_api", "pass1234");
        Year year = saveYear(nextYearValue(), true, true, true);
        saveYearClass(year, "섹션반", 1, true);
        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, ?)",
                1L, "ASSIGN_STUDENT", "section flag", "2026-03-08 09:00:00"
        );

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/bootstrap?year=" + year.getYearValue() + "&includeYearClasses=false&includeActionTypes=false",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"yearClasses\":[]"));
        assertTrue(response.body().contains("\"auditLogActionTypes\":[]"));
    }

    @Test
    @DisplayName("교사 JWT로 /api/admin/me 호출 시 권한 오류를 반환한다")
    void getAdminMe_withTeacherToken_returnsBadRequest() throws Exception {
        saveTeacher("teacher_me", "pass1234", TeacherRole.TEACHER, true);
        String teacherToken = loginAndGetAccessToken("teacher_me", "pass1234");

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/me",
                null,
                teacherToken
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("관리자 권한이 없습니다."));
    }

    @Test
    @DisplayName("유효하지 않은 JWT로 관리자 API 호출 시 토큰 오류를 반환한다")
    void getYears_withInvalidToken_returnsBadRequest() throws Exception {
        HttpResponse<String> response = request(
                "GET",
                "/api/admin/years",
                null,
                "invalid-token"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("유효하지 않은 인증 토큰입니다."));
    }

    @Test
    @DisplayName("학생을 같은 연도 다른 반에 중복 배정하면 400 에러를 반환한다")
    void assignStudent_duplicateInSameYear_returnsBadRequest() throws Exception {
        saveTeacher("admin_dup", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_dup", "pass1234");

        Year year = saveYear(nextYearValue(), true, true, true);
        YearClass classA = saveYearClass(year, "사랑반", 1, true);
        YearClass classB = saveYearClass(year, "믿음반", 2, true);
        Student student = saveStudent("중복학생", 5, true);

        String body = "{\"studentIds\":[" + student.getId() + "]}";
        HttpResponse<String> first = request(
                "POST",
                "/api/admin/year-classes/" + classA.getId() + "/students",
                body,
                adminToken
        );

        HttpResponse<String> second = request(
                "POST",
                "/api/admin/year-classes/" + classB.getId() + "/students",
                body,
                adminToken
        );

        assertEquals(200, first.statusCode());
        assertEquals(400, second.statusCode());
        assertTrue(second.body() != null && second.body().contains("이미 이 연도에 다른 반"));
    }

    @Test
    @DisplayName("교사 풀 조회 API는 limit/offset 기준 서버사이드 페이징 응답을 반환한다")
    void getTeachers_pagingResponse() throws Exception {
        saveTeacher("admin_page", "pass1234", TeacherRole.ADMIN, true);
        saveTeacher("teacher_a", "pass1234", TeacherRole.TEACHER, true);
        saveTeacher("teacher_b", "pass1234", TeacherRole.TEACHER, true);
        saveTeacher("teacher_c", "pass1234", TeacherRole.TEACHER, true);

        String adminToken = loginAndGetAccessToken("admin_page", "pass1234");

        HttpResponse<String> response = request(
                "GET",
                "/api/admin/teachers?activeOnly=true&keyword=teacher&limit=2&offset=0",
                null,
                adminToken
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body() != null && response.body().contains("\"totalCount\""));
        assertTrue(response.body().contains("\"limit\":2"));
        assertTrue(response.body().contains("\"offset\":0"));
        assertTrue(response.body().contains("\"items\""));
    }

    @Test
    @DisplayName("운영 로그 조회는 actionType/keyword/fromAt/toAt 필터와 CSV 다운로드를 지원한다")
    void auditLogs_filterAndCsvDownload() throws Exception {
        saveTeacher("admin_audit", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_audit", "pass1234");

        createAuditLogsTableIfNeeded();
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, ?)",
                1L, "ASSIGN_STUDENT", "student 11 assigned", "2026-03-01 10:00:00"
        );
        jdbcTemplate.update(
                "insert into audit_logs(actor_teacher_id, action_type, detail, created_at) values (?, ?, ?, ?)",
                1L, "MOVE_STUDENT", "student 11 moved", "2026-03-05 12:00:00"
        );

        HttpResponse<String> listResponse = request(
                "GET",
                "/api/admin/audit-logs?limit=10&offset=0&actionType=MOVE_STUDENT&keyword=moved&fromAt=2026-03-01T00:00&toAt=2026-03-31T23:59",
                null,
                adminToken
        );
        assertEquals(200, listResponse.statusCode());
        assertTrue(listResponse.body() != null && listResponse.body().contains("MOVE_STUDENT"));
        assertTrue(!listResponse.body().contains("ASSIGN_STUDENT"));

        HttpResponse<String> actionTypesResponse = request(
                "GET",
                "/api/admin/audit-logs/action-types",
                null,
                adminToken
        );
        assertEquals(200, actionTypesResponse.statusCode());
        assertTrue(actionTypesResponse.body() != null && actionTypesResponse.body().contains("ASSIGN_STUDENT"));
        assertTrue(actionTypesResponse.body().contains("MOVE_STUDENT"));

        HttpResponse<String> csvResponse = request(
                "GET",
                "/api/admin/audit-logs.csv?limit=10&offset=0",
                null,
                adminToken
        );
        assertEquals(200, csvResponse.statusCode());
        String csvBody = csvResponse.body() == null ? "" : csvResponse.body();
        String lowerCsvBody = csvBody.toLowerCase();
        assertTrue(lowerCsvBody.contains("action_type"));
        assertTrue(csvBody.contains("ASSIGN_STUDENT"));
    }

    @Test
    @DisplayName("학생 배정 undo 흐름(배정->해제->재배정)이 컨트롤러 레벨에서 정상 동작한다")
    void studentAssignUndoFlow_assignUnassignReassign() throws Exception {
        saveTeacher("admin_undo1", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_undo1", "pass1234");

        Year year = saveYear(nextYearValue(), true, true, true);
        YearClass yearClass = saveYearClass(year, "소망반", 1, true);
        Student student = saveStudent("되돌리기학생", 6, true);

        String body = "{\"studentIds\":[" + student.getId() + "]}";

        HttpResponse<String> assign = request(
                "POST",
                "/api/admin/year-classes/" + yearClass.getId() + "/students",
                body,
                adminToken
        );
        HttpResponse<String> unassign = request(
                "DELETE",
                "/api/admin/year-classes/" + yearClass.getId() + "/students",
                body,
                adminToken
        );
        HttpResponse<String> reassign = request(
                "POST",
                "/api/admin/year-classes/" + yearClass.getId() + "/students",
                body,
                adminToken
        );

        HttpResponse<String> detail = request(
                "GET",
                "/api/admin/year-classes/" + yearClass.getId(),
                null,
                adminToken
        );

        assertEquals(200, assign.statusCode());
        assertEquals(200, unassign.statusCode());
        assertEquals(200, reassign.statusCode());
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body() != null && detail.body().contains("\"studentId\":" + student.getId()));
    }

    @Test
    @DisplayName("학생 이동 undo 흐름(이동->원복)이 컨트롤러 레벨에서 정상 동작한다")
    void studentMoveUndoFlow_moveAndRollback() throws Exception {
        saveTeacher("admin_undo2", "pass1234", TeacherRole.ADMIN, true);
        String adminToken = loginAndGetAccessToken("admin_undo2", "pass1234");

        Year year = saveYear(nextYearValue(), true, true, true);
        YearClass source = saveYearClass(year, "진리반", 1, true);
        YearClass target = saveYearClass(year, "은혜반", 2, true);
        Student student = saveStudent("이동학생", 4, true);

        String studentBody = "{\"studentIds\":[" + student.getId() + "]}";

        HttpResponse<String> assignSource = request(
                "POST",
                "/api/admin/year-classes/" + source.getId() + "/students",
                studentBody,
                adminToken
        );
        HttpResponse<String> moveToTarget = request(
                "PATCH",
                "/api/admin/year-classes/" + target.getId() + "/students/move",
                studentBody,
                adminToken
        );
        HttpResponse<String> rollbackToSource = request(
                "PATCH",
                "/api/admin/year-classes/" + source.getId() + "/students/move",
                studentBody,
                adminToken
        );

        HttpResponse<String> sourceDetail = request(
                "GET",
                "/api/admin/year-classes/" + source.getId(),
                null,
                adminToken
        );
        HttpResponse<String> targetDetail = request(
                "GET",
                "/api/admin/year-classes/" + target.getId(),
                null,
                adminToken
        );

        assertEquals(200, assignSource.statusCode());
        assertEquals(200, moveToTarget.statusCode());
        assertEquals(200, rollbackToSource.statusCode());
        assertEquals(200, sourceDetail.statusCode());
        assertEquals(200, targetDetail.statusCode());
        assertTrue(sourceDetail.body() != null && sourceDetail.body().contains("\"studentId\":" + student.getId()));
        assertTrue(targetDetail.body() != null && !targetDetail.body().contains("\"studentId\":" + student.getId()));
    }

    private String loginAndGetAccessToken(String loginId, String password) throws Exception {
        String body = "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> response = request(
                "POST",
                "/api/teacher/login",
                body,
                null
        );

        assertEquals(200, response.statusCode());
        String responseBody = response.body() == null ? "" : response.body();
        Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(responseBody);
        assertTrue(matcher.find(), "login response should contain accessToken");
        return matcher.group(1);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> request(String method, String path, String body, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .header("Content-Type", "application/json");

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("PATCH".equals(method)) {
            builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("DELETE".equals(method)) {
            builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Teacher saveTeacher(String loginId, String rawPassword, TeacherRole role, boolean active) {
        return teacherRepository.save(Teacher.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .teacherName(loginId + "_name")
                .contactNumber("010-0000-0000")
                .role(role)
                .active(active)
                .build());
    }

    private Student saveStudent(String name, Integer grade, boolean active) {
        return studentRepository.save(Student.builder()
                .studentName(name)
                .schoolGrade(grade)
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

    private YearClass saveYearClass(Year year, String className, Integer sortOrder, boolean active) {
        return yearClassRepository.save(YearClass.builder()
                .year(year)
                .className(className)
                .sortOrder(sortOrder)
                .active(active)
                .build());
    }

    private int nextYearValue() {
        return YEAR_SEQUENCE.incrementAndGet();
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
}
