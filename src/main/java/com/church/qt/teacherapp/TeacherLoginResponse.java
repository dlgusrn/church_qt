package com.church.qt.teacherapp;

import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRole;

public record TeacherLoginResponse(
        Long teacherId,
        String teacherName,
        TeacherRole role,
        String accessToken,
        boolean passwordChangeRequired
) {
    public static TeacherLoginResponse from(Teacher teacher, String accessToken) {
        return new TeacherLoginResponse(
                teacher.getId(),
                teacher.getTeacherName(),
                teacher.getEffectiveRole(),
                accessToken,
                Boolean.TRUE.equals(teacher.getPasswordChangeRequired())
        );
    }
}
