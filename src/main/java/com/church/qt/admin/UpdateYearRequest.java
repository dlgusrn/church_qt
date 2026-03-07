package com.church.qt.admin;

public record UpdateYearRequest(
        boolean openToStudents,
        boolean openToTeachers,
        boolean active
) {
}