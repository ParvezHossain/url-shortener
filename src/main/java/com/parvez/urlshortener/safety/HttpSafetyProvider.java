package com.parvez.urlshortener.safety;

import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.ObjectMapper;

/** Sends destinations only to an operator-configured scanner with a bounded JSON response. */
public class HttpSafetyProvider implements SafetyProvider {
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
        if (endpoint.isBlank()) throw new SafetyScanUnavailableException();
        java.util.concurrent.CompletableFuture<HttpResponse<String>> pending = null;
        try {
            var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("url", destination.toASCIIString()))));
            if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
            pending = client.sendAsync(request.build(), HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), 4096));
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].equalsIgnoreCase("application/json")) throw new SafetyScanUnavailableException();
            return Verdict.valueOf(json.readTree(response.body()).path("verdict").asText());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SafetyScanUnavailableException();
        } catch (Exception ex) { throw new SafetyScanUnavailableException(); }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
}
