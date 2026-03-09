package com.church.qt.admin;

import java.util.List;

public record AdminTeacherListResponse(
        long totalCount,
        int limit,
        int offset,
        List<AdminTeacherResponse> items
) {
}
