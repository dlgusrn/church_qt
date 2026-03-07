package com.church.qt.teacherapp;

public record TeacherStudentListResponse(
        Long studentId,
        String studentName,
        Integer schoolGrade,
        String displayName,
        String contactNumber,
        long qtCount,
        long noteCount,
        long totalCount
) {
    public TeacherStudentListResponse(
            Long studentId,
            String studentName,
            Integer schoolGrade,
            String contactNumber,
            long qtCount,
            long noteCount
    ) {
        this(
                studentId,
                studentName,
                schoolGrade,
                schoolGrade == null ? studentName : studentName + "(" + schoolGrade + "학년)",
                contactNumber,
                qtCount,
                noteCount,
                qtCount + noteCount
        );
    }
}