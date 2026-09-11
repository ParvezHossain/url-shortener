package com.parvez.urlshortener.repository;

import com.parvez.urlshortener.domain.ShortUrl;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Replaces the internal code after PostgreSQL assigns the generated identity. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ShortUrl u set u.shortCode = :shortCode where u.id = :id")
    void updateShortCode(@Param("id") long id, @Param("shortCode") String shortCode);
}

