package com.church.qt.admin;

public record AdminBootstrapPoolResponse(
        String keyword,
        boolean activeOnly,
        int limit,
        int offset
) {
}
