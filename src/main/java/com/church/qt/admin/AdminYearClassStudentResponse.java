package com.church.qt.admin;

public record AdminYearClassStudentResponse(
        Long studentId,
        String studentName,
        Integer schoolGrade,
        String contactNumber,
        boolean active,
        long qtCount,
        long noteCount,
        long totalCount
) {
}
