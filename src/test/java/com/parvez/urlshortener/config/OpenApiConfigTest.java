package com.parvez.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies generated API documentation and Swagger UI against the running application. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class OpenApiConfigTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    private int port;

    @Test
    void getApiDocs_returns200AndContainsAllEndpoints() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request("/v3/api-docs"), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            var document = JsonPath.parse(response.body());
            assertThat(document.<String>read("$.info.title")).isEqualTo("URL Shortener API");
            assertOperation(document.read("$.paths['/api/v1/urls'].post"), "201", "400", "409", "500");
            assertOperation(document.read("$.paths['/api/v1/urls/{shortCode}'].get"), "200", "404", "500");
            assertOperation(document.read("$.paths['/api/v1/urls/{shortCode}'].delete"), "204", "404", "500");
            assertOperation(document.read("$.paths['/{shortCode}'].get"), "302", "404", "410", "500");
            assertThat(document.<Map<String, Object>>read(
                    "$.paths['/{shortCode}'].get.responses['302'].headers"))
                    .containsKeys("Location", "Cache-Control");
            assertThat(document.<Map<String, Object>>read(
                    "$.components.responses.BadRequest.content"))
                    .containsKey("application/problem+json");
            assertThat(document.<Map<String, Object>>read("$.components.schemas.ApiProblem.properties"))
                    .containsKeys("type", "title", "status", "detail", "instance", "errors");
        }
    }

    @Test
    void getSwaggerUi_returnsReachableHtml() throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            var response = client.send(request("/swagger-ui.html"), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                    value -> assertThat(value).contains("text/html"));
            assertThat(response.body()).contains("Swagger UI", "swagger-ui-bundle.js");
        }
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).GET().build();
    }

    private void assertOperation(Map<String, Object> operation, String... responseCodes) {
        assertThat(operation.get("summary")).isInstanceOf(String.class);
        assertThat(operation.get("summary").toString()).isNotBlank();
        assertThat((Map<?, ?>) operation.get("responses")).hasSize(responseCodes.length);
        for (String code : responseCodes) {
            assertThat(((Map<?, ?>) operation.get("responses")).containsKey(code)).isTrue();
        }
    }
}
