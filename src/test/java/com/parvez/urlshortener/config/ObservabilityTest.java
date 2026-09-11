package com.parvez.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies Docker-profile log output and operational metrics over real HTTP. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("docker")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    private int port;

    @Test
    void metrics_applicationStarted_exposesCatalogAndMeasurements() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var catalog = client.send(request("/actuator/metrics").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(catalog.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<List<String>>read(catalog.body(), "$.names"))
                    .contains("jvm.memory.used", "process.uptime");

            var metric = client.send(request("/actuator/metrics/jvm.memory.used").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(metric.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(metric.body(), "$.name")).isEqualTo("jvm.memory.used");
            assertThat(JsonPath.<List<Object>>read(metric.body(), "$.measurements")).isNotEmpty();
        }
    }

    @Test
    void businessEvents_dockerProfile_emitsSafeJsonAtInfo(CapturedOutput output) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var created = client.send(request("/api/v1/urls").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"originalUrl\":\"https://example.com/?token=private-secret\",\"customAlias\":\"observe-link\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(client.send(request("/observe-link").GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
            assertThat(client.send(request("/api/v1/urls/observe-link").DELETE().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
        }

        var events = output.getOut().lines()
                .filter(line -> line.contains("short URL") && line.contains("observe-link")).toList();
        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(line -> {
            var json = JsonPath.parse(line);
            assertThat(json.<String>read("$.log.level")).isEqualTo("INFO");
            assertThat(json.<String>read("$.service.name")).isEqualTo("url-shortener");
            assertThat(json.<String>read("$['@timestamp']")).isNotBlank();
            assertThat(line).doesNotContain("private-secret", "https://example.com");
        });
        assertThat(events.stream().map(line -> JsonPath.<String>read(line, "$.message")))
                .containsExactly("Created short URL with custom alias observe-link",
                        "Resolved short URL with code observe-link", "Deleted short URL with code observe-link");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }
}
