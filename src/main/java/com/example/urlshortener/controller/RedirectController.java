package com.example.urlshortener.controller;

import com.example.urlshortener.service.UrlShortenerService;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Redirects short codes to their resolved destinations. */
@RestController
public class RedirectController {

    private final UrlShortenerService service;

    /** Supplies the URL application service. */
    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    /** Returns a temporary redirect after the service records a successful access. */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String destination = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destination))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
