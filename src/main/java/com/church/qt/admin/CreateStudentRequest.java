package com.church.qt.admin;

public record CreateStudentRequest(
        String studentName,
        Integer schoolGrade,
        String contactNumber,
        String birthDate,
        Boolean active
) {
}
