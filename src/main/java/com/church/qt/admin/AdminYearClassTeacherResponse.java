package com.church.qt.admin;

import com.church.qt.domain.yearclass.YearClassTeacher;

public record AdminYearClassTeacherResponse(
        Long teacherId,
        String teacherName,
        String contactNumber,
        String role,
        String assignmentRole,
        boolean active
) {
    public static AdminYearClassTeacherResponse from(YearClassTeacher yearClassTeacher) {
        return from(yearClassTeacher, yearClassTeacher.getTeacher().getActive());
    }

    public static AdminYearClassTeacherResponse from(YearClassTeacher yearClassTeacher, boolean active) {
        return new AdminYearClassTeacherResponse(
                yearClassTeacher.getTeacher().getId(),
                yearClassTeacher.getTeacher().getTeacherName(),
                yearClassTeacher.getTeacher().getContactNumber(),
                yearClassTeacher.getTeacher().getEffectiveRole().name(),
                yearClassTeacher.getAssignmentRole(),
                active
        );
    }
}
