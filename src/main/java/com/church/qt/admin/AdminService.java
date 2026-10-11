package com.church.qt.admin;

import com.church.qt.common.AuditLogService;
import com.church.qt.common.ChangePasswordRequest;
import com.church.qt.common.PasswordPolicyValidator;
import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.fruit.FruitAdjustmentRepository;
import com.church.qt.domain.fruit.FruitType;
import com.church.qt.domain.fruit.FruitReset;
import com.church.qt.domain.fruit.FruitResetRepository;
import com.church.qt.domain.fruit.FruitFestival;
import com.church.qt.domain.fruit.FruitFestivalRepository;
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
import com.church.qt.domain.yearstudent.YearStudent;
import com.church.qt.domain.yearstudent.YearStudentRepository;
import com.church.qt.domain.yearteacher.YearTeacher;
import com.church.qt.domain.yearteacher.YearTeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
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
    private final YearTeacherRepository yearTeacherRepository;
    private final YearStudentRepository yearStudentRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final AuditLogService auditLogService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final DevotionCheckRepository devotionCheckRepository;
    private final FruitAdjustmentRepository fruitAdjustmentRepository;
    private final FruitResetRepository fruitResetRepository;
    private final FruitFestivalRepository fruitFestivalRepository;

    @Transactional(readOnly = true)
    public void validateAdmin(Long teacherId) {
        getAndValidateAdminTeacher(teacherId);
    }

    @Transactional(readOnly = true)
    public AdminMeResponse getAdminMe(Long teacherId) {
        return AdminMeResponse.from(getAndValidateAdminTeacher(teacherId));
    }

    @Transactional
    public void changeAdminPassword(Long teacherId, ChangePasswordRequest request) {
        Teacher adminTeacher = getAndValidateAdminTeacher(teacherId);
        String currentPassword = requireText(request.currentPassword(), "currentPassword");
        String newPassword = requireText(request.newPassword(), "newPassword");
        String confirmPassword = requireText(request.confirmPassword(), "confirmPassword");

        if (!passwordEncoder.matches(currentPassword, adminTeacher.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        passwordPolicyValidator.validate(newPassword);

        adminTeacher.changePassword(passwordEncoder.encode(newPassword));
        recordAuditLog(
                teacherId,
                "CHANGE_PASSWORD",
                "teacherId=" + adminTeacher.getId() + ", loginId=" + adminTeacher.getLoginId() + ", scope=admin"
        );
    }

    @Transactional
    public void resetFruitCounts(Long teacherId, FruitResetRequest request) {
        Teacher admin = getAndValidateAdminTeacher(teacherId);
        LocalDate resetDate = request == null || request.resetDate() == null ? LocalDate.now() : request.resetDate();
        Year year = yearRepository.findByYearValue(resetDate.getYear())
                .orElseThrow(() -> new IllegalArgumentException("초기화 날짜의 운영 연도를 먼저 등록하세요."));
        if (fruitResetRepository.existsByResetDate(resetDate)) throw new IllegalArgumentException("이미 해당 날짜에 초기화한 이력이 있습니다.");
        fruitResetRepository.save(FruitReset.builder().year(year).resetDate(resetDate).resetByTeacher(admin).build());
        year.resetFruitCounts(resetDate);
        recordAuditLog(teacherId, "RESET_FRUIT_COUNTS", "resetDate=" + resetDate);
    }

    @Transactional(readOnly = true)
    public List<FruitRoundResponse> getFruitRounds(Long teacherId) {
        validateAdmin(teacherId);
        List<Year> years = yearRepository.findAllByOrderByYearValueDescIdDesc();
        if (years.isEmpty()) return List.of();
        List<LocalDate> resets = new ArrayList<>(fruitResetRepository.findAllByOrderByResetDateAsc().stream()
                .map(FruitReset::getResetDate)
                .toList());
        years.stream()
                .map(Year::getFruitResetDate)
                .filter(java.util.Objects::nonNull)
                .forEach(resets::add);
        resets = resets.stream().distinct().sorted().toList();
        LocalDate from = LocalDate.of(years.stream().mapToInt(Year::getYearValue).min().orElseThrow(), 1, 1);
        LocalDate today = LocalDate.now();
        List<FruitRoundResponse> rounds = new ArrayList<>();
        for (LocalDate reset : resets) { rounds.add(new FruitRoundResponse(rounds.size() + 1, from, reset.minusDays(1), false)); from = reset; }
        rounds.add(new FruitRoundResponse(rounds.size() + 1, from, today, true));
        return rounds;
    }

    @Transactional(readOnly = true)
    public List<FruitRoundStudentResponse> getFruitRoundResults(Long teacherId, LocalDate from, LocalDate to) {
        validateAdmin(teacherId);
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("회차 기간을 확인하세요.");
        Map<Long, FruitRoundStudentTotals> totalsByStudentId = new LinkedHashMap<>();
        for (Year year : yearRepository.findAllByOrderByYearValueDescIdDesc()) {
            LocalDate yearStart = LocalDate.of(year.getYearValue(), 1, 1);
            LocalDate yearEnd = LocalDate.of(year.getYearValue(), 12, 31);
            LocalDate periodFrom = from.isAfter(yearStart) ? from : yearStart;
            LocalDate periodTo = to.isBefore(yearEnd) ? to : yearEnd;
            if (periodTo.isBefore(periodFrom)) continue;
            for (YearClassStudent membership : yearClassStudentRepository.findByYearId(year.getId())) {
                Long studentId = membership.getStudent().getId();
                long qtCount = devotionCheckRepository.countQtByYearStudentAndDateBetween(year.getId(), studentId, periodFrom, periodTo);
                long attitudeCount = devotionCheckRepository.countAttitudeByYearStudentAndDateBetween(year.getId(), studentId, periodFrom, periodTo)
                        + fruitAdjustmentRepository.sumAmount(year.getId(), studentId, FruitType.ATTITUDE, periodFrom, periodTo);
                long noteCount = devotionCheckRepository.sumNoteCountByYearStudentAndDateBetween(year.getId(), studentId, periodFrom, periodTo);
                long deductionCount = fruitAdjustmentRepository.sumAmount(year.getId(), studentId, FruitType.TOTAL, periodFrom, periodTo);
                totalsByStudentId.computeIfAbsent(studentId, ignored -> new FruitRoundStudentTotals(studentId, membership.getStudent().getStudentName()))
                        .add(qtCount, noteCount, attitudeCount, deductionCount);
            }
        }
        return totalsByStudentId.values().stream()
                .map(FruitRoundStudentTotals::toResponse)
                .sorted(java.util.Comparator.comparingLong(FruitRoundStudentResponse::totalCount).reversed().thenComparing(FruitRoundStudentResponse::studentName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FruitFestivalResponse> getFruitFestivals(Long teacherId, Long yearId) {
        validateAdmin(teacherId);
        Year year = yearRepository.findById(yearId).orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        List<FruitFestivalResponse> festivals = new ArrayList<>();
        if (year.getFruitFestivalDate() != null) festivals.add(new FruitFestivalResponse(null, year.getFruitFestivalDate()));
        fruitFestivalRepository.findByYearIdOrderByFestivalDateAsc(yearId).stream().map(FruitFestivalResponse::from).forEach(festivals::add);
        return festivals.stream().collect(Collectors.toMap(FruitFestivalResponse::festivalDate, item -> item, (left, right) -> left, LinkedHashMap::new)).values().stream().toList();
    }

    @Transactional
    public FruitFestivalResponse createFruitFestival(Long teacherId, Long yearId, FruitFestivalRequest request) {
        getAndValidateAdminTeacher(teacherId);
        Year year = yearRepository.findById(yearId).orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        if (request == null || request.festivalDate() == null) throw new IllegalArgumentException("열매잔치 날짜를 선택하세요.");
        if (request.festivalDate().getYear() != year.getYearValue()) throw new IllegalArgumentException("열매잔치 날짜는 해당 연도 안에서 선택하세요.");
        if (request.festivalDate().equals(year.getFruitFestivalDate()) || fruitFestivalRepository.existsByYearIdAndFestivalDate(yearId, request.festivalDate())) throw new IllegalArgumentException("이미 등록된 열매잔치 날짜입니다.");
        FruitFestival festival = fruitFestivalRepository.save(FruitFestival.builder().year(year).festivalDate(request.festivalDate()).build());
        recordAuditLog(teacherId, "CREATE_FRUIT_FESTIVAL", "yearValue=" + year.getYearValue() + ", festivalDate=" + request.festivalDate());
        return FruitFestivalResponse.from(festival);
    }

    @Transactional
    public void deleteFruitFestival(Long teacherId, Long yearId, LocalDate festivalDate) {
        getAndValidateAdminTeacher(teacherId);
        Year year = yearRepository.findById(yearId).orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        if (festivalDate == null) throw new IllegalArgumentException("열매잔치 날짜를 선택하세요.");
        if (festivalDate.equals(year.getFruitFestivalDate())) {
            year.updateFruitFestivalDate(null);
        } else {
            FruitFestival festival = fruitFestivalRepository.findByYearIdAndFestivalDate(yearId, festivalDate)
                    .orElseThrow(() -> new IllegalArgumentException("열매잔치 일정이 존재하지 않습니다."));
            fruitFestivalRepository.delete(festival);
        }
        recordAuditLog(teacherId, "DELETE_FRUIT_FESTIVAL", "yearValue=" + year.getYearValue() + ", festivalDate=" + festivalDate);
    }

    @Transactional(readOnly = true)
    public List<FruitAdjustmentResponse> getFruitAdjustments(Long teacherId) {
        validateAdmin(teacherId);
        return fruitAdjustmentRepository.findAllAdjustments().stream().map(FruitAdjustmentResponse::from).toList();
    }

    @Transactional
    public void cancelFruitAdjustment(Long teacherId, Long adjustmentId) {
        getAndValidateAdminTeacher(teacherId);
        com.church.qt.domain.fruit.FruitAdjustment adjustment = fruitAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("열매 추가·차감 내역이 존재하지 않습니다."));
        recordAuditLog(teacherId, "CANCEL_FRUIT_ADJUSTMENT", "adjustmentId=" + adjustment.getId() + ", yearValue=" + adjustment.getYear().getYearValue() + ", studentId=" + adjustment.getStudent().getId() + ", type=" + adjustment.getFruitType() + ", amount=" + adjustment.getAmount());
        fruitAdjustmentRepository.delete(adjustment);
    }

    private static class FruitRoundStudentTotals {
        private final Long studentId;
        private final String studentName;
        private long qtCount;
        private long noteCount;
        private long attitudeCount;
        private long deductionCount;

        private FruitRoundStudentTotals(Long studentId, String studentName) {
            this.studentId = studentId;
            this.studentName = studentName;
        }

        private void add(long qt, long note, long attitude, long deduction) {
            qtCount += qt;
            noteCount += note;
            attitudeCount += attitude;
            deductionCount += deduction;
        }

        private FruitRoundStudentResponse toResponse() {
            return new FruitRoundStudentResponse(studentId, studentName, qtCount, noteCount, attitudeCount, deductionCount,
                    qtCount + noteCount + attitudeCount + deductionCount);
        }
    }

    @Transactional
    public YearResponse updateFruitFestivalDate(Long teacherId, Long yearId, LocalDate festivalDate) {
        Year year = yearRepository.findById(yearId).orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        getAndValidateAdminTeacher(teacherId);
        if (festivalDate != null && festivalDate.getYear() != year.getYearValue()) throw new IllegalArgumentException("열매잔치 날짜는 해당 연도 안에서 선택하세요.");
        year.updateFruitFestivalDate(festivalDate);
        recordAuditLog(teacherId, "UPDATE_FRUIT_FESTIVAL", "yearValue=" + year.getYearValue() + ", festivalDate=" + festivalDate);
        return YearResponse.from(year);
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
        boolean effectivePoolActiveOnly = Boolean.TRUE.equals(poolActiveOnly);
        int validatedPoolLimit = (poolLimit == null || poolLimit < 1) ? 20 : Math.min(poolLimit, 200);
        int validatedAuditLimit = (auditLimit == null || auditLimit < 1) ? 100 : Math.min(auditLimit, 5000);
        int validatedAuditOffset = (auditOffset == null || auditOffset < 0) ? 0 : auditOffset;
        Integer resolvedYearValue = yearValue;
        if (resolvedYearValue == null) {
            resolvedYearValue = yearRepository.findByActiveTrueOrderByYearValueDesc()
                    .stream()
                    .findFirst()
                    .map(Year::getYearValue)
                    .orElse(null);
        }
        AdminTeacherListResponse teachers = effectiveIncludePools && resolvedYearValue != null
                ? getTeachers(teacherId, resolvedYearValue, effectivePoolActiveOnly, poolKeyword, poolLimit, 0)
                : new AdminTeacherListResponse(0L, validatedPoolLimit, 0, List.of());
        AdminStudentListResponse students = effectiveIncludePools && resolvedYearValue != null
                ? getStudents(teacherId, resolvedYearValue, effectivePoolActiveOnly, poolKeyword, poolLimit, 0)
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
        } else {
            targetYear = yearRepository.findByActiveTrueOrderByYearValueDesc()
                    .stream()
                    .filter(year -> !yearClassRepository.findByYearIdOrderBySortOrderAscIdAsc(year.getId()).isEmpty())
                    .findFirst()
                    .orElseGet(() -> yearRepository.findByActiveTrueOrderByYearValueDesc()
                            .stream()
                            .findFirst()
                            .orElseGet(() -> years.isEmpty()
                                    ? null
                                    : yearRepository.findByYearValue(years.get(0).yearValue()).orElse(null)));
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

        if (request.active()) {
            deactivateOtherActiveYears(null);
        }

        Year year = Year.builder()
                .yearValue(request.yearValue())
                .openToStudents(request.openToStudents())
                .openToTeachers(request.openToTeachers())
                .active(request.active())
                .build();
        Year savedYear = yearRepository.save(year);
        recordAuditLog(
                teacherId,
                "CREATE_YEAR",
                "yearId=" + savedYear.getId() + ", yearValue=" + savedYear.getYearValue() + ", active=" + savedYear.getActive()
        );
        return YearResponse.from(savedYear);
    }

    @Transactional
    public YearResponse updateYear(Long teacherId, Long yearId, UpdateYearRequest request) {
        validateAdmin(teacherId);

        Year year = yearRepository.findById(yearId)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        if (request.active()) {
            deactivateOtherActiveYears(yearId);
        }
        year.updateVisibility(request.openToStudents(), request.openToTeachers());
        year.updateActive(request.active());
        recordAuditLog(
                teacherId,
                "UPDATE_YEAR",
                "yearId=" + year.getId() + ", yearValue=" + year.getYearValue() + ", active=" + year.getActive()
        );

        return YearResponse.from(year);
    }

    private void deactivateOtherActiveYears(Long keepYearId) {
        yearRepository.findByActiveTrueOrderByYearValueDesc()
                .stream()
                .filter(year -> keepYearId == null || !year.getId().equals(keepYearId))
                .forEach(year -> year.updateActive(false));
    }

    @Transactional
    public YearClassResponse createYearClass(Long teacherId, CreateYearClassRequest request) {
        validateAdmin(teacherId);
        String className = requireText(request.className(), "className");
        Integer sortOrder = requireInteger(request.sortOrder(), "sortOrder");

        Year year = yearRepository.findByYearValue(request.yearValue())
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        YearClass yearClass = YearClass.builder()
                .year(year)
                .className(className)
                .sortOrder(sortOrder)
                .active(request.active())
                .build();
        YearClass savedYearClass = yearClassRepository.save(yearClass);
        recordAuditLog(
                teacherId,
                "CREATE_YEAR_CLASS",
                "yearClassId=" + savedYearClass.getId() + ", yearValue=" + year.getYearValue() + ", className=" + savedYearClass.getClassName()
        );
        return YearClassResponse.from(savedYearClass);
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
        validateYearTeacherMembership(yearClass.getYear().getId(), uniqueTeacherIds);

        Set<Long> existingTeacherIds = new LinkedHashSet<>();
        if (!uniqueTeacherIds.isEmpty()) {
            yearClassTeacherRepository.findByYearClassIdAndTeacherIdIn(yearClassId, new ArrayList<>(uniqueTeacherIds))
                    .forEach(item -> existingTeacherIds.add(item.getTeacher().getId()));
        }

        Map<Long, YearClassTeacher> existingByTeacherIdInYear = new LinkedHashMap<>();
        if (!uniqueTeacherIds.isEmpty()) {
            yearClassTeacherRepository.findByYearIdAndTeacherIdIn(
                            yearClass.getYear().getId(),
                            new ArrayList<>(uniqueTeacherIds)
                    )
                    .forEach(item -> existingByTeacherIdInYear.put(item.getTeacher().getId(), item));
        }

        List<YearClassTeacher> toSave = new ArrayList<>();
        List<Long> assignedTeacherIds = new ArrayList<>();
        List<Long> skippedTeacherIds = new ArrayList<>();

        for (Long teacherId : uniqueTeacherIds) {
            if (existingTeacherIds.contains(teacherId)) {
                skippedTeacherIds.add(teacherId);
                continue;
            }

            YearClassTeacher existingInYear = existingByTeacherIdInYear.get(teacherId);
            if (existingInYear != null && !existingInYear.getYearClass().getId().equals(yearClassId)) {
                throw new IllegalArgumentException("해당 교사는 이미 이 연도에 다른 반에 배정되어 있습니다. teacherId=" + teacherId);
            }

            toSave.add(YearClassTeacher.builder()
                    .yearClass(yearClass)
                    .teacher(teacherMap.get(teacherId))
                    .assignmentRole("ASSISTANT")
                    .build());
            assignedTeacherIds.add(teacherId);
        }

        if (!toSave.isEmpty()) {
            yearClassTeacherRepository.saveAll(toSave);
        }
        recordAuditLog(
                adminTeacherId,
                "ASSIGN_TEACHERS",
                "yearClassId=" + yearClassId + ", assignedTeacherIds=" + joinIds(assignedTeacherIds) + ", skippedTeacherIds=" + joinIds(skippedTeacherIds)
        );

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
        validateYearStudentMembership(yearClass.getYear().getId(), uniqueStudentIds);

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
        recordAuditLog(
                adminTeacherId,
                "ASSIGN_STUDENTS",
                "yearClassId=" + yearClassId + ", assignedStudentIds=" + joinIds(assignedStudentIds) + ", skippedStudentIds=" + joinIds(skippedStudentIds)
        );

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
        recordAuditLog(
                adminTeacherId,
                "UNASSIGN_TEACHERS",
                "yearClassId=" + yearClassId + ", removedTeacherIds=" + joinIds(removedTeacherIds) + ", skippedTeacherIds=" + joinIds(skippedTeacherIds)
        );

        return new YearClassTeacherUnassignmentBatchResponse(
                yearClassId,
                removedTeacherIds,
                skippedTeacherIds
        );
    }

    @Transactional
    public YearClassTeacherRoleUpdateResponse updateTeacherAssignmentRole(
            Long adminTeacherId,
            Long yearClassId,
            Long targetTeacherId,
            UpdateYearClassTeacherRoleRequest request
    ) {
        validateAdmin(adminTeacherId);

        YearClass yearClass = yearClassRepository.findById(yearClassId)
                .orElseThrow(() -> new IllegalArgumentException("반이 존재하지 않습니다."));
        YearClassTeacher yearClassTeacher = yearClassTeacherRepository.findByYearClassIdAndTeacherId(yearClassId, targetTeacherId)
                .orElseThrow(() -> new IllegalArgumentException("해당 교사는 이 반에 배정되어 있지 않습니다."));

        String assignmentRole = normalizeAssignmentRole(request.assignmentRole());
        if ("HOMEROOM".equals(assignmentRole)) {
            boolean hasAnotherHomeroom = yearClassTeacherRepository.findByYearClassId(yearClassId).stream()
                    .anyMatch(item -> !item.getTeacher().getId().equals(targetTeacherId)
                            && "HOMEROOM".equals(normalizeAssignmentRole(item.getAssignmentRole())));
            if (hasAnotherHomeroom) {
                throw new IllegalArgumentException("한 반에는 담임을 1명만 지정할 수 있습니다.");
            }
        }

        yearClassTeacher.updateAssignmentRole(assignmentRole);
        recordAuditLog(
                adminTeacherId,
                "UPDATE_TEACHER_ASSIGNMENT_ROLE",
                "yearClassId=" + yearClass.getId() + ", teacherId=" + targetTeacherId + ", assignmentRole=" + assignmentRole
        );
        return new YearClassTeacherRoleUpdateResponse(yearClass.getId(), targetTeacherId, assignmentRole);
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
        recordAuditLog(
                adminTeacherId,
                "UNASSIGN_STUDENTS",
                "yearClassId=" + yearClassId + ", removedStudentIds=" + joinIds(removedStudentIds) + ", skippedStudentIds=" + joinIds(skippedStudentIds)
        );

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
        recordAuditLog(
                adminTeacherId,
                "MOVE_STUDENTS",
                "targetYearClassId=" + targetYearClassId + ", movedStudentIds=" + joinIds(movedStudentIds) + ", skippedStudentIds=" + joinIds(skippedStudentIds)
        );

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
        return getYearClasses(adminTeacherId, yearValue, null, null);
    }

    @Transactional(readOnly = true)
    public List<AdminYearClassViewResponse> getYearClasses(Long adminTeacherId, Integer yearValue, LocalDate from, LocalDate to) {
        validateAdmin(adminTeacherId);
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("조회 시작일은 종료일보다 앞서야 합니다.");

        Year year = yearRepository.findByYearValue(yearValue)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        List<YearClass> yearClasses = yearClassRepository.findByYearIdOrderBySortOrderAscIdAsc(year.getId());
        return buildYearClassViews(yearClasses, from, to);
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

        yearClass.updateInfo(
                requireText(request.className(), "className"),
                requireInteger(request.sortOrder(), "sortOrder"),
                request.active()
        );
        recordAuditLog(
                adminTeacherId,
                "UPDATE_YEAR_CLASS",
                "yearClassId=" + yearClass.getId() + ", className=" + yearClass.getClassName() + ", active=" + yearClass.getActive()
        );
        return YearClassResponse.from(yearClass);
    }

    @Transactional(readOnly = true)
    public AdminTeacherListResponse getTeachers(
            Long adminTeacherId,
            Integer yearValue,
            Boolean activeOnly,
            String keyword,
            Integer limit,
            Integer offset
    ) {
        validateAdmin(adminTeacherId);
        Year year = resolveManagementYear(yearValue);

        int validatedLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 200);
        int validatedOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageIndex = validatedOffset / validatedLimit;

        String normalizedKeyword = hasText(keyword) ? keyword.trim() : null;
        if (yearTeacherRepository.findAllByYearId(year.getId(), false).isEmpty()) {
            Page<Teacher> fallbackPage = teacherRepository.search(
                    Boolean.TRUE.equals(activeOnly),
                    normalizedKeyword,
                    PageRequest.of(pageIndex, validatedLimit)
            );
            List<AdminTeacherResponse> fallbackItems = fallbackPage.getContent().stream()
                    .map(teacher -> new AdminTeacherResponse(
                            null,
                            teacher.getId(),
                            year.getYearValue(),
                            teacher.getLoginId(),
                            teacher.getTeacherName(),
                            teacher.getContactNumber(),
                            teacher.getBirthDate(),
                            teacher.getEffectiveRole().name(),
                            teacher.canCheckAllStudents(),
                            teacher.getActive(),
                            teacher.getCreatedAt(),
                            teacher.getUpdatedAt()
                    ))
                    .toList();
            return new AdminTeacherListResponse(
                    fallbackPage.getTotalElements(),
                    validatedLimit,
                    validatedOffset,
                    fallbackItems
            );
        }
        Page<YearTeacher> page = yearTeacherRepository.search(
                year.getYearValue(),
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

    @Transactional
    public AdminTeacherResponse createTeacher(Long adminTeacherId, Integer yearValue, CreateTeacherRequest request) {
        validateAdmin(adminTeacherId);
        Year year = resolveManagementYear(yearValue);

        String loginId = requireText(request.loginId(), "loginId");
        String teacherName = requireText(request.teacherName(), "teacherName");
        String birthDate = normalizeBirthDate(requireText(request.birthDate(), "birthDate"));
        TeacherRole role = parseTeacherRole(request.role());
        boolean canCheckAllStudents = Boolean.TRUE.equals(request.canCheckAllStudents());
        Teacher savedTeacher;

        Teacher teacher = teacherRepository.findByLoginId(loginId).orElse(null);
        if (teacher == null) {
            teacher = Teacher.builder()
                    .loginId(loginId)
                    .passwordHash(passwordEncoder.encode(birthDate))
                    .teacherName(teacherName)
                    .contactNumber(normalizeContactNumber(request.contactNumber()))
                    .birthDate(birthDate)
                    .role(role)
                    .active(true)
                    .passwordChangeRequired(true)
                    .canCheckAllStudents(canCheckAllStudents)
                    .build();
            savedTeacher = teacherRepository.save(teacher);
        } else {
            if (yearTeacherRepository.existsByYearIdAndTeacherId(year.getId(), teacher.getId())) {
                throw new IllegalArgumentException("이미 해당 연도에 등록된 교사입니다.");
            }
            teacher.updateInfo(
                    teacherName,
                    normalizeContactNumber(request.contactNumber()),
                    birthDate,
                    role,
                    teacher.getActive(),
                    canCheckAllStudents
            );
            teacher.changePassword(passwordEncoder.encode(birthDate));
            teacher.updatePasswordChangeRequired(true);
            savedTeacher = teacher;
        }

        YearTeacher yearTeacher = yearTeacherRepository.save(YearTeacher.builder()
                .year(year)
                .teacher(savedTeacher)
                .sortOrder(0)
                .active(request.active() == null || request.active())
                .build());
        recordAuditLog(
                adminTeacherId,
                "CREATE_TEACHER",
                "yearValue=" + yearValue + ", teacherId=" + savedTeacher.getId() + ", loginId=" + savedTeacher.getLoginId() + ", role=" + savedTeacher.getEffectiveRole().name()
        );
        return AdminTeacherResponse.from(yearTeacher);
    }

    @Transactional
    public AdminTeacherResponse createTeacher(Long adminTeacherId, CreateTeacherRequest request) {
        return createTeacher(adminTeacherId, null, request);
    }

    @Transactional
    public AdminTeacherResponse updateTeacher(Long adminTeacherId, Integer yearValue, Long targetTeacherId, UpdateTeacherRequest request) {
        validateAdmin(adminTeacherId);
        Year year = resolveManagementYear(yearValue);

        Teacher teacher = teacherRepository.findById(targetTeacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));
        YearTeacher yearTeacher = yearTeacherRepository.findByYearIdAndTeacherId(year.getId(), targetTeacherId)
                .orElseThrow(() -> new IllegalArgumentException("해당 연도에 등록되지 않은 교사입니다."));

        teacher.updateInfo(
                requireText(request.teacherName(), "teacherName"),
                normalizeContactNumber(request.contactNumber()),
                normalizeBirthDate(request.birthDate()),
                parseTeacherRole(request.role()),
                request.active() == null || request.active(),
                Boolean.TRUE.equals(request.canCheckAllStudents())
        );
        yearTeacher.update(yearTeacher.getSortOrder(), request.active() == null || request.active());

        if (hasText(request.password())) {
            String nextPassword = request.password().trim();
            passwordPolicyValidator.validate(nextPassword);
            teacher.changePassword(passwordEncoder.encode(nextPassword));
        }
        recordAuditLog(
                adminTeacherId,
                "UPDATE_TEACHER",
                "yearValue=" + yearValue + ", teacherId=" + teacher.getId() + ", loginId=" + teacher.getLoginId() + ", role=" + teacher.getEffectiveRole().name() + ", active=" + yearTeacher.getActive()
        );

        return AdminTeacherResponse.from(yearTeacher);
    }

    @Transactional(readOnly = true)
    public AdminStudentListResponse getStudents(
            Long adminTeacherId,
            Integer yearValue,
            Boolean activeOnly,
            String keyword,
            Integer limit,
            Integer offset
    ) {
        validateAdmin(adminTeacherId);
        Year year = resolveManagementYear(yearValue);

        int validatedLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 200);
        int validatedOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageIndex = validatedOffset / validatedLimit;

        String normalizedKeyword = hasText(keyword) ? keyword.trim() : null;
        if (yearStudentRepository.findAllByYearId(year.getId(), false).isEmpty()) {
            Page<Student> fallbackPage = studentRepository.search(
                    Boolean.TRUE.equals(activeOnly),
                    normalizedKeyword,
                    PageRequest.of(pageIndex, validatedLimit)
            );
            List<AdminStudentResponse> fallbackItems = fallbackPage.getContent().stream()
                    .map(student -> new AdminStudentResponse(
                            null,
                            student.getId(),
                            student.getStudentName(),
                            year.getYearValue(),
                            null,
                            student.getContactNumber(),
                            student.getBirthDate(),
                            student.getActive(),
                            student.getCreatedAt(),
                            student.getUpdatedAt()
                    ))
                    .toList();
            return new AdminStudentListResponse(
                    fallbackPage.getTotalElements(),
                    validatedLimit,
                    validatedOffset,
                    fallbackItems
            );
        }
        Page<YearStudent> page = yearStudentRepository.search(
                year.getYearValue(),
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

    @Transactional
    public AdminStudentResponse createStudent(Long adminTeacherId, Integer yearValue, CreateStudentRequest request) {
        validateAdmin(adminTeacherId);
        Year year = resolveManagementYear(yearValue);

        String studentName = requireText(request.studentName(), "studentName");
        String birthDate = normalizeBirthDate(request.birthDate());
        Student student = birthDate == null
                ? null
                : studentRepository.findFirstByStudentNameAndBirthDate(studentName, birthDate).orElse(null);
        if (student == null) {
            student = Student.builder()
                    .studentName(studentName)
                    .contactNumber(normalizeContactNumber(request.contactNumber()))
                    .birthDate(birthDate)
                    .active(true)
                    .build();
            student = studentRepository.save(student);
        } else {
            if (yearStudentRepository.existsByYearIdAndStudentId(year.getId(), student.getId())) {
                throw new IllegalArgumentException("이미 해당 연도에 등록된 학생입니다.");
            }
            student.updateInfo(
                    studentName,
                    normalizeContactNumber(request.contactNumber()),
                    birthDate,
                    student.getActive()
            );
        }
        YearStudent savedYearStudent = yearStudentRepository.save(YearStudent.builder()
                .year(year)
                .student(student)
                .schoolGrade(normalizeSchoolGrade(request.schoolGrade()))
                .sortOrder(0)
                .active(request.active() == null || request.active())
                .build());
        recordAuditLog(
                adminTeacherId,
                "CREATE_STUDENT",
                "yearValue=" + yearValue + ", studentId=" + student.getId() + ", studentName=" + student.getStudentName() + ", schoolGrade=" + savedYearStudent.getSchoolGrade()
        );
        return AdminStudentResponse.from(savedYearStudent);
    }

    @Transactional
    public AdminStudentResponse updateStudent(Long adminTeacherId, Integer yearValue, Long targetStudentId, UpdateStudentRequest request) {
        validateAdmin(adminTeacherId);
        Year year = resolveManagementYear(yearValue);

        Student student = studentRepository.findById(targetStudentId)
                .orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));
        YearStudent yearStudent = yearStudentRepository.findByYearIdAndStudentId(year.getId(), targetStudentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 연도에 등록되지 않은 학생입니다."));

        student.updateInfo(
                requireText(request.studentName(), "studentName"),
                normalizeContactNumber(request.contactNumber()),
                normalizeBirthDate(request.birthDate()),
                student.getActive()
        );
        yearStudent.update(
                normalizeSchoolGrade(request.schoolGrade()),
                yearStudent.getSortOrder(),
                request.active() == null || request.active()
        );
        recordAuditLog(
                adminTeacherId,
                "UPDATE_STUDENT",
                "yearValue=" + yearValue + ", studentId=" + student.getId() + ", studentName=" + student.getStudentName() + ", schoolGrade=" + yearStudent.getSchoolGrade() + ", active=" + yearStudent.getActive()
        );

        return AdminStudentResponse.from(yearStudent);
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
        return buildYearClassViews(yearClasses, null, null);
    }

    private List<AdminYearClassViewResponse> buildYearClassViews(List<YearClass> yearClasses, LocalDate requestedFrom, LocalDate requestedTo) {
        if (yearClasses.isEmpty()) {
            return List.of();
        }

        List<Long> yearClassIds = yearClasses.stream()
                .map(YearClass::getId)
                .toList();
        List<Long> yearIds = yearClasses.stream()
                .map(yearClass -> yearClass.getYear().getId())
                .distinct()
                .toList();

        Map<Long, List<AdminYearClassTeacherResponse>> teacherMapByYearClassId = new LinkedHashMap<>();
        List<YearClassTeacher> yearClassTeachers = yearClassTeacherRepository.findByYearClassIdInOrderByYearClassIdAscTeacherTeacherNameAsc(yearClassIds);
        List<Long> teacherIds = yearClassTeachers.stream()
                .map(item -> item.getTeacher().getId())
                .distinct()
                .toList();
        Map<String, YearTeacher> yearTeacherMap = teacherIds.isEmpty()
                ? Map.of()
                : yearTeacherRepository.findByYearIdInAndTeacherIdIn(yearIds, teacherIds)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getYear().getId() + ":" + item.getTeacher().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        yearClassTeachers.forEach(item -> {
                    YearTeacher yearTeacher = yearTeacherMap.get(item.getYearClass().getYear().getId() + ":" + item.getTeacher().getId());
                    boolean active = yearTeacher != null ? Boolean.TRUE.equals(yearTeacher.getActive()) : Boolean.TRUE.equals(item.getTeacher().getActive());
                    teacherMapByYearClassId
                            .computeIfAbsent(item.getYearClass().getId(), key -> new ArrayList<>())
                            .add(AdminYearClassTeacherResponse.from(item, active));
                });

        List<YearClassStudent> yearClassStudents = yearClassStudentRepository.findByYearClassIdInOrderByYearClassIdAscStudentStudentNameAsc(yearClassIds);
        List<Long> studentIds = yearClassStudents.stream()
                .map(item -> item.getStudent().getId())
                .distinct()
                .toList();
        Map<String, YearStudent> yearStudentMap = studentIds.isEmpty()
                ? Map.of()
                : yearStudentRepository.findByYearIdInAndStudentIdIn(yearIds, studentIds)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getYear().getId() + ":" + item.getStudent().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, List<AdminYearClassStudentResponse>> studentMapByYearClassId = new LinkedHashMap<>();
        yearClassStudents.forEach(item -> {
                    YearStudent yearStudent = yearStudentMap.get(item.getYear().getId() + ":" + item.getStudent().getId());
                    Year fruitYear = item.getYearClass().getYear();
                    LocalDate from = requestedFrom != null ? requestedFrom : (fruitYear.getFruitResetDate() == null ? LocalDate.of(fruitYear.getYearValue(), 1, 1) : fruitYear.getFruitResetDate().plusDays(1));
                    LocalDate to = requestedTo != null ? requestedTo : LocalDate.of(fruitYear.getYearValue(), 12, 31);
                    long totalAdjustment = fruitAdjustmentRepository.sumAmount(fruitYear.getId(), item.getStudent().getId(), FruitType.TOTAL, from, to);
                    long attitudeAdjustment = fruitAdjustmentRepository.sumAmount(fruitYear.getId(), item.getStudent().getId(), FruitType.ATTITUDE, from, to);
                    long qtCount = devotionCheckRepository.countQtByYearStudentAndDateBetween(fruitYear.getId(), item.getStudent().getId(), from, to);
                    long attitudeCount = devotionCheckRepository.countAttitudeByYearStudentAndDateBetween(fruitYear.getId(), item.getStudent().getId(), from, to);
                    long noteCount = devotionCheckRepository.sumNoteCountByYearStudentAndDateBetween(fruitYear.getId(), item.getStudent().getId(), from, to);

                    studentMapByYearClassId
                            .computeIfAbsent(item.getYearClass().getId(), key -> new ArrayList<>())
                            .add(new AdminYearClassStudentResponse(
                                    item.getStudent().getId(),
                                    item.getStudent().getStudentName(),
                                    yearStudent == null ? null : yearStudent.getSchoolGrade(),
                                    item.getStudent().getContactNumber(),
                                    yearStudent != null ? Boolean.TRUE.equals(yearStudent.getActive()) : item.getStudent().getActive(),
                                    qtCount,
                                    attitudeCount + attitudeAdjustment,
                                    noteCount,
                                    0,
                                    0,
                                    0,
                                    totalAdjustment,
                                    qtCount + attitudeCount + attitudeAdjustment + noteCount + totalAdjustment
                            ));
                });

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

    private void recordAuditLog(Long actorTeacherId, String actionType, String detail) {
        auditLogService.record(actorTeacherId, actionType, detail);
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "-";
        }
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
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

        TeacherRole role = teacher.getEffectiveRole();
        if (role != TeacherRole.ADMIN && role != TeacherRole.PASTOR && role != TeacherRole.DIRECTOR) {
            throw new IllegalArgumentException("관리자 권한이 없습니다.");
        }
        return teacher;
    }

    private String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value.trim();
    }

    private Integer requireInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value;
    }

    private String normalizeOptionalText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeSchoolGrade(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeContactNumber(String value) {
        if (!hasText(value)) {
            return null;
        }
        String digitsOnly = value.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? null : digitsOnly;
    }

    private String normalizeBirthDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String digitsOnly = value.replaceAll("[^0-9]", "");
        if (digitsOnly.length() != 8) {
            throw new IllegalArgumentException("birthDate는 8자리 숫자여야 합니다.");
        }
        return digitsOnly;
    }

    private TeacherRole parseTeacherRole(String rawRole) {
        if (!hasText(rawRole)) {
            throw new IllegalArgumentException("role은 비어 있을 수 없습니다.");
        }
        try {
            return TeacherRole.valueOf(rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 role입니다.");
        }
    }

    private String normalizeAssignmentRole(String rawRole) {
        if (!hasText(rawRole)) {
            return "ASSISTANT";
        }
        String normalized = rawRole.trim().toUpperCase();
        if (!normalized.equals("HOMEROOM") && !normalized.equals("ASSISTANT")) {
            throw new IllegalArgumentException("유효하지 않은 assignmentRole입니다.");
        }
        return normalized;
    }

    private Year getYearByValue(Integer yearValue) {
        if (yearValue == null) {
            throw new IllegalArgumentException("연도가 비어 있습니다.");
        }
        return yearRepository.findByYearValue(yearValue)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
    }

    private Year resolveManagementYear(Integer yearValue) {
        if (yearValue != null) {
            return getYearByValue(yearValue);
        }
        return yearRepository.findByActiveTrueOrderByYearValueDesc()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("활성 연도가 없습니다."));
    }

    private void validateYearTeacherMembership(Long yearId, Set<Long> teacherIds) {
        if (yearTeacherRepository.findAllByYearId(yearId, false).isEmpty()) {
            return;
        }
        for (Long teacherId : teacherIds) {
            YearTeacher yearTeacher = yearTeacherRepository.findByYearIdAndTeacherId(yearId, teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 교사는 이 연도 운영 명단에 없습니다. teacherId=" + teacherId));
            if (!Boolean.TRUE.equals(yearTeacher.getActive())) {
                throw new IllegalArgumentException("비활성 교사는 배정할 수 없습니다. teacherId=" + teacherId);
            }
        }
    }

    private void validateYearStudentMembership(Long yearId, Set<Long> studentIds) {
        if (yearStudentRepository.findAllByYearId(yearId, false).isEmpty()) {
            return;
        }
        for (Long studentId : studentIds) {
            YearStudent yearStudent = yearStudentRepository.findByYearIdAndStudentId(yearId, studentId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 학생은 이 연도 운영 명단에 없습니다. studentId=" + studentId));
            if (!Boolean.TRUE.equals(yearStudent.getActive())) {
                throw new IllegalArgumentException("비활성 학생은 배정할 수 없습니다. studentId=" + studentId);
            }
        }
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
