package com.church.qt.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminUiController {

    @GetMapping("/admin")
    public String adminUi() {
        return "forward:/admin.html";
    }

    @GetMapping("/ops")
    public String opsUi() {
        return "forward:/ops.html";
    }
}
