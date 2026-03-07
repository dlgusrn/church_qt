package com.church.qt.studentapp;

import com.church.qt.domain.student.Student;

public record StudentListResponse(
        Long studentId,
        String studentName,
        Integer schoolGrade,
        String displayName,
        String contactNumber
) {
    public static StudentListResponse from(Student student) {
        return new StudentListResponse(
                student.getId(),
                student.getStudentName(),
                student.getSchoolGrade(),
                student.getDisplayName(),
                student.getContactNumber()
        );
    }
}