package com.church.qt.admin;

public record UpdateYearClassRequest(
        String className,
        Integer sortOrder,
        boolean active
) {
}
