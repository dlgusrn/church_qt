package com.church.qt.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class AdminFrontendCorsConfig implements WebMvcConfigurer {

    @Value("${app.admin-frontend.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Value("${app.app-frontend.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private List<String> appAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] adminOrigins = normalizeOrigins(allowedOrigins).toArray(String[]::new);
        String[] appOrigins = normalizeOrigins(appAllowedOrigins).toArray(String[]::new);
        String[] teacherOrigins = mergeOrigins(allowedOrigins, appAllowedOrigins).toArray(String[]::new);

        registry.addMapping("/api/admin/**")
                .allowedOrigins(adminOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);

        registry.addMapping("/api/teacher/**")
                .allowedOrigins(teacherOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/api/students/**")
                .allowedOrigins(appOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    private Set<String> mergeOrigins(List<String> firstOrigins, List<String> secondOrigins) {
        Set<String> mergedOrigins = normalizeOrigins(firstOrigins);
        mergedOrigins.addAll(normalizeOrigins(secondOrigins));
        return mergedOrigins;
    }

    private Set<String> normalizeOrigins(List<String> origins) {
        Set<String> normalizedOrigins = new LinkedHashSet<>();
        for (String origin : origins) {
            String normalizedOrigin = origin.trim();
            if (!normalizedOrigin.isEmpty()) {
                normalizedOrigins.add(normalizedOrigin);
            }
        }
        return normalizedOrigins;
    }
}
