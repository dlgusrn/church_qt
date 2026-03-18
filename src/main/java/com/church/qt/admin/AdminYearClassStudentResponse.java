package com.church.qt.admin;

public record AdminYearClassStudentResponse(
        Long studentId,
        String studentName,
        String schoolGrade,
        String contactNumber,
        boolean active,
        long qtCount,
        long attitudeCount,
        long noteCount,
        long totalCount
) {
}
