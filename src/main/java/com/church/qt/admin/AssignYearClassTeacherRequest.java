package com.church.qt.admin;

import java.util.List;

public record AssignYearClassTeacherRequest(
        List<Long> teacherIds
) {
}
