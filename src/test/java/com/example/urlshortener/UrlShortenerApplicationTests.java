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

    @Test
    void redirect_concurrentRequests_commitsEveryClick() throws Exception {
        seedRedirect("concurrent-link", null);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/concurrent-link"))
                .timeout(Duration.ofSeconds(10)).GET().build();

        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            var responses = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())).toList();
            for (var future : responses) {
                var response = future.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(302);
                assertThat(response.headers().firstValue("Location")).contains("https://example.com/redirect?q=1");
                assertThat(response.body()).isEmpty();
            }
        }
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "select click_count, last_accessed_at from short_url where short_code = ?")) {
            statement.setString(1, "concurrent-link");
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("click_count")).isEqualTo(8);
                assertThat(row.getTimestamp("last_accessed_at")).isNotNull();
            }
        }
    }

    @Test
    void redirect_expiredLink_returns410WithoutChangingAnalytics() throws Exception {
        seedRedirect("expired-link", Instant.now().minusSeconds(60));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/expired-link"))
                .timeout(Duration.ofSeconds(10)).GET().build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(410);
            assertThat(response.headers().firstValue("Location")).isEmpty();
            assertThat(JsonPath.<String>read(response.body(), "$.detail"))
                    .isEqualTo("Short URL 'expired-link' has expired");
        }
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "select click_count, last_accessed_at from short_url where short_code = ?")) {
            statement.setString(1, "expired-link");
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("click_count")).isZero();
                assertThat(row.getTimestamp("last_accessed_at")).isNull();
            }
        }
    }

    @Test
    void getStats_expiredLink_returnsStatsWithoutChangingPersistedAnalytics() throws Exception {
        seedRedirect("stats-expired", Instant.now().minusSeconds(60));
        var lastAccessedAt = Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "update short_url set click_count = 7, last_accessed_at = ? where short_code = ?")) {
            statement.setTimestamp(1, java.sql.Timestamp.from(lastAccessedAt));
            statement.setString(2, "stats-expired");
            statement.executeUpdate();
        }
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls/stats-expired"))
                .timeout(Duration.ofSeconds(10)).GET().build();

        try (var client = HttpClient.newHttpClient()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(JsonPath.<Integer>read(response.body(), "$.clickCount")).isEqualTo(7);
                assertThat(JsonPath.<String>read(response.body(), "$.lastAccessedAt"))
                        .isEqualTo(lastAccessedAt.toString());
            }
        }
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "select click_count, last_accessed_at from short_url where short_code = ?")) {
            statement.setString(1, "stats-expired");
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("click_count")).isEqualTo(7);
                assertThat(row.getTimestamp("last_accessed_at").toInstant()).isEqualTo(lastAccessedAt);
            }
        }
    }

    @Test
    void deleteShortUrl_existingLink_commitsDeletionAndReturns404OnRepeat() throws Exception {
        seedRedirect("delete-link", null);
        var uri = URI.create("http://localhost:" + port + "/api/v1/urls/delete-link");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).DELETE().build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.prepareStatement("select id from short_url where short_code = ?")) {
                statement.setString(1, "delete-link");
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isFalse();
                }
            }
            var repeated = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(repeated.statusCode()).isEqualTo(404);
            for (var path : java.util.List.of("/delete-link", "/api/v1/urls/delete-link")) {
                var lookup = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                assertThat(client.send(lookup, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            }
        }
    }

    @Test
    void deleteShortUrl_concurrentDeletes_returnsOne204AndOne404() throws Exception {
        seedRedirect("delete-race", null);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls/delete-race"))
                .timeout(Duration.ofSeconds(10)).DELETE().build();

        try (var client = HttpClient.newHttpClient()) {
            var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode(),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(204, 404);
        }
    }

    private void seedRedirect(String code, Instant expiresAt) throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "insert into short_url (short_code, original_url, custom_alias, expires_at) values (?, ?, true, ?)")) {
            statement.setString(1, code);
            statement.setString(2, "https://example.com/redirect?q=1");
            statement.setTimestamp(3, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

}
