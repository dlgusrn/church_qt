package com.church.qt.admin;

public record UpdateStudentRequest(
        String studentName,
        String schoolGrade,
        String contactNumber,
        String birthDate,
        Boolean active
) {
}
