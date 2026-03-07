package com.church.qt.admin;

import com.church.qt.teacherapp.TeacherAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final TeacherAppService teacherAppService;
    private final AdminService adminService;

    @PostMapping("/years")
    public YearResponse createYear(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateYearRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.createYear(teacherId, request);
    }

    @PatchMapping("/years/{yearId}")
    public YearResponse updateYear(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long yearId,
            @RequestBody UpdateYearRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.updateYear(teacherId, yearId, request);
    }

    @PostMapping("/year-classes")
    public YearClassResponse createYearClass(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateYearClassRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return adminService.createYearClass(teacherId, request);
    }
}