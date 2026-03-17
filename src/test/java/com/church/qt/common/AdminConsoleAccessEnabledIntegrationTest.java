package com.church.qt.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.admin-console.enabled=true"
)
@ActiveProfiles("test")
class AdminConsoleAccessEnabledIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("admin-console enabled=true 일 때 /admin 라우트 접근이 가능하다")
    void adminRouteAccessibleWhenEnabled() throws Exception {
        HttpResponse<String> response = get("/admin");
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("admin-console enabled=true 일 때 /app/admin 라우트 접근이 가능하다")
    void appAdminRouteAccessibleWhenEnabled() throws Exception {
        HttpResponse<String> response = get("/app/admin");
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("admin-console enabled=true 일 때 정적 리소스 접근이 가능하다")
    void adminStaticAccessibleWhenEnabled() throws Exception {
        HttpResponse<String> html = get("/admin.html");
        HttpResponse<String> js = get("/admin.js");
        HttpResponse<String> css = get("/admin.css");

        assertEquals(200, html.statusCode());
        assertEquals(200, js.statusCode());
        assertEquals(200, css.statusCode());
    }

    @Test
    @DisplayName("존재하지 않는 favicon 요청은 404를 반환한다")
    void faviconReturnsNotFound() throws Exception {
        HttpResponse<String> response = get("/favicon.ico");
        assertEquals(404, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
