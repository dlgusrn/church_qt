package com.church.qt.domain.yearclass;

import com.church.qt.teacherapp.TeacherStudentListResponse;
import com.church.qt.domain.yearstudent.YearStudent;
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
        select new com.church.qt.teacherapp.TeacherStudentListResponse(
            s.id,
            s.studentName,
            ys.schoolGrade,
            s.contactNumber,
            coalesce(sum(case when dc.qtChecked = true then 1 else 0 end), 0L),
            coalesce(sum(case when dc.attitudeChecked = true then 1 else 0 end), 0L),
            coalesce(sum(case when dc.noteChecked = true then 1 else 0 end), 0L)
        )
        from YearClassTeacher yct
        join yct.yearClass yc
        join YearClassStudent ycs on ycs.yearClass = yc and ycs.year = yc.year
        join ycs.student s
        join YearStudent ys on ys.year = yc.year and ys.student = s
        left join DevotionCheck dc on dc.student = s and dc.year = yc.year
        where yct.teacher.id = :teacherId
          and yc.year.yearValue = :yearValue
          and yc.active = true
          and ys.active = true
          and s.active = true
        group by s.id, s.studentName, ys.schoolGrade, s.contactNumber
        order by ys.schoolGrade desc, s.studentName asc
    """)
    List<TeacherStudentListResponse> findTeacherStudents(Long teacherId, Integer yearValue);
}
