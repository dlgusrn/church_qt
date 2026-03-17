package com.church.qt.domain.teacher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByLoginIdAndActiveTrue(String loginId);
    Optional<Teacher> findByLoginId(String loginId);
    List<Teacher> findAllByOrderByTeacherNameAscIdAsc();
    List<Teacher> findByActiveTrueOrderByTeacherNameAscIdAsc();

    @Query("""
        select t
        from Teacher t
        where (:activeOnly = false or t.active = true)
          and (
                :keyword is null
                or lower(t.teacherName) like lower(concat('%', :keyword, '%'))
                or lower(t.loginId) like lower(concat('%', :keyword, '%'))
          )
        order by t.teacherName asc, t.id asc
    """)
    Page<Teacher> search(@Param("activeOnly") boolean activeOnly, @Param("keyword") String keyword, Pageable pageable);
}
