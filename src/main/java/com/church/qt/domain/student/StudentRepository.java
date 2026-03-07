package com.church.qt.domain.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByActiveTrueOrderBySchoolGradeDescStudentNameAsc();
}