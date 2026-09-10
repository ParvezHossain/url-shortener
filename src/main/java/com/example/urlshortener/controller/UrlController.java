package com.example.urlshortener.controller;

import com.example.urlshortener.dto.request.CreateShortUrlRequest;
import com.example.urlshortener.dto.response.ShortUrlResponse;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Maps URL creation requests to the application service. */
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
}
