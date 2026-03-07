package com.church.qt.studentapp;

import java.util.List;

public record StudentCalendarResponse(
        Long studentId,
        String studentName,
        String displayName,
        Integer year,
        Integer month,
        StudentCalendarSummaryResponse summary,
        List<StudentCalendarDayResponse> days
) {
}