package com.church.qt.teacherapp;

import com.church.qt.common.AuditLogService;
import com.church.qt.common.ChangePasswordRequest;
import com.church.qt.common.PasswordPolicyValidator;
import com.church.qt.domain.devotion.DevotionCheck;
import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.fruit.FruitAdjustment;
import com.church.qt.domain.fruit.FruitAdjustmentRepository;
import com.church.qt.domain.fruit.FruitType;
import com.church.qt.domain.meetingnote.MeetingNote;
import com.church.qt.domain.meetingnote.MeetingNoteRepository;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.student.StudentRepository;
import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRole;
import com.church.qt.domain.teacher.TeacherRepository;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClassStudent;
import com.church.qt.domain.yearclass.YearClassStudentRepository;
import com.church.qt.domain.yearclass.YearClassTeacherRepository;
import com.church.qt.domain.yearstudent.YearStudentRepository;
import com.church.qt.domain.yearteacher.YearTeacher;
import com.church.qt.domain.yearteacher.YearTeacherRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TeacherAppService {

    private final DevotionCheckRepository devotionCheckRepository;
    private final FruitAdjustmentRepository fruitAdjustmentRepository;
    private final MeetingNoteRepository meetingNoteRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final YearRepository yearRepository;
    private final YearClassStudentRepository yearClassStudentRepository;
    private final YearClassTeacherRepository yearClassTeacherRepository;
    private final YearStudentRepository yearStudentRepository;
    private final YearTeacherRepository yearTeacherRepository;
    private final AuditLogService auditLogService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.jwt-secret}")
    private String jwtSecret;

    @Value("${app.auth.access-token-expiration-seconds}")
    private long accessTokenExpirationSeconds;

    @Transactional(readOnly = true)
    public TeacherLoginResponse login(TeacherLoginRequest request) {
        Teacher teacher = teacherRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), teacher.getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (!teacher.getActive()) {
            throw new IllegalArgumentException("현재 페이지에 접근할 수 없습니다.");
        }
        if (isInactiveInLatestYearMembership(teacher.getId())) {
            throw new IllegalArgumentException("현재 페이지에 접근할 수 없습니다.");
        }

        String accessToken = createAccessToken(teacher);
        return TeacherLoginResponse.from(teacher, accessToken);
    }

    private boolean isInactiveInLatestYearMembership(Long teacherId) {
        return yearTeacherRepository.findByTeacherIdOrderByLatestYear(teacherId)
                .stream()
                .findFirst()
                .map(YearTeacher::getActive)
                .map(active -> !Boolean.TRUE.equals(active))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<TeacherStudentListResponse> getStudents(Long teacherId, Integer yearValue) {
        requireActiveTeacher(teacherId);
        Year year = yearRepository.findByYearValue(yearValue)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        String myClassName = yearClassTeacherRepository.findClassNamesByTeacherIdAndYearValue(teacherId, yearValue)
                .stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        List<TeacherStudentListResponse> students = yearStudentRepository.findTeacherStudentsForYear(teacherId, yearValue);
        Map<Long, String> assignedClassNameByStudentId = loadAssignedClassNames(year.getId(), students);
        Map<Long, Long> deductionByStudentId = fruitAdjustmentRepository.findTotalAdjustmentsByYearId(year.getId()).stream()
                .filter(item -> year.getFruitResetDate() == null || !item.getAdjustmentDate().isBefore(year.getFruitResetDate()))
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getStudent().getId(), java.util.stream.Collectors.summingLong(item -> item.getAmount())));
        Map<Long, Long> attitudeAdditionByStudentId = fruitAdjustmentRepository.findAdjustmentsByYearId(year.getId()).stream()
                .filter(item -> year.getFruitResetDate() == null || !item.getAdjustmentDate().isBefore(year.getFruitResetDate()))
                .filter(item -> item.getFruitType() == FruitType.ATTITUDE)
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getStudent().getId(), java.util.stream.Collectors.summingLong(item -> item.getAmount())));
        return students
                .stream()
                .map(item -> item.withClassNames(
                        item.myClassStudent() ? myClassName : null,
                        assignedClassNameByStudentId.get(item.studentId())
                ).withFruitAdjustments(
                        attitudeAdditionByStudentId.getOrDefault(item.studentId(), 0L),
                        deductionByStudentId.getOrDefault(item.studentId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherMeetingNoteListItemResponse> getMeetingNotes(Long teacherId) {
        requireActiveTeacher(teacherId);
        return meetingNoteRepository.findAllWithAuthorOrderByUpdatedAtDesc()
                .stream()
                .map(TeacherMeetingNoteListItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherMeetingNoteDetailResponse getMeetingNote(Long teacherId, Long meetingNoteId) {
        Teacher teacher = requireActiveTeacher(teacherId);
        MeetingNote meetingNote = getMeetingNoteOrThrow(meetingNoteId);
        return TeacherMeetingNoteDetailResponse.from(meetingNote, canManageMeetingNote(teacher, meetingNote));
    }

    @Transactional
    public TeacherMeetingNoteDetailResponse createMeetingNote(Long teacherId, CreateTeacherMeetingNoteRequest request) {
        Teacher teacher = requireActiveTeacher(teacherId);
        String title = requireMeetingNoteText(request.title(), "제목을 입력하세요.", 200);
        String content = requireMeetingNoteText(request.content(), "내용을 입력하세요.", 20000);

        MeetingNote meetingNote = meetingNoteRepository.save(MeetingNote.builder()
                .title(title)
                .content(content)
                .createdByTeacher(teacher)
                .build());

        auditLogService.record(
                teacherId,
                "CREATE_MEETING_NOTE",
                "meetingNoteId=" + meetingNote.getId() + ", title=" + meetingNote.getTitle()
        );
        return TeacherMeetingNoteDetailResponse.from(meetingNote, true);
    }

    @Transactional
    public TeacherMeetingNoteDetailResponse updateMeetingNote(Long teacherId, Long meetingNoteId, CreateTeacherMeetingNoteRequest request) {
        Teacher teacher = requireActiveTeacher(teacherId);
        MeetingNote meetingNote = getMeetingNoteOrThrow(meetingNoteId);
        requireMeetingNoteManagePermission(teacher, meetingNote);

        String title = requireMeetingNoteText(request.title(), "제목을 입력하세요.", 200);
        String content = requireMeetingNoteText(request.content(), "내용을 입력하세요.", 20000);
        meetingNote.update(title, content);

        auditLogService.record(
                teacherId,
                "UPDATE_MEETING_NOTE",
                "meetingNoteId=" + meetingNote.getId() + ", title=" + meetingNote.getTitle()
        );
        return TeacherMeetingNoteDetailResponse.from(meetingNote, true);
    }

    @Transactional
    public void deleteMeetingNote(Long teacherId, Long meetingNoteId) {
        Teacher teacher = requireActiveTeacher(teacherId);
        MeetingNote meetingNote = getMeetingNoteOrThrow(meetingNoteId);
        requireMeetingNoteManagePermission(teacher, meetingNote);

        meetingNoteRepository.delete(meetingNote);
        auditLogService.record(
                teacherId,
                "DELETE_MEETING_NOTE",
                "meetingNoteId=" + meetingNoteId + ", title=" + meetingNote.getTitle()
        );
    }

    @Transactional
    public TeacherLoginResponse changePassword(Long teacherId, ChangePasswordRequest request) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));

        if (!teacher.getActive()) {
            throw new IllegalArgumentException("비활성화된 교사입니다.");
        }

        String currentPassword = requirePasswordText(request.currentPassword(), "현재 비밀번호를 입력하세요.");
        String newPassword = requirePasswordText(request.newPassword(), "새 비밀번호를 입력하세요.");
        String confirmPassword = requirePasswordText(request.confirmPassword(), "새 비밀번호 확인을 입력하세요.");

        if (!passwordEncoder.matches(currentPassword, teacher.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        passwordPolicyValidator.validate(newPassword);

        teacher.changePassword(passwordEncoder.encode(newPassword));
        teacher.updatePasswordChangeRequired(false);
        auditLogService.record(
                teacherId,
                "CHANGE_PASSWORD",
                "teacherId=" + teacher.getId() + ", loginId=" + teacher.getLoginId()
        );
        return TeacherLoginResponse.from(teacher, createAccessToken(teacher));
    }

    @Transactional
    public void updateCheck(Long teacherId, TeacherCheckRequest request) {

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));

        Year year = yearRepository.findByYearValue(request.year())
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        if (!yearClassStudentRepository.existsByYearIdAndStudentId(year.getId(), request.studentId())) {
            throw new IllegalArgumentException("해당 학생은 이 연도에 속하지 않습니다.");
        }

        boolean myClassStudent = yearClassTeacherRepository.existsManageableStudent(teacherId, request.studentId(), request.year());
        boolean privilegedRole = hasPrivilegedCheckAccess(teacher);
        boolean canEditQt = myClassStudent || privilegedRole || hasGlobalCheckAccess(teacher);
        boolean canEditNote = myClassStudent || privilegedRole;

        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));

        DevotionCheck check = devotionCheckRepository
                .findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), request.date())
                .orElse(null);
        boolean previousQtChecked = check != null && Boolean.TRUE.equals(check.getQtChecked());
        boolean previousAttitudeChecked = check != null && Boolean.TRUE.equals(check.getAttitudeChecked());
        int previousAttitudeCount = check == null ? 0 : Math.max(0, check.getAttitudeCount());
        int previousNoteCount = check == null ? 0 : Math.max(0, check.getNoteCount());

        if (!canEditQt && request.qtChecked() != previousQtChecked) {
            throw new IllegalArgumentException("이 학생의 QT를 체크할 수 없습니다.");
        }

        if (!canEditNote && request.noteCount() != previousNoteCount) {
            throw new IllegalArgumentException("이 학생의 노트를 체크할 수 없습니다.");
        }

        int attitudeCount = Math.max(0, Math.min(99, request.attitudeCount()));
        if (attitudeCount == 0 && request.attitudeChecked()) attitudeCount = 1;
        if (!request.qtChecked() && attitudeCount == 0 && request.noteCount() <= 0) {
            if (check != null) {
                devotionCheckRepository.delete(check);
                auditLogService.record(
                        teacherId,
                        "DELETE_DEVOTION_CHECK",
                        "yearValue=" + year.getYearValue()
                                + ", studentId=" + student.getId()
                                + ", studentName=" + student.getStudentName()
                                + ", date=" + request.date()
                                + ", before=qt:" + previousQtChecked + "|attitude:" + previousAttitudeChecked + "|note:" + previousNoteCount
                );
            }
            return;
        }

        if (check == null) {
            check = DevotionCheck.builder()
                    .year(year)
                    .student(student)
                    .checkDate(request.date())
                    .qtChecked(request.qtChecked())
                    .attitudeChecked(request.attitudeChecked())
                    .attitudeCount(attitudeCount)
                    .noteChecked(request.noteCount() > 0)
                    .noteCount(request.noteCount())
                    .checkedByTeacher(teacher)
                    .build();

            devotionCheckRepository.save(check);
        } else {
            check.updateChecks(request.qtChecked(), attitudeCount, request.noteCount(), teacher);
        }
        auditLogService.record(
                teacherId,
                "SAVE_DEVOTION_CHECK",
                "yearValue=" + year.getYearValue()
                        + ", studentId=" + student.getId()
                        + ", studentName=" + student.getStudentName()
                        + ", date=" + request.date()
                        + ", before=qt:" + previousQtChecked + "|attitude:" + previousAttitudeCount + "|note:" + previousNoteCount
                        + ", after=qt:" + request.qtChecked() + "|attitude:" + attitudeCount + "|note:" + request.noteCount()
        );
    }

    @Transactional
    public void createFruitAdjustment(Long teacherId, CreateFruitAdjustmentRequest request) {
        Teacher teacher = requireActiveTeacher(teacherId);
        if (request.amount() == 0) throw new IllegalArgumentException("수량은 0일 수 없습니다.");
        if (Math.abs(request.amount()) > 99) throw new IllegalArgumentException("한 번에 99개까지만 추가하거나 차감할 수 있습니다.");
        if (request.studentId() == null || request.year() == null || request.date() == null) {
            throw new IllegalArgumentException("학생, 연도와 날짜를 입력하세요.");
        }
        Year year = yearRepository.findByYearValue(request.year()).orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        Student student = studentRepository.findById(request.studentId()).orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));
        if (!yearClassStudentRepository.existsByYearIdAndStudentId(year.getId(), student.getId())) throw new IllegalArgumentException("해당 학생은 이 연도에 속하지 않습니다.");
        String reason = request.reason() == null ? null : request.reason().trim();
        if (reason != null && reason.length() > 500) throw new IllegalArgumentException("사유는 500자 이내로 입력하세요.");
        if (request.date().getYear() != year.getYearValue()) throw new IllegalArgumentException("처리 날짜는 해당 연도 안에서 선택하세요.");
        FruitType fruitType = request.amount() > 0 ? FruitType.ATTITUDE : FruitType.TOTAL;
        fruitAdjustmentRepository.save(FruitAdjustment.builder().year(year).student(student).fruitType(fruitType)
                .amount(request.amount()).adjustmentDate(request.date()).reason(reason).adjustedByTeacher(teacher).build());
        auditLogService.record(teacherId, "CREATE_FRUIT_ADJUSTMENT", "yearValue=" + year.getYearValue() + ", studentId=" + student.getId() + ", type=" + fruitType + ", amount=" + request.amount() + ", date=" + request.date());
    }

    @Transactional(readOnly = true)
    public List<TeacherFruitAdjustmentResponse> getFruitAdjustments(Long teacherId, Integer yearValue) {
        requireActiveTeacher(teacherId);
        Year year = yearRepository.findByYearValue(yearValue)
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));
        return fruitAdjustmentRepository.findAdjustmentsByYearId(year.getId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getStudent().getActive()))
                .filter(item -> yearStudentRepository.existsByYearIdAndStudentIdAndActiveTrue(
                        item.getYear().getId(), item.getStudent().getId()))
                .map(TeacherFruitAdjustmentResponse::from)
                .toList();
    }

    public Long extractTeacherId(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Long.valueOf(claims.getSubject());
        } catch (JwtException e) {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다.");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다.");
        }
    }

    private String createAccessToken(Teacher teacher) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(teacher.getId()))
                .claim("teacherName", teacher.getTeacherName())
                .claim("role", teacher.getEffectiveRole().name())
                .claim("canCheckAllStudents", teacher.canCheckAllStudents())
                .claim("passwordChangeRequired", Boolean.TRUE.equals(teacher.getPasswordChangeRequired()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationSeconds)))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("인증 토큰이 없습니다.");
        }

        return authorizationHeader.substring(7);
    }

    private String requirePasswordText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String requireMeetingNoteText(String value, String message, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("입력 길이가 너무 깁니다.");
        }
        return normalized;
    }

    private Teacher requireActiveTeacher(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));
        if (!teacher.getActive()) {
            throw new IllegalArgumentException("비활성화된 교사입니다.");
        }
        return teacher;
    }

    private Map<Long, String> loadAssignedClassNames(Long yearId, List<TeacherStudentListResponse> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> studentIds = students.stream()
                .map(TeacherStudentListResponse::studentId)
                .toList();
        Map<Long, String> classNameByStudentId = new LinkedHashMap<>();
        List<YearClassStudent> yearClassStudents = yearClassStudentRepository.findByYearIdAndStudentIdIn(yearId, studentIds);
        for (YearClassStudent yearClassStudent : yearClassStudents) {
            if (yearClassStudent.getStudent() == null || yearClassStudent.getYearClass() == null) {
                continue;
            }
            classNameByStudentId.putIfAbsent(
                    yearClassStudent.getStudent().getId(),
                    yearClassStudent.getYearClass().getClassName()
            );
        }
        return classNameByStudentId;
    }

    private boolean hasGlobalCheckAccess(Teacher teacher) {
        return teacher.canCheckAllStudents();
    }

    private boolean hasPrivilegedCheckAccess(Teacher teacher) {
        TeacherRole role = teacher.getEffectiveRole();
        return role == TeacherRole.ADMIN || role == TeacherRole.PASTOR || role == TeacherRole.DIRECTOR;
    }

    private MeetingNote getMeetingNoteOrThrow(Long meetingNoteId) {
        return meetingNoteRepository.findDetailById(meetingNoteId)
                .orElseThrow(() -> new IllegalArgumentException("회의록이 존재하지 않습니다."));
    }

    private void requireMeetingNoteManagePermission(Teacher teacher, MeetingNote meetingNote) {
        if (!canManageMeetingNote(teacher, meetingNote)) {
            throw new IllegalArgumentException("이 회의록을 수정하거나 삭제할 수 없습니다.");
        }
    }

    private boolean canManageMeetingNote(Teacher teacher, MeetingNote meetingNote) {
        if (teacher.getEffectiveRole() == TeacherRole.PASTOR) {
            return true;
        }
        return meetingNote.getCreatedByTeacher() != null
                && Objects.equals(meetingNote.getCreatedByTeacher().getId(), teacher.getId());
    }
}
