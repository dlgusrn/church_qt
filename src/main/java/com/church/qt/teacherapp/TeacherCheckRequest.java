package com.church.qt.teacherapp;

import java.time.LocalDate;

public record TeacherCheckRequest(
        Long studentId,
        Integer year,
        LocalDate date,
        boolean qtChecked,
        boolean attitudeChecked,
        int attitudeCount,
        int noteCount
) {
    public TeacherCheckRequest(Long studentId, Integer year, LocalDate date, boolean qtChecked, boolean attitudeChecked, int noteCount) {
        this(studentId, year, date, qtChecked, attitudeChecked, attitudeChecked ? 1 : 0, noteCount);
    }
}
