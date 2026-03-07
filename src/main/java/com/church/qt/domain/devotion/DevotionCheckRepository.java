package com.church.qt.domain.devotion;

import org.springframework.data.jpa.repository.JpaRepository;

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

    long countByYearIdAndStudentIdAndNoteCheckedTrue(Long yearId, Long studentId);
}