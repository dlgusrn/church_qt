package com.church.qt.teacherapp;

import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRole;

public record TeacherLoginResponse(
        Long teacherId,
        String teacherName,
        TeacherRole role
) {
    public static TeacherLoginResponse from(Teacher teacher) {
        return new TeacherLoginResponse(
                teacher.getId(),
                teacher.getTeacherName(),
                teacher.getRole()
        );
    }
}