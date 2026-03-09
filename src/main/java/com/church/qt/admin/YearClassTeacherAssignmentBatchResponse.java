package com.church.qt.admin;

import java.util.List;

public record YearClassTeacherAssignmentBatchResponse(
        Long yearClassId,
        List<Long> assignedTeacherIds,
        List<Long> skippedTeacherIds
) {
}
