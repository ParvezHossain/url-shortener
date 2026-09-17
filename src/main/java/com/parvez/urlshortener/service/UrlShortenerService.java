package com.parvez.urlshortener.service;

import com.parvez.urlshortener.dto.request.CreateShortUrlRequest;
import com.parvez.urlshortener.dto.response.ShortUrlResponse;
import com.parvez.urlshortener.dto.response.ShortUrlStatsResponse;
import com.parvez.urlshortener.dto.response.UrlPageResponse;
import com.parvez.urlshortener.security.OwnerPrincipal;

/** Defines operations on shortened URLs at the application boundary. */
public interface UrlShortenerService {

    /**
     * Creates a shortened URL using a generated code or the requested alias.
     * @throws com.parvez.urlshortener.exception.InvalidUrlException for invalid input
     * @throws com.parvez.urlshortener.exception.DuplicateAliasException for an occupied alias
     */
    ShortUrlResponse create(CreateShortUrlRequest request);
    /**
     * Resolves a code and records one successful access.
     * @throws com.parvez.urlshortener.exception.UrlNotFoundException for an unknown code
     * @throws com.parvez.urlshortener.exception.UrlExpiredException for an expired link
     */
    String resolve(String shortCode);

    /**
     * Returns metadata and analytics for an existing link without recording an access.
     * @throws com.parvez.urlshortener.exception.UrlNotFoundException for an unknown code
     */
    ShortUrlStatsResponse getStats(String shortCode);
    /**
     * Removes an existing link and its analytics.
     * @throws com.parvez.urlshortener.exception.UrlNotFoundException for an unknown or deleted code
     */
    void delete(String shortCode);

    /** Creates a link for the authenticated owner. */
    ShortUrlResponse create(CreateShortUrlRequest request, OwnerPrincipal owner);
    /** Reads only owned link metadata. */
    ShortUrlStatsResponse getStats(String code, OwnerPrincipal owner);
    /** Deletes only an owned link. */
    void delete(String code, OwnerPrincipal owner);
    /** Lists a bounded page of owned links. */
    UrlPageResponse list(
            OwnerPrincipal owner, int page, int size);
}
