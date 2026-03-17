package com.church.qt.admin;

import com.church.qt.teacherapp.TeacherAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final TeacherAppService teacherAppService;
    private final AdminService adminService;

    @PostMapping("/years")
    public YearResponse createYear(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateYearRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.createYear(teacherId, request);
    }

    @PatchMapping("/years/{yearId}")
    public YearResponse updateYear(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearId,
            @RequestBody UpdateYearRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.updateYear(teacherId, yearId, request);
    }

    @PostMapping("/year-classes")
    public YearClassResponse createYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateYearClassRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.createYearClass(teacherId, request);
    }

    @PostMapping("/year-classes/{yearClassId}/teachers")
    public YearClassTeacherAssignmentBatchResponse assignTeacherToYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearClassId,
            @RequestBody AssignYearClassTeacherRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.assignTeacherToYearClass(teacherId, yearClassId, request);
    }

    @PostMapping("/year-classes/{yearClassId}/students")
    public YearClassStudentAssignmentBatchResponse assignStudentToYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearClassId,
            @RequestBody AssignYearClassStudentRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.assignStudentToYearClass(teacherId, yearClassId, request);
    }

    @DeleteMapping("/year-classes/{yearClassId}/teachers")
    public YearClassTeacherUnassignmentBatchResponse unassignTeacherFromYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearClassId,
            @RequestBody UnassignYearClassTeacherRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.unassignTeacherFromYearClass(teacherId, yearClassId, request);
    }

    @DeleteMapping("/year-classes/{yearClassId}/students")
    public YearClassStudentUnassignmentBatchResponse unassignStudentFromYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearClassId,
            @RequestBody UnassignYearClassStudentRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.unassignStudentFromYearClass(teacherId, yearClassId, request);
    }

    @PatchMapping("/year-classes/{targetYearClassId}/students/move")
    public YearClassStudentMoveBatchResponse moveStudentsToYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long targetYearClassId,
            @RequestBody MoveYearClassStudentRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.moveStudentsToYearClass(teacherId, targetYearClassId, request);
    }

    @GetMapping("/years")
    public List<YearResponse> getYears(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getYears(teacherId);
    }

    @GetMapping("/me")
    public AdminMeResponse getAdminMe(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getAdminMe(teacherId);
    }

    @GetMapping("/bootstrap")
    public AdminBootstrapResponse getAdminBootstrap(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String poolKeyword,
            @RequestParam(required = false) Boolean poolActiveOnly,
            @RequestParam(required = false) Integer poolLimit,
            @RequestParam(required = false) Integer auditLimit,
            @RequestParam(required = false) Integer auditOffset,
            @RequestParam(required = false) Long auditActorTeacherId,
            @RequestParam(required = false) String auditActionType,
            @RequestParam(required = false) String auditKeyword,
            @RequestParam(required = false) String auditFromAt,
            @RequestParam(required = false) String auditToAt,
            @RequestParam(required = false) Boolean includePools,
            @RequestParam(required = false) Boolean includeAuditLogs,
            @RequestParam(required = false) Boolean includeYearClasses,
            @RequestParam(required = false) Boolean includeActionTypes
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getAdminBootstrap(
                teacherId,
                year,
                poolKeyword,
                poolActiveOnly,
                poolLimit,
                auditLimit,
                auditOffset,
                auditActorTeacherId,
                auditActionType,
                auditKeyword,
                auditFromAt,
                auditToAt,
                includePools,
                includeAuditLogs,
                includeYearClasses,
                includeActionTypes
        );
    }

    @GetMapping("/year-classes")
    public List<AdminYearClassViewResponse> getYearClasses(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam Integer year
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getYearClasses(teacherId, year);
    }

    @GetMapping("/year-classes/{yearClassId}")
    public AdminYearClassViewResponse getYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearClassId
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getYearClass(teacherId, yearClassId);
    }

    @GetMapping("/audit-logs")
    public AdminAuditLogListResponse getAuditLogs(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(defaultValue = "100") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(required = false) Long actorTeacherId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fromAt,
            @RequestParam(required = false) String toAt
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getAuditLogs(teacherId, limit, offset, actorTeacherId, actionType, keyword, fromAt, toAt);
    }

    @GetMapping(value = "/audit-logs.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> downloadAuditLogsCsv(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(defaultValue = "1000") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(required = false) Long actorTeacherId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fromAt,
            @RequestParam(required = false) String toAt
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        String csv = adminService.getAuditLogsCsv(teacherId, limit, offset, actorTeacherId, actionType, keyword, fromAt, toAt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/audit-logs/action-types")
    public List<String> getAuditLogActionTypes(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getAuditLogActionTypes(teacherId);
    }

    @PatchMapping("/year-classes/{yearClassId}")
    public YearClassResponse updateYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearClassId,
            @RequestBody UpdateYearClassRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.updateYearClass(teacherId, yearClassId, request);
    }

    @GetMapping("/teachers")
    public AdminTeacherListResponse getTeachers(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getTeachers(teacherId, activeOnly, keyword, limit, offset);
    }

    @PostMapping("/teachers")
    public AdminTeacherResponse createTeacher(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateTeacherRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.createTeacher(teacherId, request);
    }

    @PatchMapping("/teachers/{targetTeacherId}")
    public AdminTeacherResponse updateTeacher(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long targetTeacherId,
            @RequestBody UpdateTeacherRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.updateTeacher(teacherId, targetTeacherId, request);
    }

    @GetMapping("/students")
    public AdminStudentListResponse getStudents(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.getStudents(teacherId, activeOnly, keyword, limit, offset);
    }

    @PostMapping("/students")
    public AdminStudentResponse createStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateStudentRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.createStudent(teacherId, request);
    }

    @PatchMapping("/students/{targetStudentId}")
    public AdminStudentResponse updateStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long targetStudentId,
            @RequestBody UpdateStudentRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.updateStudent(teacherId, targetStudentId, request);
    }
}
