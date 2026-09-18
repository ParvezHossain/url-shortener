package com.parvez.urlshortener.service;

import com.parvez.urlshortener.cache.RedirectCache;
import com.parvez.urlshortener.cache.RedirectCacheEntry;
import com.parvez.urlshortener.domain.ShortUrl;
import com.parvez.urlshortener.dto.request.CreateShortUrlRequest;
import com.parvez.urlshortener.dto.response.ShortUrlResponse;
import com.parvez.urlshortener.dto.response.ShortUrlStatsResponse;
import com.parvez.urlshortener.dto.response.UrlPageResponse;
import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.exception.DuplicateAliasException;
import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.exception.UrlExpiredException;
import com.parvez.urlshortener.exception.UrlNotFoundException;
import com.parvez.urlshortener.repository.ShortUrlRepository;
import com.parvez.urlshortener.security.OwnerPrincipal;
import com.parvez.urlshortener.util.Base62Encoder;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates destinations and atomically persists generated or custom short links. */
@Service
public class UrlShortenerServiceImpl implements UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerServiceImpl.class);
    private final ShortUrlRepository repository;
    private final RedirectCache redirectCache;
    private final String baseUrl;
    private final int maxOriginalUrlLength;
    private final Duration permanentCacheTtl;

    /** Supplies persistence and externally configured link settings. */
    public UrlShortenerServiceImpl(
            ShortUrlRepository repository,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.short-code.max-original-url-length:2048}") int maxOriginalUrlLength) {
        this(repository, RedirectCache.noop(), baseUrl, maxOriginalUrlLength, Duration.ofHours(1));
    }

    /** Supplies persistence, cache, and externally configured link settings. */
    @Autowired
    public UrlShortenerServiceImpl(
            ShortUrlRepository repository,
            RedirectCache redirectCache,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.short-code.max-original-url-length:2048}") int maxOriginalUrlLength,
            @Value("${app.cache.redirect.permanent-ttl:PT1H}") Duration permanentCacheTtl) {
        this.repository = repository;
        this.redirectCache = redirectCache;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxOriginalUrlLength = maxOriginalUrlLength;
        this.permanentCacheTtl = permanentCacheTtl;
    }

    /**
     * Creates a generated or custom link.
     * @throws InvalidUrlException when the destination, alias, or options are invalid
     * @throws DuplicateAliasException when the requested alias is already in use
     */
    @Override
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request) {
        return createForOwner(request, null);
    }

    /** Creates a link associated with the authenticated owner. */
    @Override
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request,
            OwnerPrincipal owner) {
        return createForOwner(request, requireOwner(owner));
    }

    private ShortUrlResponse createForOwner(CreateShortUrlRequest request, UUID ownerId) {
        if (request == null) {
            throw new InvalidUrlException("Request must not be null");
        }
        validateUrl(request.originalUrl());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new InvalidUrlException("expiresAt must be in the future");
        }
        if (request.customAlias() != null) {
            return createCustomAlias(request, ownerId);
        }
        // Reserved character keeps temporary codes separate from public Base62 codes.
        String temporaryCode = "~" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        var saved = repository.saveAndFlush(newLink(temporaryCode, request, false, ownerId));
        String shortCode = Base62Encoder.encode(saved.getId());
        repository.updateShortCode(saved.getId(), shortCode);
        log.info("Created short URL with code {}", shortCode);
        return new ShortUrlResponse(shortCode, baseUrl + "/" + shortCode,
                saved.getOriginalUrl(), saved.getCreatedAt(), saved.getExpiresAt());
    }

    /**
     * Resolves an active link and commits its access analytics before returning.
     * @throws UrlNotFoundException for an unknown code
     * @throws UrlExpiredException when the link has expired
     */
    @Override
    @Transactional
    public String resolve(String shortCode) {
        var cached = redirectCache.get(shortCode);
        if (cached.isPresent()) {
            var entry = cached.get();
            var now = Instant.now();
            if (entry.expiresAt() == null || entry.expiresAt().isAfter(now)) {
                if (repository.recordCachedAccess(shortCode, now) == 1) {
                    log.info("Resolved short URL with code {}", shortCode);
                    return entry.destination();
                }
            }
            redirectCache.evict(shortCode);
        }

        var url = repository.findByShortCodeForUpdate(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        if (url.isExpired()) {
            redirectCache.evict(shortCode);
            throw new UrlExpiredException(shortCode);
        }
        url.recordAccess();
        cache(url);
        log.info("Resolved short URL with code {}", shortCode);
        return url.getOriginalUrl();
    }

    /**
     * Reads metadata and analytics, including expired links, without changing the entity.
     * @throws UrlNotFoundException for an unknown code
     */
    @Override
    @Transactional(readOnly = true)
    public ShortUrlStatsResponse getStats(String shortCode) {
        return stats(shortCode, null);
    }

    /** Reads statistics only for the authenticated owner; foreign codes look absent. */
    @Override
    @Transactional(readOnly = true)
    public ShortUrlStatsResponse getStats(String shortCode,
            OwnerPrincipal owner) {
        return stats(shortCode, requireOwner(owner));
    }

    private ShortUrlStatsResponse stats(String shortCode, UUID ownerId) {
        var url = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        if (!url.belongsTo(ownerId)) throw new UrlNotFoundException(shortCode);
        return toStats(url);
    }

    private ShortUrlStatsResponse toStats(ShortUrl url) {
        return new ShortUrlStatsResponse(url.getShortCode(), url.getOriginalUrl(),
                url.getCreatedAt(), url.getExpiresAt(), url.getClickCount(), url.getLastAccessedAt(),
                baseUrl + "/" + url.getShortCode(), url.isCustomAlias());
    }

    /**
     * Deletes the matching link in a transaction coordinated with redirect updates.
     * @throws UrlNotFoundException for an unknown or previously deleted code
     */
    @Override
    @Transactional
    public void delete(String shortCode) {
        deleteForOwner(shortCode, null);
    }

    /** Deletes only a link owned by the authenticated caller. */
    @Override
    @Transactional
    public void delete(String shortCode, OwnerPrincipal owner) {
        deleteForOwner(shortCode, requireOwner(owner));
    }

    private void deleteForOwner(String shortCode, UUID ownerId) {
        var url = repository.findByShortCodeForUpdate(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        if (!url.belongsTo(ownerId)) throw new UrlNotFoundException(shortCode);
        repository.delete(url);
        redirectCache.evict(shortCode);
        log.info("Deleted short URL with code {}", shortCode);
    }

    private void cache(ShortUrl url) {
        Duration ttl = url.getExpiresAt() == null
                ? permanentCacheTtl
                : Duration.between(Instant.now(), url.getExpiresAt());

        redirectCache.put(url.getShortCode(),
                new RedirectCacheEntry(url.getOriginalUrl(), url.getExpiresAt()), ttl);
    }

    private ShortUrlResponse createCustomAlias(CreateShortUrlRequest request, UUID ownerId) {
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
            saved = repository.saveAndFlush(newLink(alias, request, true, ownerId));
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && ("uq_short_url_short_code".equals(violation.getConstraintName())
                        || "uk_short_url_short_code".equals(violation.getConstraintName()))) {
                    throw new DuplicateAliasException(alias);
                }
            }
            throw ex;
        }
        log.info("Created short URL with custom alias {}", alias);
        return new ShortUrlResponse(alias, baseUrl + "/" + alias,
                saved.getOriginalUrl(), saved.getCreatedAt(), saved.getExpiresAt());
    }

    /** Lists a bounded page of the caller's links in stable creation order. */
    @Override
    @Transactional(readOnly = true)
    public UrlPageResponse list(
            OwnerPrincipal owner, int page, int size) {
        var ownerId = requireOwner(owner);
        if (page < 0 || size < 1 || size > 100) throw new InvalidUrlException("Invalid page or size (1–100)");
        var result = repository.findByOwnerId(ownerId, PageRequest.of(
                page, size, Sort.by("createdAt", "id").descending()));
        return new UrlPageResponse(
                result.map(this::toStats).getContent(), page, size, result.getTotalElements());
    }

    private UUID requireOwner(OwnerPrincipal owner) {
        if (owner == null || owner.ownerId() == null) {
            throw new ApiAuthenticationException();
        }
        return owner.ownerId();
    }

    private ShortUrl newLink(String code, CreateShortUrlRequest request, boolean custom, UUID ownerId) {
        return ownerId == null ? new ShortUrl(code, request.originalUrl(), custom, request.expiresAt())
                : new ShortUrl(code, request.originalUrl(), custom, request.expiresAt(), ownerId);
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
