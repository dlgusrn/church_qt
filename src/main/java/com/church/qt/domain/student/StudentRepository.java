package com.church.qt.domain.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByActiveTrueOrderBySchoolGradeDescStudentNameAsc();
    List<Student> findAllByOrderBySchoolGradeDescStudentNameAscIdAsc();
    List<Student> findByActiveTrueOrderBySchoolGradeDescStudentNameAscIdAsc();

    @Query("""
        select s
        from Student s
        where (:activeOnly = false or s.active = true)
          and (
                :keyword is null
                or lower(s.studentName) like lower(concat('%', :keyword, '%'))
          )
        order by s.schoolGrade desc, s.studentName asc, s.id asc
    """)
    Page<Student> search(@Param("activeOnly") boolean activeOnly, @Param("keyword") String keyword, Pageable pageable);
}
