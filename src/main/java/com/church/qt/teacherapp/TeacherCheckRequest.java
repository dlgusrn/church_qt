package com.church.qt.teacherapp;

import java.time.LocalDate;

public record TeacherCheckRequest(
        Long studentId,
        Integer year,
        LocalDate date,
        boolean qtChecked,
        boolean attitudeChecked,
        int noteCount
) {
}
