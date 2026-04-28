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

    @GetMapping("/meeting-notes")
    public List<TeacherMeetingNoteListItemResponse> getMeetingNotes(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return teacherAppService.getMeetingNotes(teacherId);
    }

    @GetMapping("/meeting-notes/{meetingNoteId}")
    public TeacherMeetingNoteDetailResponse getMeetingNote(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long meetingNoteId
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return teacherAppService.getMeetingNote(teacherId, meetingNoteId);
    }

    @PostMapping("/meeting-notes")
    public TeacherMeetingNoteDetailResponse createMeetingNote(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CreateTeacherMeetingNoteRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return teacherAppService.createMeetingNote(teacherId, request);
    }

    @PutMapping("/meeting-notes/{meetingNoteId}")
    public TeacherMeetingNoteDetailResponse updateMeetingNote(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long meetingNoteId,
            @RequestBody CreateTeacherMeetingNoteRequest request
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        return teacherAppService.updateMeetingNote(teacherId, meetingNoteId, request);
    }

    @DeleteMapping("/meeting-notes/{meetingNoteId}")
    public void deleteMeetingNote(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long meetingNoteId
    ) {
        Long teacherId = teacherAppService.extractTeacherId(authorizationHeader);
        teacherAppService.deleteMeetingNote(teacherId, meetingNoteId);
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
