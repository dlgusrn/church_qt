package com.church.qt.domain.yearclass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YearClassRepository extends JpaRepository<YearClass, Long> {

    List<YearClass> findByYearIdOrderBySortOrderAscIdAsc(Long yearId);
    List<YearClass> findByYearIdAndActiveTrueOrderBySortOrderAscIdAsc(Long yearId);
}
