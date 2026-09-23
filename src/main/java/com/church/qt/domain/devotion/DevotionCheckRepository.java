package com.church.qt.domain.devotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DevotionCheckRepository extends JpaRepository<DevotionCheck, Long> {

    Optional<DevotionCheck> findByYearIdAndStudentIdAndCheckDate(
            Long yearId,
            Long studentId,
            LocalDate checkDate
    );

    List<DevotionCheck> findByYearIdAndStudentIdAndCheckDateBetweenOrderByCheckDateAsc(
            Long yearId,
            Long studentId,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByYearIdAndStudentIdAndQtCheckedTrue(Long yearId, Long studentId);

    long countByYearIdAndStudentIdAndAttitudeCheckedTrue(Long yearId, Long studentId);

    @Query("""
        select coalesce(sum(dc.noteCount), 0L)
        from DevotionCheck dc
        where dc.year.id = :yearId
          and dc.student.id = :studentId
    """)
    long sumNoteCountByYearIdAndStudentId(@Param("yearId") Long yearId, @Param("studentId") Long studentId);

    @Query("select count(dc) from DevotionCheck dc where dc.year.id = :yearId and dc.student.id = :studentId and dc.qtChecked = true and dc.checkDate between :from and :to")
    long countQtByYearStudentAndDateBetween(@Param("yearId") Long yearId, @Param("studentId") Long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(dc.attitudeCount), 0L) from DevotionCheck dc where dc.year.id = :yearId and dc.student.id = :studentId and dc.checkDate between :from and :to")
    long countAttitudeByYearStudentAndDateBetween(@Param("yearId") Long yearId, @Param("studentId") Long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(dc.noteCount), 0L) from DevotionCheck dc where dc.year.id = :yearId and dc.student.id = :studentId and dc.checkDate between :from and :to")
    long sumNoteCountByYearStudentAndDateBetween(@Param("yearId") Long yearId, @Param("studentId") Long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
