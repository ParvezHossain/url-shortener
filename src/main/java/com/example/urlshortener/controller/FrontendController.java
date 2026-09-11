package com.example.urlshortener.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the frontend entry point without forwarding into the short-code route. */
@Hidden
@RestController
public class FrontendController {

    /** Returns the packaged HTML entry point, revalidated to discover new hashed assets. */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/index.html"));
    }
}
