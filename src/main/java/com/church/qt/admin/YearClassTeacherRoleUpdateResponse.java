package com.church.qt.admin;

public record YearClassTeacherRoleUpdateResponse(
        Long yearClassId,
        Long teacherId,
        String assignmentRole
) {
}
