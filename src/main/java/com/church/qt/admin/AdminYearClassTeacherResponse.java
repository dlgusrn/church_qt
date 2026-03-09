package com.church.qt.admin;

import com.church.qt.domain.yearclass.YearClassTeacher;

public record AdminYearClassTeacherResponse(
        Long teacherId,
        String teacherName,
        String contactNumber,
        String role,
        boolean active
) {
    public static AdminYearClassTeacherResponse from(YearClassTeacher yearClassTeacher) {
        return new AdminYearClassTeacherResponse(
                yearClassTeacher.getTeacher().getId(),
                yearClassTeacher.getTeacher().getTeacherName(),
                yearClassTeacher.getTeacher().getContactNumber(),
                yearClassTeacher.getTeacher().getRole().name(),
                yearClassTeacher.getTeacher().getActive()
        );
    }
}
