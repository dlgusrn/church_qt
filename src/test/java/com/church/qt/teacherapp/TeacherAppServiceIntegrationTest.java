package com.church.qt.teacherapp;

import com.church.qt.domain.devotion.DevotionCheckRepository;
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

    @Test
    @DisplayName("전체 학생 체크 권한이 있는 교사는 연도 전체 학생 목록을 조회할 수 있다")
    void getStudents_withGlobalCheckAccess_returnsAllYearStudents() {
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
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 없는 교사는 담당 학생만 조회한다")
    void getStudents_withoutGlobalCheckAccess_returnsAssignedStudentsOnly() {
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

        assertEquals(1, responses.size());
        assertEquals("담당학생", responses.get(0).studentName());
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 있는 교사는 담당 반이 아니어도 체크할 수 있다")
    void updateCheck_withGlobalCheckAccess_allowsUnassignedStudent() {
        Teacher teacher = saveTeacher("check_all_teacher", TeacherRole.TEACHER, true, true);
        Year year = saveYear(2028);
        saveYearTeacher(year, teacher);
        YearClass otherClass = saveYearClass(year, "소망반");

        Student student = saveStudent("전체권한학생");
        saveYearStudent(year, student, "6");
        saveYearClassStudent(year, otherClass, student);

        teacherAppService.updateCheck(
                teacher.getId(),
                new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2028, 3, 15), true, false, false)
        );

        assertEquals(1L, devotionCheckRepository.countByYearIdAndStudentIdAndQtCheckedTrue(year.getId(), student.getId()));
    }

    @Test
    @DisplayName("전체 학생 체크 권한이 없는 교사는 담당 반이 아니면 체크할 수 없다")
    void updateCheck_withoutGlobalCheckAccess_rejectsUnassignedStudent() {
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
                        new TeacherCheckRequest(student.getId(), year.getYearValue(), LocalDate.of(2029, 3, 16), true, false, false)
                )
        );

        assertEquals("해당 학생은 이 교사가 관리하는 학생이 아닙니다.", exception.getMessage());
        assertFalse(devotionCheckRepository.findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), LocalDate.of(2029, 3, 16)).isPresent());
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
