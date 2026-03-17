package com.church.qt.admin;

import com.church.qt.domain.yearclass.YearClass;

import java.time.LocalDateTime;
import java.util.List;

public record AdminYearClassViewResponse(
        Long yearClassId,
        Long yearId,
        Integer yearValue,
        String className,
        Integer sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AdminYearClassTeacherResponse> teachers,
        List<AdminYearClassStudentResponse> students
) {
    public static AdminYearClassViewResponse of(
            YearClass yearClass,
            List<AdminYearClassTeacherResponse> teachers,
            List<AdminYearClassStudentResponse> students
    ) {
        return new AdminYearClassViewResponse(
                yearClass.getId(),
                yearClass.getYear().getId(),
                yearClass.getYear().getYearValue(),
                yearClass.getClassName(),
                yearClass.getSortOrder(),
                yearClass.getActive(),
                yearClass.getCreatedAt(),
                yearClass.getUpdatedAt(),
                teachers,
                students
        );
    }
}
