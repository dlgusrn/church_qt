package com.church.qt.admin;

public record CreateStudentRequest(
        String studentName,
        String schoolGrade,
        String contactNumber,
        String birthDate,
        Boolean active
) {
}
