package com.church.qt.admin;

import java.util.List;

public record UnassignYearClassStudentRequest(
        List<Long> studentIds
) {
}
