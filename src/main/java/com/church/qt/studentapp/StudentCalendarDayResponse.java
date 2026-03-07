package com.church.qt.studentapp;

public record StudentCalendarDayResponse(
        String date,
        boolean qtChecked,
        boolean noteChecked,
        boolean isToday
) {
}