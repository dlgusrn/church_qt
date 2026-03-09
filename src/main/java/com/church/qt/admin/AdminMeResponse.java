package com.church.qt.admin;

import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRole;

public record AdminMeResponse(
        Long teacherId,
        String loginId,
        String teacherName,
        String contactNumber,
        TeacherRole role,
        boolean active
) {
    public static AdminMeResponse from(Teacher teacher) {
        return new AdminMeResponse(
                teacher.getId(),
                teacher.getLoginId(),
                teacher.getTeacherName(),
                teacher.getContactNumber(),
                teacher.getRole(),
                teacher.getActive()
        );
    }
}
