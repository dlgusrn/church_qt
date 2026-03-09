package com.church.qt.admin;

import com.church.qt.domain.student.Student;

public record AdminStudentResponse(
        Long studentId,
        String studentName,
        Integer schoolGrade,
        String contactNumber,
        boolean active
) {
    public static AdminStudentResponse from(Student student) {
        return new AdminStudentResponse(
                student.getId(),
                student.getStudentName(),
                student.getSchoolGrade(),
                student.getContactNumber(),
                student.getActive()
        );
    }
}
