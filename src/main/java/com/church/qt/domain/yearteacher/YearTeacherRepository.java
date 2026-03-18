package com.church.qt.domain.yearteacher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YearTeacherRepository extends JpaRepository<YearTeacher, Long> {

    Optional<YearTeacher> findByYearIdAndTeacherId(Long yearId, Long teacherId);

    boolean existsByYearIdAndTeacherId(Long yearId, Long teacherId);

    @Query("""
        select yt
        from YearTeacher yt
        join yt.teacher t
        where yt.year.yearValue = :yearValue
          and (:activeOnly = false or yt.active = true)
          and (
                :keyword is null
                or lower(t.teacherName) like lower(concat('%', :keyword, '%'))
                or lower(t.loginId) like lower(concat('%', :keyword, '%'))
          )
        order by yt.sortOrder asc, t.teacherName asc, t.id asc
    """)
    Page<YearTeacher> search(
            @Param("yearValue") Integer yearValue,
            @Param("activeOnly") boolean activeOnly,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
        select yt
        from YearTeacher yt
        join fetch yt.teacher t
        where yt.year.id = :yearId
          and (:activeOnly = false or yt.active = true)
        order by yt.sortOrder asc, t.teacherName asc, t.id asc
    """)
    List<YearTeacher> findAllByYearId(@Param("yearId") Long yearId, @Param("activeOnly") boolean activeOnly);
}
