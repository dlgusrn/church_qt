package com.church.qt.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class AdminFrontendCorsConfig implements WebMvcConfigurer {

    @Value("${app.admin-frontend.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Value("${app.app-frontend.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private List<String> appAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/admin/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);

        registry.addMapping("/api/teacher/login")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/api/students/**")
                .allowedOrigins(appAllowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/api/teacher/me/**")
                .allowedOrigins(appAllowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/api/teacher/check")
                .allowedOrigins(appAllowedOrigins.toArray(String[]::new))
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
