package com.church.qt.admin;

import com.church.qt.domain.year.Year;

import java.time.LocalDateTime;

public record YearResponse(
        Long id,
        Integer yearValue,
        boolean openToStudents,
        boolean openToTeachers,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static YearResponse from(Year year) {
        return new YearResponse(
                year.getId(),
                year.getYearValue(),
                year.getOpenToStudents(),
                year.getOpenToTeachers(),
                year.getActive(),
                year.getCreatedAt(),
                year.getUpdatedAt()
        );
    }
}
