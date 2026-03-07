package com.church.qt.teacherapp;

import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRole;

public record TeacherLoginResponse(
        Long teacherId,
        String teacherName,
        TeacherRole role,
        String accessToken
) {
    public static TeacherLoginResponse from(Teacher teacher, String accessToken) {
        return new TeacherLoginResponse(
                teacher.getId(),
                teacher.getTeacherName(),
                teacher.getRole(),
                accessToken
        );
    }
}