package com.church.qt.domain.yearclass;

import com.church.qt.domain.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YearClassStudentRepository extends JpaRepository<YearClassStudent, Long> {

    List<YearClassStudent> findByYearIdOrderByStudentSchoolGradeDescStudentStudentNameAsc(Long yearId);

    List<YearClassStudent> findByYearIdAndStudentActiveTrueOrderByStudentSchoolGradeDescStudentStudentNameAsc(Long yearId);

    boolean existsByYearIdAndStudentId(Long yearId, Long studentId);

    List<YearClassStudent> findByYearClassId(Long yearClassId);

    List<YearClassStudent> findByStudent(Student student);
}