package com.church.qt.domain.year;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YearRepository extends JpaRepository<Year, Long> {

    Optional<Year> findByYearValue(Integer yearValue);
    List<Year> findAllByOrderByYearValueDescIdDesc();

    List<Year> findByActiveTrueOrderByYearValueDesc();

    List<Year> findByOpenToStudentsTrueAndActiveTrueOrderByYearValueDesc();

    Optional<Year> findFirstByOpenToStudentsTrueAndActiveTrueOrderByYearValueDesc();
}
