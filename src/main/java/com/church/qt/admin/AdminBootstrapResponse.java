package com.church.qt.admin;

import java.util.List;

public record AdminBootstrapResponse(
        String schemaVersion,
        String generatedAt,
        AdminMeResponse me,
        List<YearResponse> years,
        List<String> auditLogActionTypes,
        Integer selectedYear,
        List<AdminYearClassViewResponse> yearClasses,
        AdminTeacherListResponse teachers,
        AdminStudentListResponse students,
        AdminAuditLogListResponse auditLogs,
        AdminBootstrapPoolResponse pool,
        AdminBootstrapAuditResponse audit
) {
}
