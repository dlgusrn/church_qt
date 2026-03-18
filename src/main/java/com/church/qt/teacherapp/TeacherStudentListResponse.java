package com.church.qt.teacherapp;

public record TeacherStudentListResponse(
        Long studentId,
        String studentName,
        String schoolGrade,
        String displayName,
        String contactNumber,
        long qtCount,
        long attitudeCount,
        long noteCount,
        long totalCount
) {
    public TeacherStudentListResponse(
            Long studentId,
            String studentName,
            String schoolGrade,
            String contactNumber,
            long qtCount,
            long attitudeCount,
            long noteCount
    ) {
        this(
                studentId,
                studentName,
                schoolGrade,
                schoolGrade == null ? studentName : studentName + "(" + schoolGrade + "학년)",
                contactNumber,
                qtCount,
                attitudeCount,
                noteCount,
                qtCount + attitudeCount + noteCount
        );
    }
}
