package com.example.urlshortener.service;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.dto.request.CreateShortUrlRequest;
import com.example.urlshortener.dto.response.ShortUrlResponse;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.DuplicateAliasException;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.util.Base62Encoder;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates destinations and atomically persists generated or custom short links. */
@Service
public class UrlShortenerServiceImpl implements UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerServiceImpl.class);
    private final ShortUrlRepository repository;
    private final String baseUrl;
    private final int maxOriginalUrlLength;

    /** Supplies persistence and externally configured link settings. */
    public UrlShortenerServiceImpl(
            ShortUrlRepository repository,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.short-code.max-original-url-length:2048}") int maxOriginalUrlLength) {
        this.repository = repository;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxOriginalUrlLength = maxOriginalUrlLength;
    }

    /**
     * Creates a generated or custom link.
     * @throws InvalidUrlException when the destination, alias, or options are invalid
     * @throws DuplicateAliasException when the requested alias is already in use
     */
    @Override
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request) {
        if (request == null) {
            throw new InvalidUrlException("Request must not be null");
        }
        validateUrl(request.originalUrl());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new InvalidUrlException("expiresAt must be in the future");
        }
        if (request.customAlias() != null) {
            return createCustomAlias(request);
        }
        // Reserved character keeps temporary codes separate from public Base62 codes.
        String temporaryCode = "~" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        var saved = repository.saveAndFlush(new ShortUrl(temporaryCode, request.originalUrl(), false, request.expiresAt()));
        String shortCode = Base62Encoder.encode(saved.getId());
        repository.updateShortCode(saved.getId(), shortCode);
        log.info("Created short URL with code {}", shortCode);
        return new ShortUrlResponse(shortCode, baseUrl + "/" + shortCode,
                saved.getOriginalUrl(), saved.getCreatedAt(), saved.getExpiresAt());
    }

    private ShortUrlResponse createCustomAlias(CreateShortUrlRequest request) {
        String alias = request.customAlias();
        if (!alias.matches("[a-zA-Z0-9_-]{3,16}")) {
            throw new InvalidUrlException("customAlias must contain 3 to 16 letters, digits, underscores, or hyphens");
        }
        if (repository.findByShortCode(alias).isPresent()) {
            throw new DuplicateAliasException(alias);
        }
        ShortUrl saved;
        try {
            // Flush here so concurrent claims are translated before transaction completion.
            saved = repository.saveAndFlush(new ShortUrl(alias, request.originalUrl(), true, request.expiresAt()));
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_short_url_short_code".equals(violation.getConstraintName())) {
                    throw new DuplicateAliasException(alias);
                }
            }
            throw ex;
        }
        log.info("Created short URL with custom alias {}", alias);
        return new ShortUrlResponse(alias, baseUrl + "/" + alias,
                saved.getOriginalUrl(), saved.getCreatedAt(), saved.getExpiresAt());
    }

    private void validateUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new InvalidUrlException("originalUrl must not be blank");
        }
        if (originalUrl.length() > maxOriginalUrlLength) {
            throw new InvalidUrlException("originalUrl must not exceed " + maxOriginalUrlLength + " characters");
        }
        try {
            var uri = new URI(originalUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getPort() > 65535) {
                throw new InvalidUrlException("originalUrl must be an absolute HTTP or HTTPS URL with a host");
            }
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("originalUrl is malformed");
        }
    }
}
