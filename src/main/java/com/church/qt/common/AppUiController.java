package com.church.qt.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppUiController {

    @GetMapping("/app")
    public String appRoot() {
        return "redirect:/app/student";
    }

    @GetMapping("/app/student")
    public String studentHome() {
        return "forward:/app.html";
    }

    @GetMapping("/app/student/calendar/{studentId:\\d+}")
    public String studentCalendar() {
        return "forward:/app.html";
    }

    @GetMapping("/app/teacher/login")
    public String teacherLogin() {
        return "forward:/app.html";
    }

    @GetMapping("/app/teacher/students")
    public String teacherStudents() {
        return "forward:/app.html";
    }

    @GetMapping("/app/teacher/students/{studentId:\\d+}/calendar")
    public String teacherStudentCalendar() {
        return "forward:/app.html";
    }
}
