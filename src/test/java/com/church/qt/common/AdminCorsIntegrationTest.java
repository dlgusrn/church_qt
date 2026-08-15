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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.admin-console.enabled=false",
                "app.admin-frontend.allowed-origins=http://admin.example.com",
                "app.app-frontend.allowed-origins=http://app.example.com"
        }
)
@ActiveProfiles("test")
class AdminCorsIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("허용된 Origin의 preflight OPTIONS 요청에 CORS 헤더가 반환된다")
    void preflight_allowsConfiguredOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/admin/years"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://admin.example.com")
                .header("Access-Control-Request-Method", "GET")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("http://admin.example.com", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    @DisplayName("허용되지 않은 Origin은 CORS 허용 헤더가 반환되지 않는다")
    void preflight_disallowsUnknownOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/admin/years"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://evil.example.com")
                .header("Access-Control-Request-Method", "GET")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertNotEquals("http://evil.example.com", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    @DisplayName("교사 로그인 preflight는 관리자와 앱 Origin을 모두 허용한다")
    void teacherLoginPreflight_allowsAdminAndAppOrigins() throws Exception {
        assertPreflightAllowed("/api/teacher/login", "POST", "http://admin.example.com");
        assertPreflightAllowed("/api/teacher/login", "POST", "http://app.example.com");
    }

    @Test
    @DisplayName("교사 API preflight는 앱 Origin을 허용한다")
    void teacherApiPreflight_allowsAppOrigin() throws Exception {
        assertPreflightAllowed("/api/teacher/meeting-notes", "POST", "http://app.example.com");
    }

    private void assertPreflightAllowed(String path, String method, String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }
}
