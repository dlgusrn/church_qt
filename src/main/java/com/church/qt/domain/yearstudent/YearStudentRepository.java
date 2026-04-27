package com.church.qt.domain.yearstudent;

import com.church.qt.domain.student.Student;
import com.church.qt.teacherapp.TeacherStudentListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YearStudentRepository extends JpaRepository<YearStudent, Long> {

    Optional<YearStudent> findByYearIdAndStudentId(Long yearId, Long studentId);

    boolean existsByYearIdAndStudentId(Long yearId, Long studentId);

    @Query("""
        select ys
        from YearStudent ys
        join ys.student s
        where ys.year.yearValue = :yearValue
          and (:activeOnly = false or ys.active = true)
          and (
                :keyword is null
                or lower(s.studentName) like lower(concat('%', :keyword, '%'))
          )
        order by ys.sortOrder asc, ys.schoolGrade desc, s.studentName asc, s.id asc
    """)
    Page<YearStudent> search(
            @Param("yearValue") Integer yearValue,
            @Param("activeOnly") boolean activeOnly,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
        select ys
        from YearStudent ys
        join fetch ys.student s
        where ys.year.id = :yearId
          and (:activeOnly = false or ys.active = true)
        order by ys.sortOrder asc, ys.schoolGrade desc, s.studentName asc, s.id asc
    """)
    List<YearStudent> findAllByYearId(@Param("yearId") Long yearId, @Param("activeOnly") boolean activeOnly);

    @Query("""
        select ys
        from YearStudent ys
        join fetch ys.student s
        where ys.year.id = :yearId
          and ys.active = true
        order by ys.schoolGrade desc, s.studentName asc, s.id asc
    """)
    List<YearStudent> findActiveByYearIdOrderByGradeDescNameAsc(@Param("yearId") Long yearId);

    @Query("""
        select ys
        from YearStudent ys
        join fetch ys.student s
        where ys.year.id in :yearIds
          and ys.student.id in :studentIds
    """)
    List<YearStudent> findByYearIdInAndStudentIdIn(
            @Param("yearIds") List<Long> yearIds,
            @Param("studentIds") List<Long> studentIds
    );

    @Query("""
        select new com.church.qt.teacherapp.TeacherStudentListResponse(
            s.id,
            s.studentName,
            ys.schoolGrade,
            s.contactNumber,
            case when exists (
                select 1
                from YearClassTeacher yct
                join yct.yearClass yc
                join YearClassStudent ycs on ycs.yearClass = yc and ycs.year = yc.year
                where yct.teacher.id = :teacherId
                  and yc.year = ys.year
                  and yc.active = true
                  and ycs.student = s
            ) then true else false end,
            coalesce(sum(case when dc.qtChecked = true then 1 else 0 end), 0L),
            coalesce(sum(case when dc.attitudeChecked = true then 1 else 0 end), 0L),
            coalesce(sum(dc.noteCount), 0L)
        )
        from YearStudent ys
        join ys.student s
        left join DevotionCheck dc on dc.student = s and dc.year = ys.year
        where ys.year.yearValue = :yearValue
          and ys.active = true
          and s.active = true
        group by s.id, s.studentName, ys.schoolGrade, s.contactNumber
        order by ys.schoolGrade desc, s.studentName asc, s.id asc
    """)
    List<TeacherStudentListResponse> findTeacherStudentsForYear(@Param("teacherId") Long teacherId, @Param("yearValue") Integer yearValue);

    List<YearStudent> findByStudent(Student student);
}
