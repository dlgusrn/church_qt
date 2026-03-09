package com.church.qt.admin;

import com.church.qt.domain.teacher.Teacher;

public record AdminTeacherResponse(
        Long teacherId,
        String loginId,
        String teacherName,
        String contactNumber,
        String role,
        boolean active
) {
    public static AdminTeacherResponse from(Teacher teacher) {
        return new AdminTeacherResponse(
                teacher.getId(),
                teacher.getLoginId(),
                teacher.getTeacherName(),
                teacher.getContactNumber(),
                teacher.getRole().name(),
                teacher.getActive()
        );
    }
}
