package com.church.qt.admin;

public record UpdateStudentRequest(
        String studentName,
        Integer schoolGrade,
        String contactNumber,
        String birthDate,
        Boolean active
) {
}
