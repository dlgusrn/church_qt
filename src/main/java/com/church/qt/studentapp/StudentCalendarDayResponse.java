package com.church.qt.studentapp;

public record StudentCalendarDayResponse(
        String date,
        boolean qtChecked,
        boolean attitudeChecked,
        int noteCount,
        boolean isToday,
        boolean isBirthday
) {
}
