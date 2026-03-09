package com.church.qt.admin;

import java.util.List;

public record MoveYearClassStudentRequest(
        List<Long> studentIds
) {
}
