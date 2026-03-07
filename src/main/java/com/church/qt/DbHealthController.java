package com.church.qt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DbHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DbHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db-health")
    public Map<String, Object> dbHealth() {
        return jdbcTemplate.queryForMap(
                "SELECT DATABASE() AS db, NOW() AS now"
        );
    }
}