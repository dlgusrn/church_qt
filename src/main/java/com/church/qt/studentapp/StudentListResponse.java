package com.church.qt.studentapp;

import com.church.qt.domain.yearstudent.YearStudent;

public record StudentListResponse(
        Long studentId,
        String studentName,
        String schoolGrade,
        String displayName,
        String contactNumber
) {
    public static StudentListResponse from(YearStudent yearStudent) {
        return new StudentListResponse(
                yearStudent.getStudent().getId(),
                yearStudent.getStudent().getStudentName(),
                yearStudent.getSchoolGrade(),
                yearStudent.buildDisplayName(),
                yearStudent.getStudent().getContactNumber()
        );
    }
}
