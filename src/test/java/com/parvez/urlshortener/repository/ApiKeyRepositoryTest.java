package com.parvez.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parvez.urlshortener.domain.ShortUrl;
import com.parvez.urlshortener.dto.request.CreateShortUrlRequest;
import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.service.ApiKeyService;
import com.parvez.urlshortener.service.UrlShortenerService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies PostgreSQL ownership constraints and the real HTTP authentication boundary. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ApiKeyRepositoryTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApiKeyRepository keys;
    @Autowired private ApiKeyService authentication;
    @Autowired private ShortUrlRepository urls;
    @Autowired private UrlShortenerService service;
    @Value("${local.server.port}") private int port;

    @Test
    void insert_duplicatePrefix_enforcesUniqueness() throws Exception {
        var key = provision();
        assertThatThrownBy(() -> keys.insert(key.prefix(), key.owner(), "0".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_unknownOwner_enforcesForeignKeys() {
        assertThatThrownBy(() -> keys.insert("c".repeat(24), UUID.randomUUID(), "0".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> urls.saveAndFlush(new ShortUrl("orphan", "https://example.com", true,
                null, UUID.randomUUID()))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOwnerId_ownedAndLegacyLinks_returnsOnlyRequestedOwner() throws Exception {
        var first = provision();
        var second = provision();
        urls.saveAndFlush(new ShortUrl("first", "https://example.com", true, null, first.owner()));
        urls.saveAndFlush(new ShortUrl("second", "https://example.com", true, null, second.owner()));
        urls.saveAndFlush(new ShortUrl("legacy", "https://example.com", true, null));
        assertThat(urls.findByOwnerId(first.owner(), PageRequest.of(0, 20)).getContent())
                .extracting(ShortUrl::getShortCode).containsExactly("first");
        assertThat(urls.findByShortCode("legacy").orElseThrow().belongsTo(null)).isTrue();
    }

    @Test
    void authenticate_rotateAndRevoke_persistsUsageHashesAndSanitizedAudit() throws Exception {
        var original = provision();
        var owner = authentication.authenticate(original.raw());
        assertThat(jdbc.queryForObject("select last_used_at from api_key where prefix = ?",
                java.sql.Timestamp.class, original.prefix())).isNotNull();
        var replacement = authentication.rotate(owner, original.prefix());
        assertThatThrownBy(() -> authentication.authenticate(original.raw()))
                .isInstanceOf(ApiAuthenticationException.class);
        var nextOwner = authentication.authenticate(replacement.apiKey());
        assertThat(nextOwner.ownerId()).isEqualTo(original.owner());
        authentication.revoke(nextOwner, replacement.prefix());
        assertThatThrownBy(() -> authentication.authenticate(replacement.apiKey()))
                .isInstanceOf(ApiAuthenticationException.class);
        assertThat(call("GET", "/api/v2/urls", replacement.apiKey()).statusCode()).isEqualTo(401);
        var stored = jdbc.queryForList("select prefix, owner_id, key_hash, revoked_at from api_key where owner_id = ?", original.owner()).toString();
        var audit = jdbc.queryForList("select owner_id, event_type, actor_prefix, target_prefix from api_audit where owner_id = ?", original.owner()).toString();
        assertThat(stored).doesNotContain(original.raw(), replacement.apiKey());
        assertThat(audit).contains("KEY_ROTATED", "KEY_REVOKED").doesNotContain(original.raw(), replacement.apiKey());
    }

    @Test
    void management_ownedLink_blocksOtherOwnersAndV1ButRedirectsPublicly() throws Exception {
        var first = provision();
        var second = provision();
        var created = service.create(new CreateShortUrlRequest("https://example.com", null, null),
                authentication.authenticate(first.raw()));
        String path = "/api/v2/urls/" + created.shortCode();
        assertThat(call("GET", path, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", path, second.raw()).statusCode()).isEqualTo(404);
        assertThat(call("DELETE", path, second.raw()).statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/urls/" + created.shortCode(), null).statusCode()).isEqualTo(404);
        assertThat(call("DELETE", "/api/v1/urls/" + created.shortCode(), null).statusCode()).isEqualTo(404);
        assertThat(call("GET", path, first.raw()).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/" + created.shortCode(), null).statusCode()).isEqualTo(302);
        assertThat(call("GET", "/api/v2/urls", first.raw()).body()).contains(created.shortCode());
        assertThat(call("DELETE", path, first.raw()).statusCode()).isEqualTo(204);
        assertThat(urls.findByShortCode(created.shortCode())).isEmpty();
    }

    @Test
    void rotate_concurrentCalls_onlyOneReplacementCommits() throws Exception {
        var key = provision();
        var owner = authentication.authenticate(key.raw());
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> rotate = () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
            try {
                authentication.rotate(owner, key.prefix());
                return true;
            } catch (ApiAuthenticationException ex) {
                return false;
            }
        };
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(rotate);
            var second = executor.submit(rotate);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("select count(*) from api_key where owner_id = ? and revoked_at is null",
                Long.class, key.owner())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from api_audit where owner_id = ? and event_type = 'KEY_ROTATED'",
                Long.class, key.owner())).isEqualTo(1);
    }

    @Test
    void generateQr_unknownOrForeignCode_doesNotDiscloseResource() throws Exception {
        var first = provision();
        var second = provision();
        var owner = authentication.authenticate(first.raw());
        var created = service.create(new CreateShortUrlRequest("https://example.com/private", null, null), owner);
        for (String extension : new String[]{"png", "svg"}) {
            String path = "/api/v2/urls/" + created.shortCode() + "/qr." + extension;
            assertThat(call("GET", path, null).statusCode()).isEqualTo(401);
            var foreign = call("GET", path, second.raw());
            assertThat(foreign.statusCode()).isEqualTo(404);
            assertThat(foreign.body()).doesNotContain("example.com", created.shortUrl());
            assertThat(call("GET", "/api/v2/urls/missing/qr." + extension, first.raw()).statusCode()).isEqualTo(404);
            var success = call("GET", path, first.raw());
            assertThat(success.statusCode()).isEqualTo(200);
            assertThat(success.headers().firstValue("Cache-Control")).hasValue("no-store");
        }
        assertThat(service.getStats(created.shortCode(), owner).clickCount()).isZero();
    }

    private HttpResponse<String> call(String method, String path, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
        if (key != null) request.header("X-API-Key", key);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private Key provision() throws Exception {
        UUID owner = UUID.randomUUID();
        String prefix = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String raw = "usk_" + prefix + "_" + "a".repeat(64);
        jdbc.update("insert into api_owner(id) values (?)", owner);
        keys.insert(prefix, owner, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.US_ASCII))));
        return new Key(owner, prefix, raw);
    }

    private record Key(UUID owner, String prefix, String raw) {}
}
