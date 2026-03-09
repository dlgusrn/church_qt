package com.church.qt.admin;

import java.util.List;

public record UnassignYearClassTeacherRequest(
        List<Long> teacherIds
) {
}
