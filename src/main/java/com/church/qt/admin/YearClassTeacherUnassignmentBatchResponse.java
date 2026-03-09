package com.church.qt.admin;

import java.util.List;

public record YearClassTeacherUnassignmentBatchResponse(
        Long yearClassId,
        List<Long> removedTeacherIds,
        List<Long> skippedTeacherIds
) {
}
