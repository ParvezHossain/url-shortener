package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ShortUrl;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persists shortened URLs and looks them up by their unique public code. */
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    /** Finds the URL associated with a code, or an empty result when it is unknown. */
    Optional<ShortUrl> findByShortCode(String shortCode);
    /** Replaces the internal code after PostgreSQL assigns the generated identity. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ShortUrl u set u.shortCode = :shortCode where u.id = :id")
    void updateShortCode(@Param("id") long id, @Param("shortCode") String shortCode);
}

