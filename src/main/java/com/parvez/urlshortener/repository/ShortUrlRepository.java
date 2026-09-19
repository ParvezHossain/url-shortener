package com.parvez.urlshortener.repository;

import com.parvez.urlshortener.domain.ShortUrl;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persists shortened URLs and looks them up by their unique public code. */
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    /** Finds the URL associated with a code, or an empty result when it is unknown. */
    Optional<ShortUrl> findByShortCode(String shortCode);
    /** Locks the matching URL until transaction completion to serialize access updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from ShortUrl u where u.shortCode = :shortCode")
    Optional<ShortUrl> findByShortCodeForUpdate(@Param("shortCode") String shortCode);

        /** Atomically records one cached redirect only for the cached destination and an active, unexpired row. */
        @Modifying
        @Query("update ShortUrl u set u.clickCount = u.clickCount + 1, "
            + "u.lastAccessedAt = :accessedAt where u.shortCode = :shortCode "
            + "and u.originalUrl = :destination "
            + "and u.safetyState = com.parvez.urlshortener.domain.SafetyState.ACTIVE "
            + "and (u.expiresAt is null or u.expiresAt > :accessedAt)")
        int recordCachedAccess(@Param("shortCode") String shortCode,
            @Param("destination") String destination, @Param("accessedAt") Instant accessedAt);

    /** Returns a bounded, deterministically ordered page for one owner. */
    Page<ShortUrl> findByOwnerId(
            UUID ownerId, Pageable pageable);

    /** Replaces the internal code after PostgreSQL assigns the generated identity. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ShortUrl u set u.shortCode = :shortCode where u.id = :id")
    void updateShortCode(@Param("id") long id, @Param("shortCode") String shortCode);
}

