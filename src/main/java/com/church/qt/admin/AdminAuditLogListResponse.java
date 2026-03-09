package com.church.qt.admin;

import java.util.List;

public record AdminAuditLogListResponse(
        long totalCount,
        int limit,
        int offset,
        List<AdminAuditLogRowResponse> items
) {
}
