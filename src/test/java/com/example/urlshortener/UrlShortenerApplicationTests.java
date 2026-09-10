package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.util.Base62Encoder;
import com.jayway.jsonpath.JsonPath;
import java.sql.DriverManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies bootstrap and health against an isolated PostgreSQL database. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class UrlShortenerApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    private int port;

    @Test
    void contextLoads() {
        // Spring context should start without error (TICKET-001 acceptance criterion).
    }

    @Test
    void health_applicationStarted_returns200AndUp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        }
    }
    @Test
    void createShortUrl_validRequest_commitsGeneratedCodeToDatabase() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString("{\"originalUrl\":\"https://example.com/integration\"}"))
                .build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(201);
            String code = JsonPath.read(response.body(), "$.shortCode");
            String shortUrl = JsonPath.read(response.body(), "$.shortUrl");
            assertThat(response.headers().firstValue("Location")).contains(shortUrl);
            assertThat(shortUrl).endsWith("/" + code);
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.prepareStatement(
                            "select id, original_url, created_at, custom_alias, click_count, expires_at from short_url where short_code = ?")) {
                statement.setString(1, code);
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(Base62Encoder.encode(row.getLong("id"))).isEqualTo(code);
                    assertThat(row.getString("original_url")).isEqualTo("https://example.com/integration");
                    assertThat(row.getTimestamp("created_at")).isNotNull();
                    assertThat(row.getBoolean("custom_alias")).isFalse();
                    assertThat(row.getTimestamp("expires_at")).isNull();
                    assertThat(row.getLong("click_count")).isZero();
                    assertThat(row.next()).isFalse();
                }
            }
        }
    }

    @Test
    void createShortUrl_customAlias_persistsAliasAndRejectsDuplicate() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"originalUrl\":\"https://example.com/custom\",\"customAlias\":\"My_link-1\"}"))
                .build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(response.body(), "$.shortCode")).isEqualTo("My_link-1");
            assertThat(response.headers().firstValue("Location")).hasValueSatisfying(
                    location -> assertThat(location).endsWith("/My_link-1"));
            var duplicate = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(JsonPath.<String>read(duplicate.body(), "$.detail"))
                    .isEqualTo("Alias 'My_link-1' is already taken");
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.prepareStatement(
                            "select original_url, custom_alias, expires_at from short_url where short_code = ?")) {
                statement.setString(1, "My_link-1");
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("original_url")).isEqualTo("https://example.com/custom");
                    assertThat(row.getBoolean("custom_alias")).isTrue();
                    assertThat(row.getTimestamp("expires_at")).isNull();
                    assertThat(row.next()).isFalse();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createShortUrl_futureExpiry_commitsExpiryToDatabase(boolean customAlias) throws Exception {
        var expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        String aliasJson = customAlias ? "\"expires-link\"" : "null";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"originalUrl":"https://example.com/expiry","customAlias":%s,"expiresAt":"%s"}
                        """.formatted(aliasJson, expiresAt)))
                .build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(response.body(), "$.expiresAt")).isEqualTo(expiresAt.toString());
            String code = JsonPath.read(response.body(), "$.shortCode");
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.prepareStatement(
                            "select expires_at, custom_alias from short_url where short_code = ?")) {
                statement.setString(1, code);
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getTimestamp("expires_at").toInstant()).isEqualTo(expiresAt);
                    assertThat(row.getBoolean("custom_alias")).isEqualTo(customAlias);
                    assertThat(row.next()).isFalse();
                }
            }
        }
    }

}
