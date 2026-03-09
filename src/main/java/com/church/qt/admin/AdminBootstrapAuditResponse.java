package com.church.qt.admin;

public record AdminBootstrapAuditResponse(
        int limit,
        int offset,
        Long actorTeacherId,
        String actionType,
        String keyword,
        String fromAt,
        String toAt
) {
}
