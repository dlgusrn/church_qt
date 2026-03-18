package com.church.qt.admin;

import com.church.qt.domain.yearstudent.YearStudent;

import java.time.LocalDateTime;

public record AdminStudentResponse(
        Long yearStudentId,
        Long studentId,
        String studentName,
        Integer yearValue,
        String schoolGrade,
        String contactNumber,
        String birthDate,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminStudentResponse from(YearStudent yearStudent) {
        return new AdminStudentResponse(
                yearStudent.getId(),
                yearStudent.getStudent().getId(),
                yearStudent.getStudent().getStudentName(),
                yearStudent.getYear().getYearValue(),
                yearStudent.getSchoolGrade(),
                yearStudent.getStudent().getContactNumber(),
                yearStudent.getStudent().getBirthDate(),
                yearStudent.getActive(),
                yearStudent.getCreatedAt(),
                yearStudent.getUpdatedAt()
        );
    }
}
