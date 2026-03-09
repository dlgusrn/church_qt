package com.church.qt.admin;

import com.church.qt.domain.yearclass.YearClassStudent;

public record AdminYearClassStudentResponse(
        Long studentId,
        String studentName,
        Integer schoolGrade,
        String contactNumber,
        boolean active
) {
    public static AdminYearClassStudentResponse from(YearClassStudent yearClassStudent) {
        return new AdminYearClassStudentResponse(
                yearClassStudent.getStudent().getId(),
                yearClassStudent.getStudent().getStudentName(),
                yearClassStudent.getStudent().getSchoolGrade(),
                yearClassStudent.getStudent().getContactNumber(),
                yearClassStudent.getStudent().getActive()
        );
    }
}
