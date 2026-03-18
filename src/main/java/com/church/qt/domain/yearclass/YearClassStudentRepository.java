package com.church.qt.domain.yearclass;

import com.church.qt.domain.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YearClassStudentRepository extends JpaRepository<YearClassStudent, Long> {

    List<YearClassStudent> findByYearId(Long yearId);

    boolean existsByYearIdAndStudentId(Long yearId, Long studentId);
    List<YearClassStudent> findByYearIdAndStudentIdIn(Long yearId, List<Long> studentIds);
    List<YearClassStudent> findByYearClassIdAndStudentIdIn(Long yearClassId, List<Long> studentIds);
    List<YearClassStudent> findByYearClassIdInOrderByYearClassIdAscStudentStudentNameAsc(List<Long> yearClassIds);

    List<YearClassStudent> findByYearClassId(Long yearClassId);

    List<YearClassStudent> findByStudent(Student student);

    Optional<YearClassStudent> findFirstByYearIdAndStudentId(Long yearId, Long studentId);
}
