package com.church.qt.studentapp;

public record StudentCalendarDayResponse(
        String date,
        boolean qtChecked,
        boolean attitudeChecked,
        boolean noteChecked,
        boolean isToday,
        boolean isBirthday
) {
}
