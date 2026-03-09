package com.church.qt.admin;

import java.util.List;

public record AssignYearClassStudentRequest(
        List<Long> studentIds
) {
}
