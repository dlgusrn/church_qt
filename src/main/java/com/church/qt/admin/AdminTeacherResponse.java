package com.church.qt.admin;

import com.church.qt.domain.yearteacher.YearTeacher;

import java.time.LocalDateTime;

public record AdminTeacherResponse(
        Long yearTeacherId,
        Long teacherId,
        Integer yearValue,
        String loginId,
        String teacherName,
        String contactNumber,
        String birthDate,
        String role,
        boolean canCheckAllStudents,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminTeacherResponse from(YearTeacher yearTeacher) {
        return new AdminTeacherResponse(
                yearTeacher.getId(),
                yearTeacher.getTeacher().getId(),
                yearTeacher.getYear().getYearValue(),
                yearTeacher.getTeacher().getLoginId(),
                yearTeacher.getTeacher().getTeacherName(),
                yearTeacher.getTeacher().getContactNumber(),
                yearTeacher.getTeacher().getBirthDate(),
                yearTeacher.getTeacher().getEffectiveRole().name(),
                yearTeacher.getTeacher().canCheckAllStudents(),
                yearTeacher.getActive(),
                yearTeacher.getCreatedAt(),
                yearTeacher.getUpdatedAt()
        );
    }
}
