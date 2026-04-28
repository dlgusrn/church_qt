package com.church.qt.teacherapp;

import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.devotion.DevotionCheck;
import com.church.qt.domain.meetingnote.MeetingNoteRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeacherAppServiceIntegrationTest {

    @Autowired
    private TeacherAppService teacherAppService;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private YearClassRepository yearClassRepository;

    @Autowired
    private YearClassTeacherRepository yearClassTeacherRepository;

    @Autowired
    private YearClassStudentRepository yearClassStudentRepository;

    @Autowired
    private YearTeacherRepository yearTeacherRepository;

    @Autowired
    private YearStudentRepository yearStudentRepository;

    @Autowired
    private DevotionCheckRepository devotionCheckRepository;

    @Autowired
    private MeetingNoteRepository meetingNoteRepository;

    @Test
    @DisplayName("교사 학생 목록은 권한과 무관하게 연도 전체 학생을 조회하고 담당 반 여부를 함께 반환한다")
    void getStudents_returnsAllYearStudentsWithMyClassFlag() {
        Teacher teacher = saveTeacher("global_teacher", TeacherRole.TEACHER, true, true);
        Year year = saveYear(2026);
        saveYearTeacher(year, teacher);
        YearClass yearClass = saveYearClass(year, "은혜반");

        Student assignedStudent = saveStudent("배정학생");
        Student unassignedStudent = saveStudent("미배정학생");
        saveYearStudent(year, assignedStudent, "4");
        saveYearStudent(year, unassignedStudent, "5");
        saveYearClassStudent(year, yearClass, assignedStudent);
        saveYearClassTeacher(yearClass, teacher);

        List<TeacherStudentListResponse> responses = teacherAppService.getStudents(teacher.getId(), year.getYearValue());

        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(item -> item.studentName().equals("배정학생")));
        assertTrue(responses.stream().anyMatch(item -> item.studentName().equals("미배정학생")));
        assertTrue(responses.stream().filter(item -> item.studentName().equals("배정학생")).findFirst().orElseThrow().myClassStudent());
        assertFalse(responses.stream().filter(item -> item.studentName().equals("미배정학생")).findFirst().orElseThrow().myClassStudent());
        assertEquals("은혜반", responses.stream().filter(item -> item.studentName().equals("배정학생")).findFirst().orElseThrow().myClassName());
        assertEquals("은혜반", responses.stream().filter(item -> item.studentName().equals("배정학생")).findFirst().orElseThrow().assignedClassName());
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 없는 교사도 연도 전체 학생 목록을 조회할 수 있다")
    void getStudents_withoutGlobalCheckAccess_returnsAllStudents() {
        Teacher teacher = saveTeacher("assigned_teacher", TeacherRole.TEACHER, true, false);
        Year year = saveYear(2027);
        saveYearTeacher(year, teacher);
        YearClass yearClass = saveYearClass(year, "사랑반");

        Student assignedStudent = saveStudent("담당학생");
        Student unassignedStudent = saveStudent("다른학생");
        saveYearStudent(year, assignedStudent, "4");
        saveYearStudent(year, unassignedStudent, "5");
        saveYearClassStudent(year, yearClass, assignedStudent);
        saveYearClassTeacher(yearClass, teacher);

        List<TeacherStudentListResponse> responses = teacherAppService.getStudents(teacher.getId(), year.getYearValue());

        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(item -> item.studentName().equals("담당학생") && item.myClassStudent()));
        assertTrue(responses.stream().anyMatch(item -> item.studentName().equals("다른학생") && !item.myClassStudent()));
        assertEquals("사랑반", responses.stream().filter(item -> item.studentName().equals("담당학생")).findFirst().orElseThrow().myClassName());
        assertEquals("사랑반", responses.stream().filter(item -> item.studentName().equals("담당학생")).findFirst().orElseThrow().assignedClassName());
    }

    @Test
    @DisplayName("ADMIN 역할만 있는 교사도 연도 전체 학생 목록을 조회하고 담당 반 여부로 구분한다")
    void getStudents_withAdminRoleOnly_returnsAllStudents() {
        Teacher teacher = saveTeacher("admin_teacher_only", TeacherRole.ADMIN, true, false);
        Year year = saveYear(2030);
        saveYearTeacher(year, teacher);
        YearClass yearClass = saveYearClass(year, "충성반");

        Student assignedStudent = saveStudent("관리자담당학생");
        Student unassignedStudent = saveStudent("관리자다른학생");
        saveYearStudent(year, assignedStudent, "4");
        saveYearStudent(year, unassignedStudent, "5");
        saveYearClassStudent(year, yearClass, assignedStudent);
        saveYearClassTeacher(yearClass, teacher);

        List<TeacherStudentListResponse> responses = teacherAppService.getStudents(teacher.getId(), year.getYearValue());

        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(item -> item.studentName().equals("관리자담당학생") && item.myClassStudent()));
        assertTrue(responses.stream().anyMatch(item -> item.studentName().equals("관리자다른학생") && !item.myClassStudent()));
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 있는 교사는 다른 반 학생의 QT를 체크할 수 있다")
    void updateCheck_withGlobalCheckAccess_allowsQtForUnassignedStudent() {
        Teacher teacher = saveTeacher("check_all_teacher", TeacherRole.TEACHER, true, true);
        Year year = saveYear(2028);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "소망반");

        Student student = saveStudent("전체권한학생");
        saveYearStudent(year, student, "6");
        saveYearClassStudent(year, otherClass, student);

        teacherAppService.updateCheck(
                teacher.getId(),
                new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2028, 3, 15), true, false, 0)
        );

        assertEquals(1L, devotionCheckRepository.countByYearIdAndStudentIdAndQtCheckedTrue(year.getId(), student.getId()));
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 없는 교사는 다른 반 학생의 QT를 체크할 수 없다")
    void updateCheck_withoutGlobalCheckAccess_rejectsUnassignedQt() {
        Teacher teacher = saveTeacher("no_check_all_teacher", TeacherRole.TEACHER, true, false);
        Year year = saveYear(2029);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "진리반");

        Student student = saveStudent("권한없는학생");
        saveYearStudent(year, student, "6");
        saveYearClassStudent(year, otherClass, student);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teacherAppService.updateCheck(
                        teacher.getId(),
                        new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2029, 3, 16), true, false, 0)
                )
        );

        assertEquals("이 학생의 QT를 체크할 수 없습니다.", exception.getMessage());
        assertFalse(devotionCheckRepository.findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), LocalDate.of(2029, 3, 16)).isPresent());
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 없는 교사도 다른 반 학생의 태도는 체크할 수 있다")
    void updateCheck_withoutGlobalCheckAccess_allowsAttitudeForUnassignedStudent() {
        Teacher teacher = saveTeacher("attitude_teacher", TeacherRole.TEACHER, true, false);
        Year year = saveYear(2033);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "화평반");

        Student student = saveStudent("태도학생");
        saveYearStudent(year, student, "4");
        saveYearClassStudent(year, otherClass, student);

        teacherAppService.updateCheck(
                teacher.getId(),
                new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2033, 3, 12), false, true, 0)
        );

        assertEquals(1L, devotionCheckRepository.countByYearIdAndStudentIdAndAttitudeCheckedTrue(year.getId(), student.getId()));
    }

    @Test
    @DisplayName("다른 반 학생은 태도만 체크할 수 있다")
    void updateCheck_withGlobalCheckAccess_allowsAttitudeOnlyForUnassignedStudent() {
        Teacher teacher = saveTeacher("attitude_only_teacher", TeacherRole.TEACHER, true, true);
        Year year = saveYear(2031);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "믿음반");

        Student student = saveStudent("태도전용학생");
        saveYearStudent(year, student, "5");
        saveYearClassStudent(year, otherClass, student);

        teacherAppService.updateCheck(
                teacher.getId(),
                new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2031, 3, 10), false, true, 0)
        );

        assertEquals(1L, devotionCheckRepository.countByYearIdAndStudentIdAndAttitudeCheckedTrue(year.getId(), student.getId()));
        assertEquals(0L, devotionCheckRepository.countByYearIdAndStudentIdAndQtCheckedTrue(year.getId(), student.getId()));
        assertEquals(0L, devotionCheckRepository.sumNoteCountByYearIdAndStudentId(year.getId(), student.getId()));
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 있는 교사는 다른 반 학생의 노트 체크는 할 수 없다")
    void updateCheck_withGlobalCheckAccess_rejectsNoteForUnassignedStudent() {
        Teacher teacher = saveTeacher("reject_qt_note_teacher", TeacherRole.TEACHER, true, true);
        Year year = saveYear(2032);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "평안반");

        Student student = saveStudent("제한학생");
        saveYearStudent(year, student, "6");
        saveYearClassStudent(year, otherClass, student);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teacherAppService.updateCheck(
                        teacher.getId(),
                        new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2032, 3, 11), false, true, 1)
                )
        );

        assertEquals("이 학생의 노트를 체크할 수 없습니다.", exception.getMessage());
        assertFalse(devotionCheckRepository.findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), LocalDate.of(2032, 3, 11)).isPresent());
    }

    @Test
    @DisplayName("ADMIN 역할은 다른 반 학생도 QT와 노트를 모두 체크할 수 있다")
    void updateCheck_withAdminRole_allowsFullCheckForUnassignedStudent() {
        Teacher teacher = saveTeacher("admin_full_teacher", TeacherRole.ADMIN, true, false);
        Year year = saveYear(2034);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "기쁨반");

        Student student = saveStudent("관리자전체학생");
        saveYearStudent(year, student, "6");
        saveYearClassStudent(year, otherClass, student);

        teacherAppService.updateCheck(
                teacher.getId(),
                new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2034, 3, 13), true, true, 3)
        );

        DevotionCheck check = devotionCheckRepository.findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), LocalDate.of(2034, 3, 13)).orElseThrow();
        assertTrue(Boolean.TRUE.equals(check.getQtChecked()));
        assertTrue(Boolean.TRUE.equals(check.getAttitudeChecked()));
        assertEquals(3, check.getNoteCount());
    }

    @Test
    @DisplayName("교사는 회의록을 작성하고 목록 및 상세 조회할 수 있다")
    void meetingNote_createAndRead_success() {
        Teacher teacher = saveTeacher("meeting_teacher", TeacherRole.TEACHER, true, false);

        TeacherMeetingNoteDetailResponse created = teacherAppService.createMeetingNote(
                teacher.getId(),
                new CreateTeacherMeetingNoteRequest("주일 회의", "# 안건\n- 학생 심방\n- 반 운영")
        );

        assertEquals("주일 회의", created.title());
        assertTrue(created.noteId() != null && created.noteId() > 0);

        List<TeacherMeetingNoteListItemResponse> items = teacherAppService.getMeetingNotes(teacher.getId());

        assertEquals(1, items.size());
        assertEquals(created.noteId(), items.get(0).noteId());
        assertEquals("meeting_teacher_name", items.get(0).authorName());
        assertTrue(items.get(0).preview().contains("안건"));

        TeacherMeetingNoteDetailResponse detail = teacherAppService.getMeetingNote(teacher.getId(), created.noteId());
        assertEquals("# 안건\n- 학생 심방\n- 반 운영", detail.content());
    }

    @Test
    @DisplayName("회의록 작성 시 제목과 내용은 필수다")
    void meetingNote_create_requiresTitleAndContent() {
        Teacher teacher = saveTeacher("meeting_teacher_required", TeacherRole.TEACHER, true, false);

        IllegalArgumentException titleException = assertThrows(
                IllegalArgumentException.class,
                () -> teacherAppService.createMeetingNote(
                        teacher.getId(),
                        new CreateTeacherMeetingNoteRequest(" ", "내용")
                )
        );
        assertEquals("제목을 입력하세요.", titleException.getMessage());

        IllegalArgumentException contentException = assertThrows(
                IllegalArgumentException.class,
                () -> teacherAppService.createMeetingNote(
                        teacher.getId(),
                        new CreateTeacherMeetingNoteRequest("제목", " ")
                )
        );
        assertEquals("내용을 입력하세요.", contentException.getMessage());
        assertEquals(0, meetingNoteRepository.count());
    }

    private Teacher saveTeacher(String loginId, TeacherRole role, boolean active, boolean canCheckAllStudents) {
        return teacherRepository.save(Teacher.builder()
                .loginId(loginId)
                .passwordHash("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhash")
                .teacherName(loginId + "_name")
                .contactNumber("010-0000-0000")
                .role(role)
                .active(active)
                .canCheckAllStudents(canCheckAllStudents)
                .build());
    }

    private Student saveStudent(String name) {
        return studentRepository.save(Student.builder()
                .studentName(name)
                .contactNumber("010-1111-1111")
                .active(true)
                .build());
    }

    private Year saveYear(int yearValue) {
        return yearRepository.save(Year.builder()
                .yearValue(yearValue)
                .openToStudents(true)
                .openToTeachers(true)
                .active(true)
                .build());
    }

    private void saveYearTeacher(Year year, Teacher teacher) {
        yearTeacherRepository.save(YearTeacher.builder()
                .year(year)
                .teacher(teacher)
                .sortOrder(0)
                .active(true)
                .build());
    }

    private YearClass saveYearClass(Year year, String className) {
        return yearClassRepository.save(YearClass.builder()
                .year(year)
                .className(className)
                .sortOrder(1)
                .active(true)
                .build());
    }

    private void saveYearClassTeacher(YearClass yearClass, Teacher teacher) {
        yearClassTeacherRepository.save(YearClassTeacher.builder()
                .yearClass(yearClass)
                .teacher(teacher)
                .assignmentRole("HOMEROOM")
                .build());
    }

    private void saveYearStudent(Year year, Student student, String schoolGrade) {
        yearStudentRepository.save(YearStudent.builder()
                .year(year)
                .student(student)
                .schoolGrade(schoolGrade)
                .sortOrder(0)
                .active(true)
                .build());
    }

    private void saveYearClassStudent(Year year, YearClass yearClass, Student student) {
        yearClassStudentRepository.save(YearClassStudent.builder()
                .year(year)
                .yearClass(yearClass)
                .student(student)
                .build());
    }
}
