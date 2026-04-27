package com.church.qt.domain.yearclass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YearClassTeacherRepository extends JpaRepository<YearClassTeacher, Long> {

    boolean existsByYearClassIdAndTeacherId(Long yearClassId, Long teacherId);
    Optional<YearClassTeacher> findByYearClassIdAndTeacherId(Long yearClassId, Long teacherId);
    List<YearClassTeacher> findByYearClassId(Long yearClassId);
    List<YearClassTeacher> findByYearClassIdAndTeacherIdIn(Long yearClassId, List<Long> teacherIds);
    List<YearClassTeacher> findByYearClassIdInOrderByYearClassIdAscTeacherTeacherNameAsc(List<Long> yearClassIds);

    @Query("""
        select yct
        from YearClassTeacher yct
        join yct.yearClass yc
        where yc.year.id = :yearId
          and yct.teacher.id in :teacherIds
    """)
    List<YearClassTeacher> findByYearIdAndTeacherIdIn(@Param("yearId") Long yearId, @Param("teacherIds") List<Long> teacherIds);

    @Query("""
        select case when count(yct) > 0 then true else false end
        from YearClassTeacher yct
        join yct.yearClass yc
        join YearClassStudent ycs on ycs.yearClass = yc and ycs.year = yc.year
        where yct.teacher.id = :teacherId
          and ycs.student.id = :studentId
          and yc.year.yearValue = :yearValue
          and yc.active = true
          and yct.teacher.active = true
          and ycs.student.active = true
    """)
    boolean existsManageableStudent(Long teacherId, Long studentId, Integer yearValue);

    @Query("""
        select yc.className
        from YearClassTeacher yct
        join yct.yearClass yc
        where yct.teacher.id = :teacherId
          and yc.year.yearValue = :yearValue
          and yc.active = true
        order by yc.sortOrder asc, yc.id asc
    """)
    List<String> findClassNamesByTeacherIdAndYearValue(@Param("teacherId") Long teacherId, @Param("yearValue") Integer yearValue);
}
