package com.church.qt.admin;

import com.church.qt.domain.student.Student;

import java.time.LocalDateTime;

public record AdminStudentResponse(
        Long studentId,
        String studentName,
        Integer schoolGrade,
        String contactNumber,
        String birthDate,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminStudentResponse from(Student student) {
        return new AdminStudentResponse(
                student.getId(),
                student.getStudentName(),
                student.getSchoolGrade(),
                student.getContactNumber(),
                student.getBirthDate(),
                student.getActive(),
                student.getCreatedAt(),
                student.getUpdatedAt()
        );
    }
}
