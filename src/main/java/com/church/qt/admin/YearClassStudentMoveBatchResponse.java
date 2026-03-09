package com.church.qt.admin;

import java.util.List;

public record YearClassStudentMoveBatchResponse(
        Long targetYearClassId,
        List<Long> movedStudentIds,
        List<Long> skippedStudentIds
) {
}
