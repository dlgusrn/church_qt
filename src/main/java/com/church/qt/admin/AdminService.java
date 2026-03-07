package com.church.qt.admin;

import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRepository;
import com.church.qt.domain.teacher.TeacherRole;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClass;
import com.church.qt.domain.yearclass.YearClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TeacherRepository teacherRepository;
    private final YearRepository yearRepository;
    private final YearClassRepository yearClassRepository;

    @Transactional(readOnly = true)
    public void validateAdmin(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));

        if (!teacher.getActive()) {
            throw new IllegalArgumentException("비활성화된 교사입니다.");
        }

        if (teacher.getRole() != TeacherRole.ADMIN) {
            throw new IllegalArgumentException("관리자 권한이 없습니다.");
        }
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
}