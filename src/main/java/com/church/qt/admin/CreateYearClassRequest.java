package com.church.qt.admin;

public record CreateYearClassRequest(
        Integer yearValue,
        String className,
        Integer sortOrder,
        boolean active
) {
}