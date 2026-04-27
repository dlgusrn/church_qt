package com.church.qt.teacherapp;

public record TeacherStudentListResponse(
        Long studentId,
        String studentName,
        String schoolGrade,
        String displayName,
        String contactNumber,
        boolean myClassStudent,
        String myClassName,
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
            boolean myClassStudent,
            long qtCount,
            long attitudeCount,
            long noteCount
    ) {
        this(
                studentId,
                studentName,
                schoolGrade,
                contactNumber,
                myClassStudent,
                null,
                qtCount,
                attitudeCount,
                noteCount
        );
    }

    public TeacherStudentListResponse(
            Long studentId,
            String studentName,
            String schoolGrade,
            String contactNumber,
            boolean myClassStudent,
            String myClassName,
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
                myClassStudent,
                myClassName,
                qtCount,
                attitudeCount,
                noteCount,
                qtCount + attitudeCount + noteCount
        );
    }

    public TeacherStudentListResponse withMyClassName(String myClassName) {
        return new TeacherStudentListResponse(
                studentId,
                studentName,
                schoolGrade,
                displayName,
                contactNumber,
                myClassStudent,
                myClassName,
                qtCount,
                attitudeCount,
                noteCount,
                totalCount
        );
    }
}
