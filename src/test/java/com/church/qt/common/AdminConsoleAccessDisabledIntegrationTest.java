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
        properties = "app.admin-console.enabled=false"
)
@ActiveProfiles("test")
class AdminConsoleAccessDisabledIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("admin-console enabled=false 일 때 /admin 및 정적 리소스는 404를 반환한다")
    void adminResourcesBlockedWhenDisabled() throws Exception {
        assertEquals(404, get("/admin").statusCode());
        assertEquals(404, get("/admin.html").statusCode());
        assertEquals(404, get("/admin.js").statusCode());
        assertEquals(404, get("/admin.css").statusCode());
    }

    @Test
    @DisplayName("admin-console enabled=false 여도 API 라우트 자체는 404로 막히지 않는다")
    void adminApiRouteNotBlockedByStaticFilter() throws Exception {
        HttpResponse<String> response = get("/api/admin/years");
        assertNotEquals(404, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
