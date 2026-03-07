package com.church.qt.teacherapp;

public record TeacherLoginRequest(
        String loginId,
        String password
) {
}