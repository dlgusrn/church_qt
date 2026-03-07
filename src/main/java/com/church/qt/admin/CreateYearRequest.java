package com.church.qt.admin;

public record CreateYearRequest(
        Integer yearValue,
        boolean openToStudents,
        boolean openToTeachers,
        boolean active
) {
}