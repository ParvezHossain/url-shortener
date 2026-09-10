package com.example.urlshortener.service;

import com.example.urlshortener.dto.request.CreateShortUrlRequest;
import com.example.urlshortener.dto.response.ShortUrlResponse;

/** Defines operations on shortened URLs at the application boundary. */
public interface UrlShortenerService {

    /**
     * Creates a shortened URL using a generated code or the requested alias.
     * @throws com.example.urlshortener.exception.InvalidUrlException for invalid input
     * @throws com.example.urlshortener.exception.DuplicateAliasException for an occupied alias
     */
    ShortUrlResponse create(CreateShortUrlRequest request);
    /**
     * Resolves a code and records one successful access.
     * @throws com.example.urlshortener.exception.UrlNotFoundException for an unknown code
     * @throws com.example.urlshortener.exception.UrlExpiredException for an expired link
     */
    String resolve(String shortCode);
}
