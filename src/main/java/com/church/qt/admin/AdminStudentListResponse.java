package com.church.qt.admin;

import java.util.List;

public record AdminStudentListResponse(
        long totalCount,
        int limit,
        int offset,
        List<AdminStudentResponse> items
) {
}
