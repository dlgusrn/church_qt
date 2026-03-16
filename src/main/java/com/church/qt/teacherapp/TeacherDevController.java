package com.church.qt.teacherapp;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequiredArgsConstructor
public class TeacherDevController {

    private final PasswordEncoder passwordEncoder;

    @GetMapping("/api/dev/password-hash")
    public String getPasswordHash(@RequestParam String raw) {
        return passwordEncoder.encode(raw);
    }
}
