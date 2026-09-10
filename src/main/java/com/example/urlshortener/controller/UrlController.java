package com.example.urlshortener.controller;

import com.example.urlshortener.dto.request.CreateShortUrlRequest;
import com.example.urlshortener.dto.response.ShortUrlResponse;
import com.example.urlshortener.dto.response.ShortUrlStatsResponse;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Maps URL creation, statistics, and deletion requests to the application service. */
@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService service;

    /** Supplies the URL application service. */
    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    /** Creates a short link and returns its public location. */
    @PostMapping
    public ResponseEntity<ShortUrlResponse> createShortUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }
    /** Returns link metadata and analytics without recording a visit. */
    @GetMapping("/{shortCode}")
    public ResponseEntity<ShortUrlStatsResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(service.getStats(shortCode));
    }
    /** Deletes a link and returns an empty response after the transaction commits. */
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable String shortCode) {
        service.delete(shortCode);
        return ResponseEntity.noContent().build();
    }


}
