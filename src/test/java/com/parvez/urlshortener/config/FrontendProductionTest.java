package com.parvez.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies that the actual Vite production output is available over HTTP. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.base-url=https://links.example.test")
@ActiveProfiles("test")
@Testcontainers
class FrontendProductionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    private int port;

    @Test
    void frontendConfig_configuredDeployment_returnsPublicBaseUrl() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request("/ui/config"), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
            assertThat(JsonPath.<String>read(response.body(), "$.publicBaseUrl"))
                    .isEqualTo("https://links.example.test");
        }
    }

    @Test
    void frontendProductionBuild_isServedBySpringBoot() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request("/"), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/html");
            assertThat(response.body()).contains("Shortly", "id=\"root\"").doesNotContain("/src/main.tsx");

            String policy = response.headers().firstValue("Content-Security-Policy").orElseThrow();
            var nonce = Pattern.compile("'nonce-([^']+)'").matcher(policy);
            assertThat(nonce.find()).isTrue();
            var scripts = Pattern.compile("<script([^>]*)>([\\s\\S]*?)</script>").matcher(response.body());
            int scriptCount = 0;
            while (scripts.find()) {
                assertThat(scripts.group(1)).contains("nonce=\"" + nonce.group(1) + "\"", "src=");
                assertThat(scripts.group(2)).isBlank();
                scriptCount++;
            }
            assertThat(scriptCount).isGreaterThanOrEqualTo(2);
            assertThat(policy).doesNotContain("unsafe-inline", "unsafe-eval");

            var assets = Pattern.compile("(?:src|href)=\"(/assets/[^\"]+\\.(?:js|css))\"")
                    .matcher(response.body());
            int count = 0;
            while (assets.find()) {
                String path = assets.group(1);
                var asset = client.send(request(path), HttpResponse.BodyHandlers.ofString());
                assertThat(asset.statusCode()).isEqualTo(200);
                assertThat(asset.body()).isNotBlank().doesNotContain("<!doctype html>");
                assertThat(asset.headers().firstValue("Content-Type").orElseThrow())
                        .contains(path.endsWith(".css") ? "text/css" : "javascript");
                count++;
            }
            assertThat(count).isGreaterThanOrEqualTo(2);

            var unknownCode = client.send(request("/unknown014"), HttpResponse.BodyHandlers.ofString());
            assertThat(unknownCode.statusCode()).isEqualTo(404);
            assertThat(unknownCode.headers().firstValue("Content-Type").orElseThrow())
                    .contains("application/problem+json");
        }
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).GET().build();
    }
}
