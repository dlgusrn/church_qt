package com.church.qt.teacherapp;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teacher")
public class TeacherAppController {

    private final TeacherAppService teacherAppService;

    @PostMapping("/login")
    public TeacherLoginResponse login(@RequestBody TeacherLoginRequest request) {
        return teacherAppService.login(request);
    }

    @GetMapping("/{teacherId}/students")
    public List<TeacherStudentListResponse> getStudents(
            @PathVariable Long teacherId,
            @RequestParam Integer year
    ) {
        return teacherAppService.getStudents(teacherId, year);
    }

    @PostMapping("/{teacherId}/check")
    public void updateCheck(
            @PathVariable Long teacherId,
            @RequestBody TeacherCheckRequest request
    ) {
        teacherAppService.updateCheck(teacherId, request);
    }
}