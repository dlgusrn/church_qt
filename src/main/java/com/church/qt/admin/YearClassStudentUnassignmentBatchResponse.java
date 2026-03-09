package com.church.qt.admin;

import java.util.List;

public record YearClassStudentUnassignmentBatchResponse(
        Long yearClassId,
        List<Long> removedStudentIds,
        List<Long> skippedStudentIds
) {
}
