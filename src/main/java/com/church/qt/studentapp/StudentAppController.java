package com.church.qt.studentapp;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StudentAppController {

    private final StudentAppService studentAppService;

    @GetMapping("/api/students")
    public List<StudentListResponse> getStudents() {
        return studentAppService.getStudents();
    }

    @GetMapping("/api/students/current-year")
    public StudentCurrentYearResponse getCurrentYear() {
        return studentAppService.getCurrentYear();
    }

    @GetMapping("/api/students/{studentId}/calendar")
    public StudentCalendarResponse getStudentCalendar(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return studentAppService.getStudentCalendar(studentId, year, month);
    }
}
