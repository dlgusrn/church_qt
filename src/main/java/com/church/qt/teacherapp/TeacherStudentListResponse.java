package com.church.qt.teacherapp;

public record TeacherStudentListResponse(
        Long studentId,
        String studentName,
        String schoolGrade,
        String displayName,
        String contactNumber,
        boolean myClassStudent,
        String myClassName,
        String assignedClassName,
        long qtCount,
        long attitudeCount,
        long noteCount,
        long deductionCount,
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
            String assignedClassName,
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
                assignedClassName,
                qtCount,
                attitudeCount,
                noteCount,
                0,
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
                assignedClassName,
                qtCount,
                attitudeCount,
                noteCount,
                deductionCount,
                totalCount
        );
    }

    public TeacherStudentListResponse withClassNames(String myClassName, String assignedClassName) {
        return new TeacherStudentListResponse(
                studentId,
                studentName,
                schoolGrade,
                displayName,
                contactNumber,
                myClassStudent,
                myClassName,
                assignedClassName,
                qtCount,
                attitudeCount,
                noteCount,
                deductionCount,
                totalCount
        );
    }

    public TeacherStudentListResponse withDeductionCount(long deductionCount) {
        return new TeacherStudentListResponse(studentId, studentName, schoolGrade, displayName, contactNumber, myClassStudent,
                myClassName, assignedClassName, qtCount, attitudeCount, noteCount, deductionCount,
                qtCount + attitudeCount + noteCount + deductionCount);
    }
}
