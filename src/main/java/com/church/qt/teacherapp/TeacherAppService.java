package com.church.qt.teacherapp;

import com.church.qt.domain.devotion.DevotionCheck;
import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.student.StudentRepository;
import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRepository;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClassStudentRepository;
import com.church.qt.domain.yearclass.YearClassTeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherAppService {

    private final DevotionCheckRepository devotionCheckRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final YearRepository yearRepository;
    private final YearClassStudentRepository yearClassStudentRepository;
    private final YearClassTeacherRepository yearClassTeacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public TeacherLoginResponse login(TeacherLoginRequest request) {
        Teacher teacher = teacherRepository.findByLoginIdAndActiveTrue(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), teacher.getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return TeacherLoginResponse.from(teacher);
    }

    @Transactional(readOnly = true)
    public List<TeacherStudentListResponse> getStudents(Long teacherId, Integer yearValue) {
        return yearClassTeacherRepository.findTeacherStudents(teacherId, yearValue);
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

        if (!yearClassTeacherRepository.existsManageableStudent(teacherId, request.studentId(), request.year())) {
            throw new IllegalArgumentException("해당 학생은 이 교사가 관리하는 학생이 아닙니다.");
        }

        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));

        DevotionCheck check = devotionCheckRepository
                .findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), request.date())
                .orElse(null);

        if (!request.qtChecked() && !request.noteChecked()) {
            if (check != null) {
                devotionCheckRepository.delete(check);
            }
            return;
        }

        if (check == null) {
            check = DevotionCheck.builder()
                    .year(year)
                    .student(student)
                    .checkDate(request.date())
                    .qtChecked(request.qtChecked())
                    .noteChecked(request.noteChecked())
                    .checkedByTeacher(teacher)
                    .build();

            devotionCheckRepository.save(check);
        } else {
            check.updateChecks(request.qtChecked(), request.noteChecked(), teacher);
        }
    }
}