package com.church.qt.admin;

import java.util.List;

public record YearClassStudentAssignmentBatchResponse(
        Long yearId,
        Long yearClassId,
        List<Long> assignedStudentIds,
        List<Long> skippedStudentIds
) {
}
