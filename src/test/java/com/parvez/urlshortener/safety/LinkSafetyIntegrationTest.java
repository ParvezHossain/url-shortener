package com.parvez.urlshortener.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

import com.parvez.urlshortener.cache.RedirectCache;
import com.parvez.urlshortener.cache.RedirectCacheEntry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies committed scan decisions, independent audits and redirect guards over HTTP. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SafetyTestConfig.class)
@Testcontainers
class LinkSafetyIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Value("${local.server.port}") private int port;
    private final JdbcTemplate jdbc;

    @Autowired
    LinkSafetyIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    @MockitoBean private RedirectCache cache;

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SCAN_FAILED"})
    void redirect_inactiveLinkWithStaleCache_neverRedirectsOrCountsClick(String state) throws Exception {
        String code = "off-" + state;
        jdbc.update("insert into short_url(short_code, original_url, safety_state) values (?, ?, ?)",
                code, "https://example.com/private", state);
        when(cache.get(code)).thenReturn(Optional.of(new RedirectCacheEntry("https://example.com/private", null)));
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + code))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("Location")).isEmpty();
            assertThat(response.body()).doesNotContain("example.com");
        }
        assertThat(jdbc.queryForObject("select click_count from short_url where short_code = ?", Long.class, code)).isZero();
        verify(cache, atLeastOnce()).evict(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"safe", "malicious", "timeout"})
    void create_scanDecision_commitsStateAndAuditEvenForErrorResponse(String verdict) throws Exception {
        String code = "scan-" + verdict;
        String state = switch (verdict) { case "safe" -> "ACTIVE"; case "malicious" -> "REJECTED"; default -> "SCAN_FAILED"; };
        int status = switch (verdict) { case "safe" -> 201; case "malicious" -> 422; default -> 503; };
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"originalUrl":"https://example.com/%s", "customAlias":"%s"}
                            """.formatted(verdict, code))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(status);
            assertThat(response.body()).doesNotContain("provider secret");
            if (status != 201) assertThat(response.headers().firstValue("Location")).isEmpty();
        }
        assertThat(jdbc.queryForObject("select safety_state from short_url where short_code = ?", String.class, code))
                .isEqualTo(state);
        assertThat(jdbc.queryForObject("select count(*) from safety_audit where verdict = ? and provider = 'test-v1' and scanned_at is not null",
                Integer.class, state)).isPositive();
    }
}
