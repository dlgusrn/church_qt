package com.church.qt.teacherapp;

import com.church.qt.common.ChangePasswordRequest;
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

    @GetMapping("/me/students")
    public List<TeacherStudentListResponse> getMyStudents(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam Integer year
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return teacherAppService.getStudents(teacherId, year);
    }

    @PostMapping("/check")
    public void updateMyCheck(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody TeacherCheckRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        teacherAppService.updateCheck(teacherId, request);
    }

    @PostMapping("/me/password")
    public TeacherLoginResponse changeMyPassword(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody ChangePasswordRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return teacherAppService.changePassword(teacherId, request);
    }

}
