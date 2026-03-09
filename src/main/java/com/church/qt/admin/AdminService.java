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
import com.church.qt.domain.yearclass.YearClassTeacher;
import com.church.qt.domain.yearclass.YearClassTeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final String ADMIN_BOOTSTRAP_SCHEMA_VERSION = "v1";
    private static final List<String> AUDIT_DETAIL_COLUMN_CANDIDATES = List.of("detail", "description", "message");
    private static final List<String> AUDIT_ACTION_COLUMN_CANDIDATES = List.of("action_type", "action", "event_type");
    private static final List<String> AUDIT_CREATED_AT_COLUMN_CANDIDATES = List.of("created_at", "created_date", "occurred_at");
    private static final List<String> AUDIT_ACTOR_COLUMN_CANDIDATES = List.of("actor_teacher_id", "teacher_id", "actor_id");


    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final YearRepository yearRepository;
    private final YearClassRepository yearClassRepository;
    private final YearClassTeacherRepository yearClassTeacherRepository;
    private final YearClassStudentRepository yearClassStudentRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Transactional(readOnly = true)
    public void validateAdmin(Long teacherId) {
        getAndValidateAdminTeacher(teacherId);
    }

    @Transactional(readOnly = true)
    public AdminMeResponse getAdminMe(Long teacherId) {
        return AdminMeResponse.from(getAndValidateAdminTeacher(teacherId));
    }

    @Transactional(readOnly = true)
    public AdminBootstrapResponse getAdminBootstrap(
            Long teacherId,
            Integer yearValue,
            String poolKeyword,
            Boolean poolActiveOnly,
            Integer poolLimit,
            Integer auditLimit,
            Integer auditOffset,
            Long auditActorTeacherId,
            String auditActionType,
            String auditKeyword,
            String auditFromAt,
            String auditToAt,
            Boolean includePools,
            Boolean includeAuditLogs,
            Boolean includeYearClasses,
            Boolean includeActionTypes
    ) {
        Teacher adminTeacher = getAndValidateAdminTeacher(teacherId);
        List<YearResponse> years = yearRepository.findAllByOrderByYearValueDescIdDesc()
                .stream()
                .map(YearResponse::from)
                .toList();
        boolean effectiveIncludeYearClasses = includeYearClasses == null || includeYearClasses;
        boolean effectiveIncludeActionTypes = includeActionTypes == null || includeActionTypes;
        List<String> actionTypes = effectiveIncludeActionTypes ? loadAuditLogActionTypes() : List.of();
        boolean effectiveIncludePools = includePools == null || includePools;
        boolean effectiveIncludeAuditLogs = includeAuditLogs == null || includeAuditLogs;
        boolean effectivePoolActiveOnly = poolActiveOnly == null || poolActiveOnly;
        int validatedPoolLimit = (poolLimit == null || poolLimit < 1) ? 20 : Math.min(poolLimit, 200);
        int validatedAuditLimit = (auditLimit == null || auditLimit < 1) ? 100 : Math.min(auditLimit, 5000);
        int validatedAuditOffset = (auditOffset == null || auditOffset < 0) ? 0 : auditOffset;
        AdminTeacherListResponse teachers = effectiveIncludePools
                ? getTeachers(teacherId, effectivePoolActiveOnly, poolKeyword, poolLimit, 0)
                : new AdminTeacherListResponse(0L, validatedPoolLimit, 0, List.of());
        AdminStudentListResponse students = effectiveIncludePools
                ? getStudents(teacherId, effectivePoolActiveOnly, poolKeyword, poolLimit, 0)
                : new AdminStudentListResponse(0L, validatedPoolLimit, 0, List.of());
        AdminAuditLogListResponse auditLogs;
        if (!effectiveIncludeAuditLogs) {
            auditLogs = new AdminAuditLogListResponse(0L, validatedAuditLimit, validatedAuditOffset, List.of());
        } else {
            try {
                auditLogs = getAuditLogs(
                        teacherId,
                        auditLimit,
                        auditOffset,
                        auditActorTeacherId,
                        auditActionType,
                        auditKeyword,
                        auditFromAt,
                        auditToAt
                );
            } catch (IllegalArgumentException e) {
                auditLogs = new AdminAuditLogListResponse(0L, validatedAuditLimit, validatedAuditOffset, List.of());
            }
        }
        List<AdminYearClassViewResponse> yearClasses = List.of();
        Year targetYear = null;
        if (yearValue != null) {
            targetYear = yearRepository.findByYearValue(yearValue)
                    .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        } else if (!years.isEmpty()) {
            targetYear = yearRepository.findByYearValue(years.get(0).yearValue())
                    .orElse(null);
        }
        if (targetYear != null && effectiveIncludeYearClasses) {
            yearClasses = buildYearClassViews(
                    yearClassRepository.findByYearIdOrderBySortOrderAscIdAsc(targetYear.getId())
            );
        }
        AdminBootstrapPoolResponse pool = new AdminBootstrapPoolResponse(
                hasText(poolKeyword) ? poolKeyword.trim() : null,
                effectivePoolActiveOnly,
                teachers.limit(),
                teachers.offset()
        );
        AdminBootstrapAuditResponse audit = new AdminBootstrapAuditResponse(
                auditLogs.limit(),
                auditLogs.offset(),
                auditActorTeacherId,
                hasText(auditActionType) ? auditActionType.trim() : null,
                hasText(auditKeyword) ? auditKeyword.trim() : null,
                hasText(auditFromAt) ? normalizeDateTimeText(auditFromAt) : null,
                hasText(auditToAt) ? normalizeDateTimeText(auditToAt) : null
        );

        return new AdminBootstrapResponse(
                ADMIN_BOOTSTRAP_SCHEMA_VERSION,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                AdminMeResponse.from(adminTeacher),
                years,
                actionTypes,
                targetYear == null ? null : targetYear.getYearValue(),
                yearClasses,
                teachers,
                students,
                auditLogs,
                pool,
                audit
        );
    }

    @Transactional
    public YearResponse createYear(Long teacherId, CreateYearRequest request) {
        validateAdmin(teacherId);

        yearRepository.findByYearValue(request.yearValue())
                .ifPresent(y -> {
                    throw new IllegalArgumentException("이미 존재하는 연도입니다.");
                });

        Year year = Year.builder()
                .yearValue(request.yearValue())
                .openToStudents(request.openToStudents())
                .openToTeachers(request.openToTeachers())
                .active(request.active())
                .build();

        return YearResponse.from(yearRepository.save(year));
    }

    @Transactional
    public YearResponse updateYear(Long teacherId, Long yearId, UpdateYearRequest request) {
        validateAdmin(teacherId);

        Year year = yearRepository.findById(yearId)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        year.updateVisibility(request.openToStudents(), request.openToTeachers());
        year.updateActive(request.active());

        return YearResponse.from(year);
    }

    @Transactional
    public YearClassResponse createYearClass(Long teacherId, CreateYearClassRequest request) {
        validateAdmin(teacherId);

        Year year = yearRepository.findByYearValue(request.yearValue())
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        YearClass yearClass = YearClass.builder()
                .year(year)
                .className(request.className())
                .sortOrder(request.sortOrder())
                .active(request.active())
                .build();

        return YearClassResponse.from(yearClassRepository.save(yearClass));
    }

    @Transactional
    public YearClassTeacherAssignmentBatchResponse assignTeacherToYearClass(
            Long adminTeacherId,
            Long yearClassId,
            AssignYearClassTeacherRequest request
    ) {
        validateAdmin(adminTeacherId);

        YearClass yearClass = yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        if (request.teacherIds() == null || request.teacherIds().isEmpty()) {
            throw new IllegalArgumentException("teacherIds는 1개 이상이어야 합니다.");
        }

        Set<Long> uniqueTeacherIds = new LinkedHashSet<>(request.teacherIds());
        Map<Long, Teacher> teacherMap = loadTeacherMap(uniqueTeacherIds);

        Set<Long> existingTeacherIds = new LinkedHashSet<>();
        if (!uniqueTeacherIds.isEmpty()) {
            yearClassTeacherRepository.findByYearClassIdAndTeacherIdIn(yearClassId, new ArrayList<>(uniqueTeacherIds))
                    .forEach(item -> existingTeacherIds.add(item.getTeacher().getId()));
        }

        List<YearClassTeacher> toSave = new ArrayList<>();
        List<Long> assignedTeacherIds = new ArrayList<>();
        List<Long> skippedTeacherIds = new ArrayList<>();

        for (Long teacherId : uniqueTeacherIds) {
            if (existingTeacherIds.contains(teacherId)) {
                skippedTeacherIds.add(teacherId);
                continue;
            }

            toSave.add(YearClassTeacher.builder()
                    .yearClass(yearClass)
                    .teacher(teacherMap.get(teacherId))
                    .build());
            assignedTeacherIds.add(teacherId);
        }

        if (!toSave.isEmpty()) {
            yearClassTeacherRepository.saveAll(toSave);
        }

        return new YearClassTeacherAssignmentBatchResponse(
                yearClassId,
                assignedTeacherIds,
                skippedTeacherIds
        );
    }

    @Transactional
    public YearClassStudentAssignmentBatchResponse assignStudentToYearClass(
            Long adminTeacherId,
            Long yearClassId,
            AssignYearClassStudentRequest request
    ) {
        validateAdmin(adminTeacherId);

        YearClass yearClass = yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        if (request.studentIds() == null || request.studentIds().isEmpty()) {
            throw new IllegalArgumentException("studentIds는 1개 이상이어야 합니다.");
        }

        Set<Long> uniqueStudentIds = new LinkedHashSet<>(request.studentIds());
        Map<Long, Student> studentMap = loadStudentMap(uniqueStudentIds);

        Map<Long, YearClassStudent> existingByStudentId = new LinkedHashMap<>();
        if (!uniqueStudentIds.isEmpty()) {
            yearClassStudentRepository.findByYearIdAndStudentIdIn(
                            yearClass.getYear().getId(),
                            new ArrayList<>(uniqueStudentIds)
                    )
                    .forEach(item -> existingByStudentId.put(item.getStudent().getId(), item));
        }

        List<YearClassStudent> toSave = new ArrayList<>();
        List<Long> assignedStudentIds = new ArrayList<>();
        List<Long> skippedStudentIds = new ArrayList<>();

        for (Long studentId : uniqueStudentIds) {
            YearClassStudent existing = existingByStudentId.get(studentId);
            if (existing != null) {
                if (!existing.getYearClass().getId().equals(yearClassId)) {
                    throw new IllegalArgumentException("해당 학생은 이미 이 연도에 다른 반에 배정되어 있습니다. studentId=" + studentId);
                }
                skippedStudentIds.add(studentId);
                continue;
            }

            toSave.add(YearClassStudent.builder()
                    .year(yearClass.getYear())
                    .yearClass(yearClass)
                    .student(studentMap.get(studentId))
                    .build());
            assignedStudentIds.add(studentId);
        }

        if (!toSave.isEmpty()) {
            yearClassStudentRepository.saveAll(toSave);
        }

        return new YearClassStudentAssignmentBatchResponse(
                yearClass.getYear().getId(),
                yearClassId,
                assignedStudentIds,
                skippedStudentIds
        );
    }

    @Transactional
    public YearClassTeacherUnassignmentBatchResponse unassignTeacherFromYearClass(
            Long adminTeacherId,
            Long yearClassId,
            UnassignYearClassTeacherRequest request
    ) {
        validateAdmin(adminTeacherId);

        yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        if (request.teacherIds() == null || request.teacherIds().isEmpty()) {
            throw new IllegalArgumentException("teacherIds는 1개 이상이어야 합니다.");
        }

        Set<Long> uniqueTeacherIds = new LinkedHashSet<>(request.teacherIds());
        List<YearClassTeacher> existing = yearClassTeacherRepository.findByYearClassIdAndTeacherIdIn(
                yearClassId,
                new ArrayList<>(uniqueTeacherIds)
        );

        Set<Long> existingIds = new LinkedHashSet<>();
        existing.forEach(item -> existingIds.add(item.getTeacher().getId()));

        List<Long> removedTeacherIds = new ArrayList<>();
        List<Long> skippedTeacherIds = new ArrayList<>();
        for (Long teacherId : uniqueTeacherIds) {
            if (existingIds.contains(teacherId)) {
                removedTeacherIds.add(teacherId);
            } else {
                skippedTeacherIds.add(teacherId);
            }
        }

        if (!existing.isEmpty()) {
            yearClassTeacherRepository.deleteAll(existing);
        }

        return new YearClassTeacherUnassignmentBatchResponse(
                yearClassId,
                removedTeacherIds,
                skippedTeacherIds
        );
    }

    @Transactional
    public YearClassStudentUnassignmentBatchResponse unassignStudentFromYearClass(
            Long adminTeacherId,
            Long yearClassId,
            UnassignYearClassStudentRequest request
    ) {
        validateAdmin(adminTeacherId);

        yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        if (request.studentIds() == null || request.studentIds().isEmpty()) {
            throw new IllegalArgumentException("studentIds는 1개 이상이어야 합니다.");
        }

        Set<Long> uniqueStudentIds = new LinkedHashSet<>(request.studentIds());
        List<YearClassStudent> existing = yearClassStudentRepository.findByYearClassIdAndStudentIdIn(
                yearClassId,
                new ArrayList<>(uniqueStudentIds)
        );

        Set<Long> existingIds = new LinkedHashSet<>();
        existing.forEach(item -> existingIds.add(item.getStudent().getId()));

        List<Long> removedStudentIds = new ArrayList<>();
        List<Long> skippedStudentIds = new ArrayList<>();
        for (Long studentId : uniqueStudentIds) {
            if (existingIds.contains(studentId)) {
                removedStudentIds.add(studentId);
            } else {
                skippedStudentIds.add(studentId);
            }
        }

        if (!existing.isEmpty()) {
            yearClassStudentRepository.deleteAll(existing);
        }

        return new YearClassStudentUnassignmentBatchResponse(
                yearClassId,
                removedStudentIds,
                skippedStudentIds
        );
    }

    @Transactional
    public YearClassStudentMoveBatchResponse moveStudentsToYearClass(
            Long adminTeacherId,
            Long targetYearClassId,
            MoveYearClassStudentRequest request
    ) {
        validateAdmin(adminTeacherId);

        YearClass targetYearClass = yearClassRepository.findById(targetYearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        if (request.studentIds() == null || request.studentIds().isEmpty()) {
            throw new IllegalArgumentException("studentIds는 1개 이상이어야 합니다.");
        }

        Set<Long> uniqueStudentIds = new LinkedHashSet<>(request.studentIds());
        Map<Long, YearClassStudent> existingByStudentId = new LinkedHashMap<>();
        yearClassStudentRepository.findByYearIdAndStudentIdIn(
                        targetYearClass.getYear().getId(),
                        new ArrayList<>(uniqueStudentIds)
                )
                .forEach(item -> existingByStudentId.put(item.getStudent().getId(), item));

        List<Long> movedStudentIds = new ArrayList<>();
        List<Long> skippedStudentIds = new ArrayList<>();

        for (Long studentId : uniqueStudentIds) {
            YearClassStudent existing = existingByStudentId.get(studentId);
            if (existing == null) {
                throw new IllegalArgumentException("해당 학생은 이 연도에 배정 정보가 없습니다. studentId=" + studentId);
            }
            if (existing.getYearClass().getId().equals(targetYearClassId)) {
                skippedStudentIds.add(studentId);
                continue;
            }

            existing.moveToYearClass(targetYearClass);
            movedStudentIds.add(studentId);
        }

        return new YearClassStudentMoveBatchResponse(
                targetYearClassId,
                movedStudentIds,
                skippedStudentIds
        );
    }

    @Transactional(readOnly = true)
    public List<YearResponse> getYears(Long adminTeacherId) {
        validateAdmin(adminTeacherId);

        return yearRepository.findAllByOrderByYearValueDescIdDesc()
                .stream()
                .map(YearResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminYearClassViewResponse> getYearClasses(Long adminTeacherId, Integer yearValue) {
        validateAdmin(adminTeacherId);

        Year year = yearRepository.findByYearValue(yearValue)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        List<YearClass> yearClasses = yearClassRepository.findByYearIdOrderBySortOrderAscIdAsc(year.getId());
        return buildYearClassViews(yearClasses);
    }

    @Transactional(readOnly = true)
    public AdminYearClassViewResponse getYearClass(Long adminTeacherId, Long yearClassId) {
        validateAdmin(adminTeacherId);

        YearClass yearClass = yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        return buildYearClassViews(List.of(yearClass))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("반 조회에 실패했습니다."));
    }

    @Transactional(readOnly = true)
    public AdminAuditLogListResponse getAuditLogs(
            Long adminTeacherId,
            Integer limit,
            Integer offset,
            Long actorTeacherId,
            String actionType,
            String keyword,
            String fromAt,
            String toAt
    ) {
        validateAdmin(adminTeacherId);

        int validatedLimit = (limit == null || limit < 1) ? 100 : Math.min(limit, 500);
        int validatedOffset = (offset == null || offset < 0) ? 0 : offset;

        List<String> columns = getAuditLogColumns();
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("audit_logs 테이블 컬럼을 조회할 수 없습니다.");
        }

        Set<String> columnSet = columns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        AuditLogSchema schema = resolveAuditLogSchema(columnSet);

        StringBuilder where = new StringBuilder(" where 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (actorTeacherId != null && schema.actorTeacherIdColumn() != null) {
            where.append(" and ").append(schema.actorTeacherIdColumn()).append(" = :actorTeacherId ");
            params.addValue("actorTeacherId", actorTeacherId);
        }
        if (hasText(actionType) && schema.actionTypeColumn() != null) {
            where.append(" and ").append(schema.actionTypeColumn()).append(" = :actionType ");
            params.addValue("actionType", actionType.trim());
        }
        if (hasText(keyword) && schema.detailColumn() != null) {
            where.append(" and ").append(schema.detailColumn()).append(" like :keyword ");
                params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (schema.createdAtColumn() != null) {
            if (hasText(fromAt)) {
                where.append(" and ").append(schema.createdAtColumn()).append(" >= :fromAt ");
                params.addValue("fromAt", normalizeDateTimeText(fromAt));
            }
            if (hasText(toAt)) {
                where.append(" and ").append(schema.createdAtColumn()).append(" <= :toAt ");
                params.addValue("toAt", normalizeDateTimeText(toAt));
            }
        }

        String orderBy = pickOrderBy(schema);

        String countSql = "select count(*) from audit_logs " + where;
        Long totalCount = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);
        if (totalCount == null) {
            totalCount = 0L;
        }

        params.addValue("limit", validatedLimit);
        params.addValue("offset", validatedOffset);
        String selectSql = "select * from audit_logs " + where + orderBy + " limit :limit offset :offset";
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(selectSql, params);

        List<AdminAuditLogRowResponse> items = rows.stream()
                .map(row -> new AdminAuditLogRowResponse(normalizeAuditLogRow(row, schema)))
                .toList();

        return new AdminAuditLogListResponse(
                totalCount,
                validatedLimit,
                validatedOffset,
                items
        );
    }

    @Transactional
    public YearClassResponse updateYearClass(
            Long adminTeacherId,
            Long yearClassId,
            UpdateYearClassRequest request
    ) {
        validateAdmin(adminTeacherId);

        YearClass yearClass = yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));

        yearClass.updateInfo(request.className(), request.sortOrder(), request.active());
        return YearClassResponse.from(yearClass);
    }

    @Transactional(readOnly = true)
    public AdminTeacherListResponse getTeachers(
            Long adminTeacherId,
            Boolean activeOnly,
            String keyword,
            Integer limit,
            Integer offset
    ) {
        validateAdmin(adminTeacherId);

        int validatedLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 200);
        int validatedOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageIndex = validatedOffset / validatedLimit;

        String normalizedKeyword = hasText(keyword) ? keyword.trim() : null;
        Page<Teacher> page = teacherRepository.search(
                Boolean.TRUE.equals(activeOnly),
                normalizedKeyword,
                PageRequest.of(pageIndex, validatedLimit)
        );

        List<AdminTeacherResponse> items = page.getContent().stream()
                .map(AdminTeacherResponse::from)
                .toList();

        return new AdminTeacherListResponse(
                page.getTotalElements(),
                validatedLimit,
                validatedOffset,
                items
        );
    }

    @Transactional(readOnly = true)
    public AdminStudentListResponse getStudents(
            Long adminTeacherId,
            Boolean activeOnly,
            String keyword,
            Integer limit,
            Integer offset
    ) {
        validateAdmin(adminTeacherId);

        int validatedLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 200);
        int validatedOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageIndex = validatedOffset / validatedLimit;

        String normalizedKeyword = hasText(keyword) ? keyword.trim() : null;
        Page<Student> page = studentRepository.search(
                Boolean.TRUE.equals(activeOnly),
                normalizedKeyword,
                PageRequest.of(pageIndex, validatedLimit)
        );

        List<AdminStudentResponse> items = page.getContent().stream()
                .map(AdminStudentResponse::from)
                .toList();

        return new AdminStudentListResponse(
                page.getTotalElements(),
                validatedLimit,
                validatedOffset,
                items
        );
    }

    @Transactional(readOnly = true)
    public String getAuditLogsCsv(
            Long adminTeacherId,
            Integer limit,
            Integer offset,
            Long actorTeacherId,
            String actionType,
            String keyword,
            String fromAt,
            String toAt
    ) {
        AdminAuditLogListResponse response = getAuditLogs(
                adminTeacherId,
                limit,
                offset,
                actorTeacherId,
                actionType,
                keyword,
                fromAt,
                toAt
        );

        List<AdminAuditLogRowResponse> items = response.items();
        if (items.isEmpty()) {
            return "no_data\n";
        }

        List<String> headers = buildCsvHeaders(items.get(0).data());
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append("\n");

        for (AdminAuditLogRowResponse row : items) {
            List<String> columns = new ArrayList<>();
            for (String header : headers) {
                Object value = row.data().get(header);
                columns.add(escapeCsv(value == null ? "" : String.valueOf(value)));
            }
            csv.append(String.join(",", columns)).append("\n");
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<String> getAuditLogActionTypes(Long adminTeacherId) {
        validateAdmin(adminTeacherId);
        return loadAuditLogActionTypes();
    }

    private List<String> loadAuditLogActionTypes() {
        List<String> columns = getAuditLogColumns();
        Set<String> columnSet = columns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        AuditLogSchema schema = resolveAuditLogSchema(columnSet);
        if (schema.actionTypeColumn() == null) {
            return List.of();
        }

        return namedParameterJdbcTemplate.queryForList(
                "select distinct " + schema.actionTypeColumn() +
                        " from audit_logs where " + schema.actionTypeColumn() + " is not null and trim(" + schema.actionTypeColumn() + ") <> '' order by " + schema.actionTypeColumn() + " asc",
                new HashMap<>(),
                String.class
        );
    }

    private List<AdminYearClassViewResponse> buildYearClassViews(List<YearClass> yearClasses) {
        if (yearClasses.isEmpty()) {
            return List.of();
        }

        List<Long> yearClassIds = yearClasses.stream()
                .map(YearClass::getId)
                .toList();

        Map<Long, List<AdminYearClassTeacherResponse>> teacherMapByYearClassId = new LinkedHashMap<>();
        yearClassTeacherRepository.findByYearClassIdInOrderByYearClassIdAscTeacherTeacherNameAsc(yearClassIds)
                .forEach(item -> teacherMapByYearClassId
                        .computeIfAbsent(item.getYearClass().getId(), key -> new ArrayList<>())
                        .add(AdminYearClassTeacherResponse.from(item)));

        Map<Long, List<AdminYearClassStudentResponse>> studentMapByYearClassId = new LinkedHashMap<>();
        yearClassStudentRepository.findByYearClassIdInOrderByYearClassIdAscStudentSchoolGradeDescStudentStudentNameAsc(yearClassIds)
                .forEach(item -> studentMapByYearClassId
                        .computeIfAbsent(item.getYearClass().getId(), key -> new ArrayList<>())
                        .add(AdminYearClassStudentResponse.from(item)));

        List<AdminYearClassViewResponse> responses = new ArrayList<>();
        for (YearClass yearClass : yearClasses) {
            responses.add(AdminYearClassViewResponse.of(
                    yearClass,
                    teacherMapByYearClassId.getOrDefault(yearClass.getId(), List.of()),
                    studentMapByYearClassId.getOrDefault(yearClass.getId(), List.of())
            ));
        }
        return responses;
    }

    private List<String> getAuditLogColumns() {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where lower(table_name) = 'audit_logs'
                  and (
                        table_schema = database()
                        or upper(table_schema) = 'PUBLIC'
                  )
                order by ordinal_position
                """,
                new HashMap<>()
        );
        return rows.stream()
                .map(row -> (String) row.get("column_name"))
                .toList();
    }

    private String pickOrderBy(AuditLogSchema schema) {
        if (schema.orderByColumn() != null) {
            return " order by " + schema.orderByColumn() + " desc ";
        }
        if (schema.idColumn() != null) {
            return " order by id desc ";
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private AuditLogSchema resolveAuditLogSchema(Set<String> columnSet) {
        return new AuditLogSchema(
                pickExistingColumn(AUDIT_ACTOR_COLUMN_CANDIDATES, columnSet),
                pickExistingColumn(AUDIT_ACTION_COLUMN_CANDIDATES, columnSet),
                pickExistingColumn(AUDIT_DETAIL_COLUMN_CANDIDATES, columnSet),
                pickExistingColumn(AUDIT_CREATED_AT_COLUMN_CANDIDATES, columnSet),
                columnSet.contains("id") ? "id" : null
        );
    }

    private String pickExistingColumn(List<String> candidates, Set<String> columnSet) {
        for (String candidate : candidates) {
            if (columnSet.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> normalizeAuditLogRow(Map<String, Object> row, AuditLogSchema schema) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(row);

        if (schema.idColumn() != null) {
            normalized.put("id", getValueIgnoreCase(row, schema.idColumn()));
        }
        if (schema.actorTeacherIdColumn() != null) {
            normalized.put("actor_teacher_id", getValueIgnoreCase(row, schema.actorTeacherIdColumn()));
        }
        if (schema.actionTypeColumn() != null) {
            normalized.put("action_type", getValueIgnoreCase(row, schema.actionTypeColumn()));
        }
        if (schema.detailColumn() != null) {
            normalized.put("detail", getValueIgnoreCase(row, schema.detailColumn()));
        }
        if (schema.createdAtColumn() != null) {
            normalized.put("created_at", getValueIgnoreCase(row, schema.createdAtColumn()));
        }
        return normalized;
    }

    private Object getValueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<String> buildCsvHeaders(Map<String, Object> firstRow) {
        List<String> headers = new ArrayList<>();
        List<String> canonical = List.of("id", "actor_teacher_id", "action_type", "detail", "created_at");
        for (String key : canonical) {
            if (containsKeyIgnoreCase(firstRow, key)) {
                headers.add(key);
            }
        }
        for (String key : firstRow.keySet()) {
            if (!containsStringIgnoreCase(headers, key)) {
                headers.add(key);
            }
        }
        return headers;
    }

    private boolean containsKeyIgnoreCase(Map<String, Object> row, String key) {
        for (String rowKey : row.keySet()) {
            if (rowKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsStringIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private String escapeCsv(String value) {
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String normalizeDateTimeText(String value) {
        String normalized = value.trim().replace("T", " ");
        if (normalized.length() == 16) {
            return normalized + ":00";
        }
        return normalized;
    }

    private Map<Long, Teacher> loadTeacherMap(Set<Long> teacherIds) {
        List<Teacher> teachers = teacherRepository.findAllById(teacherIds);
        if (teachers.size() != teacherIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 교사가 포함되어 있습니다.");
        }

        Map<Long, Teacher> teacherMap = new LinkedHashMap<>();
        for (Teacher teacher : teachers) {
            if (!teacher.getActive()) {
                throw new IllegalArgumentException("비활성화된 교사는 배정할 수 없습니다.");
            }
            teacherMap.put(teacher.getId(), teacher);
        }
        return teacherMap;
    }

    private Map<Long, Student> loadStudentMap(Set<Long> studentIds) {
        List<Student> students = studentRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 학생이 포함되어 있습니다.");
        }

        Map<Long, Student> studentMap = new LinkedHashMap<>();
        for (Student student : students) {
            if (!student.getActive()) {
                throw new IllegalArgumentException("비활성화된 학생은 배정할 수 없습니다.");
            }
            studentMap.put(student.getId(), student);
        }
        return studentMap;
    }

    private Teacher getAndValidateAdminTeacher(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));

        if (!teacher.getActive()) {
            throw new IllegalArgumentException("비활성화된 교사입니다.");
        }

        if (teacher.getRole() != TeacherRole.ADMIN) {
            throw new IllegalArgumentException("관리자 권한이 없습니다.");
        }
        return teacher;
    }

    private record AuditLogSchema(
            String actorTeacherIdColumn,
            String actionTypeColumn,
            String detailColumn,
            String createdAtColumn,
            String idColumn
    ) {
        String orderByColumn() {
            return createdAtColumn != null ? createdAtColumn : idColumn;
        }
    }
}
