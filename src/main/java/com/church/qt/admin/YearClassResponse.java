package com.church.qt.admin;

import com.church.qt.domain.yearclass.YearClass;

public record YearClassResponse(
        Long id,
        Long yearId,
        Integer yearValue,
        String className,
        Integer sortOrder,
        boolean active
) {
    public static YearClassResponse from(YearClass yearClass) {
        return new YearClassResponse(
                yearClass.getId(),
                yearClass.getYear().getId(),
                yearClass.getYear().getYearValue(),
                yearClass.getClassName(),
                yearClass.getSortOrder(),
                yearClass.getActive()
        );
    }
}