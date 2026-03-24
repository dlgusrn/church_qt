package com.church.qt.admin;

public record UpdateTeacherRequest(
        String teacherName,
        String contactNumber,
        String birthDate,
        String role,
        Boolean active,
        String password,
        Boolean canCheckAllStudents
) {
}
