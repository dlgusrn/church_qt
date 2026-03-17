package com.church.qt.admin;

import com.church.qt.domain.teacher.Teacher;

import java.time.LocalDateTime;

public record AdminTeacherResponse(
        Long teacherId,
        String loginId,
        String teacherName,
        String contactNumber,
        String birthDate,
        String role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminTeacherResponse from(Teacher teacher) {
        return new AdminTeacherResponse(
                teacher.getId(),
                teacher.getLoginId(),
                teacher.getTeacherName(),
                teacher.getContactNumber(),
                teacher.getBirthDate(),
                teacher.getEffectiveRole().name(),
                teacher.getActive(),
                teacher.getCreatedAt(),
                teacher.getUpdatedAt()
        );
    }
}
