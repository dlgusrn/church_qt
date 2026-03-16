package com.church.qt.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AdminConsoleAccessFilter extends OncePerRequestFilter {

    private static final Set<String> ADMIN_CONSOLE_PATHS = Set.of(
            "/admin",
            "/admin.html",
            "/admin.js",
            "/admin.css",
            "/ops",
            "/ops.html",
            "/ops.js",
            "/ops.css"
    );

    @Value("${app.admin-console.enabled:true}")
    private boolean adminConsoleEnabled;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!adminConsoleEnabled && ADMIN_CONSOLE_PATHS.contains(path)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
