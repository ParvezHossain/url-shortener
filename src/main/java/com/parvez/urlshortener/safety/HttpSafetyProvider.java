package com.parvez.urlshortener.safety;

import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/** Sends destinations only to an operator-configured scanner with a bounded JSON response. */
public class HttpSafetyProvider implements SafetyProvider {
    private static final Logger log = LoggerFactory.getLogger(HttpSafetyProvider.class);
    private final HttpClient client;
    private final ObjectMapper json;
    private final String endpoint;
    private final String token;
    private final String name;
    private final Duration timeout;

    /** Configures one attempt; redirects and automatic application retries are disabled. */
    public HttpSafetyProvider(ObjectMapper json, String endpoint, String token, String name, Duration timeout) {
        if (!name.matches("[a-zA-Z0-9_-]{1,64}") || timeout.toMillis() < 100 || timeout.toMillis() > 10000) {
            throw new IllegalArgumentException("Invalid scanner name or timeout (100ms–10s)");
        }
        if (!endpoint.isBlank()) {
            URI uri = URI.create(endpoint);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid scanner endpoint");
            }
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.json = json;
        this.endpoint = endpoint;
        this.token = token;
        this.name = name;
        this.timeout = timeout;
    }

    /** Identifies the scanner configuration revision for auditing and caching. */
    @Override public String name() { return name; }

    /** Requires HTTP 200 with a JSON SAFE or MALICIOUS verdict; all other results fail closed. */
    @Override public Verdict scan(URI destination) {
        if (endpoint.isBlank()) throw unavailable("endpoint_not_configured");
        java.util.concurrent.CompletableFuture<HttpResponse<String>> pending = null;
        try {
            var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("url", destination.toASCIIString()))));
            if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
            pending = client.sendAsync(request.build(), HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), 4096));
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw unavailable("http_status_" + response.statusCode());
            if (!response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                throw unavailable("unexpected_content_type");
            }
            return Verdict.valueOf(json.readTree(response.body()).path("verdict").asText());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable("interrupted");
        } catch (SafetyScanUnavailableException ex) {
            throw ex;
        } catch (TimeoutException ex) {
            throw unavailable("timeout");
        } catch (ExecutionException ex) {
            throw unavailable("transport_or_body_failure");
        } catch (Exception ex) {
            throw unavailable("invalid_request_or_response");
        }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }

    private SafetyScanUnavailableException unavailable(String reason) {
        // Only bounded configuration labels and application-owned reasons enter logs.
        log.warn("Safety scanner {} unavailable: {}", name, reason);
        return new SafetyScanUnavailableException();
    }
}
