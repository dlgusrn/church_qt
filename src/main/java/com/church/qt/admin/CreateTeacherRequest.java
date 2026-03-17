package com.church.qt.admin;

public record CreateTeacherRequest(
        String loginId,
        String password,
        String teacherName,
        String contactNumber,
        String birthDate,
        String role,
        Boolean active
) {
}
