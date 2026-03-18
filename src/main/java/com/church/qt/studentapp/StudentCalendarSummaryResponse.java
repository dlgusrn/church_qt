package com.church.qt.studentapp;

public record StudentCalendarSummaryResponse(
        long qtCount,
        long attitudeCount,
        long noteCount,
        long totalCount
) {
}
