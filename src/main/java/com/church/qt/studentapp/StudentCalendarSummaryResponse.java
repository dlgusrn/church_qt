package com.church.qt.studentapp;

public record StudentCalendarSummaryResponse(
        long qtCount,
        long noteCount,
        long totalCount
) {
}