package com.parvez.urlshortener.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/** Exercises the real HTTP adapter against an isolated local scanner. */
@Tag("unit")
class HttpSafetyProviderTest {
    private HttpServer server;
    private String endpoint;
    private final ObjectMapper json = new ObjectMapper();
    private final URI destination = URI.create("https://example.com/path?token=private");

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/scan";
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @ParameterizedTest
    @ValueSource(strings = {"SAFE", "MALICIOUS"})
    void scan_definitiveVerdict_sendsDestinationAndBearerToken(String verdict) {
        var body = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        server.createContext("/scan", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"verdict\":\"" + verdict + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        var provider = provider(Duration.ofSeconds(2));
        assertThat(provider.name()).isEqualTo("test-v1");
        assertThat(provider.scan(destination)).isEqualTo(SafetyProvider.Verdict.valueOf(verdict));
        assertThat(json.readTree(body.get()).path("url").asText()).isEqualTo(destination.toString());
        assertThat(authorization.get()).isEqualTo("Bearer scanner-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "malformed", "oversize", "redirect", "error", "content-type"})
    void scan_untrustworthyResponse_failsClosed(String scenario) {
        server.createContext("/scan", exchange -> {
            String body = switch (scenario) {
                case "unknown" -> "{\"verdict\":\"UNKNOWN\"}";
                case "malformed" -> "{";
                case "oversize" -> " ".repeat(5000) + "{\"verdict\":\"SAFE\"}";
                default -> "{\"verdict\":\"SAFE\"}";
            };
            exchange.getResponseHeaders().set("Content-Type", scenario.equals("content-type") ? "text/plain" : "application/json");
            exchange.getResponseHeaders().set("Location", endpoint);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(scenario.equals("redirect") ? 302 : scenario.equals("error") ? 503 : 200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).scan(destination))
                .isInstanceOf(SafetyScanUnavailableException.class).hasMessageNotContaining("scanner-token");
    }

    @Test
    void scan_timeout_includesBodyReadAndFailsClosed() {
        var release = new CountDownLatch(1);
        server.createContext("/scan", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 100);
            try { release.await(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try {
            assertThatThrownBy(() -> provider(Duration.ofMillis(100)).scan(destination))
                    .isInstanceOf(SafetyScanUnavailableException.class);
        } finally { release.countDown(); }
    }

    @Test
    void scan_missingEndpoint_failsClosed() {
        assertThatThrownBy(() -> new HttpSafetyProvider(json, "", "", "test", Duration.ofSeconds(1)).scan(destination))
                .isInstanceOf(SafetyScanUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://scanner", "https://user:pass@scanner", "https://scanner/#fragment", "relative"})
    void constructor_invalidEndpoint_rejectsConfiguration(String endpoint) {
        assertThatThrownBy(() -> new HttpSafetyProvider(json, endpoint, "", "test", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_invalidBounds_rejectsConfiguration() {
        assertThatThrownBy(() -> new HttpSafetyProvider(json, endpoint, "", "bad:name", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider(Duration.ofMillis(99))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider(Duration.ofSeconds(11))).isInstanceOf(IllegalArgumentException.class);
    }

    private HttpSafetyProvider provider(Duration timeout) {
        return new HttpSafetyProvider(json, endpoint, "scanner-token", "test-v1", timeout);
    }
}
