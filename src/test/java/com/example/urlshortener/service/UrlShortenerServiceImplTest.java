package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.dto.request.CreateShortUrlRequest;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.exception.UrlExpiredException;
import com.example.urlshortener.exception.DuplicateAliasException;
import java.util.Optional;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.util.Base62Encoder;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Verifies destination validation and generated link creation without a database. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceImplTest {

    @Mock
    private ShortUrlRepository repository;
    private UrlShortenerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerServiceImpl(repository, "https://sho.rt/", 2048);
    }

    @Test
    void create_validUrlNoAlias_savesEntityWithGeneratedCode() {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        var saved = mock(ShortUrl.class);
        when(saved.getId()).thenReturn(62L);
        when(saved.getOriginalUrl()).thenReturn("https://example.com/path");
        when(saved.getCreatedAt()).thenReturn(createdAt);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenReturn(saved);

        try (var encoder = mockStatic(Base62Encoder.class)) {
            encoder.when(() -> Base62Encoder.encode(62L)).thenReturn("10");

            var response = service.create(new CreateShortUrlRequest("https://example.com/path", null, null));

            encoder.verify(() -> Base62Encoder.encode(62L));
            verify(repository).updateShortCode(62L, "10");
            assertThat(response.shortCode()).isEqualTo("10");
            assertThat(response.shortUrl()).isEqualTo("https://sho.rt/10");
            assertThat(response.originalUrl()).isEqualTo("https://example.com/path");
            assertThat(response.createdAt()).isEqualTo(createdAt);
            assertThat(response.expiresAt()).isNull();
        }
        var captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getShortCode()).matches("~[0-9a-f]{15}");
        assertThat(captor.getValue().isCustomAlias()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "/relative", "ftp://example.com", "https:///path",
        "https://example.com/a b", "https://example.com:99999"})
    void create_malformedUrl_throwsInvalidUrlException(String url) {
        assertInvalid(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void create_blankUrl_throwsInvalidUrlException(String url) {
        assertInvalid(url);
    }

    @Test
    void create_urlExceedingMaxLength_throwsInvalidUrlException() {
        assertInvalid("https://example.com/" + "a".repeat(2048));
    }

    @Test
    void create_nullRequest_throwsInvalidUrlException() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "expiring-link")
    void create_expiresAtInPast_throwsInvalidUrlException(String alias) {
        var expiresAt = Instant.now().minusSeconds(60);

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", alias, expiresAt)))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("expiresAt must be in the future");
        verifyNoInteractions(repository);
    }

    @Test
    void create_expiresAtEqualToNow_throwsInvalidUrlException() {
        var now = Instant.parse("2026-09-10T00:00:00Z");
        try (var time = mockStatic(Instant.class)) {
            time.when(Instant::now).thenReturn(now);

            assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", null, now)))
                    .isInstanceOf(InvalidUrlException.class)
                    .hasMessage("expiresAt must be in the future");
            verifyNoInteractions(repository);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "expiring-link")
    void create_expiresAtInFuture_savesSuccessfully(String alias) {
        var expiresAt = Instant.now().plusSeconds(3600);
        assertSavedExpiry(alias, expiresAt);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "permanent-link")
    void create_noExpiresAt_savesWithNullExpiry(String alias) {
        assertSavedExpiry(alias, null);
    }

    private void assertSavedExpiry(String alias, Instant expiresAt) {
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(invocation -> {
            ShortUrl entity = invocation.getArgument(0);
            var saved = mock(ShortUrl.class);
            if (alias == null) {
                when(saved.getId()).thenReturn(62L);
            }
            when(saved.getOriginalUrl()).thenReturn(entity.getOriginalUrl());
            when(saved.getCreatedAt()).thenReturn(entity.getCreatedAt());
            when(saved.getExpiresAt()).thenReturn(entity.getExpiresAt());
            return saved;
        });

        var response = service.create(new CreateShortUrlRequest("https://example.com", alias, expiresAt));

        var captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.shortCode()).isEqualTo(alias == null ? "10" : alias);
    }

    @Test
    void create_persistsCreatedAtTimestamp() {
        var before = Instant.now();
        var saved = mock(ShortUrl.class);
        when(saved.getId()).thenReturn(1L);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenReturn(saved);

        service.create(new CreateShortUrlRequest("http://example.com", null, null));

        var captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void create_urlAtMaxLength_acceptsUrl() {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(2048 - prefix.length());
        var saved = mock(ShortUrl.class);
        when(saved.getId()).thenReturn(1L);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenReturn(saved);

        service.create(new CreateShortUrlRequest(url, null, null));

        verify(repository).saveAndFlush(any(ShortUrl.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "Ab_9-x", "abcdefghijklmnop"})
    void create_validCustomAlias_savesWithGivenCode(String alias) {
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var before = Instant.now();

        try (var encoder = mockStatic(Base62Encoder.class)) {
            var response = service.create(new CreateShortUrlRequest("https://example.com", alias, null));

            assertThat(response.shortCode()).isEqualTo(alias);
            assertThat(response.shortUrl()).isEqualTo("https://sho.rt/" + alias);
            assertThat(response.originalUrl()).isEqualTo("https://example.com");
            assertThat(response.createdAt()).isBetween(before, Instant.now());
            assertThat(response.expiresAt()).isNull();
            encoder.verifyNoInteractions();
        }
        var captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getShortCode()).isEqualTo(alias);
        assertThat(captor.getValue().isCustomAlias()).isTrue();
        verify(repository, never()).updateShortCode(anyLong(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ab"})
    void create_aliasTooShort_throwsInvalidUrlException(String alias) {
        assertInvalidAlias(alias);
    }

    @Test
    void create_aliasTooLong_throwsInvalidUrlException() {
        assertInvalidAlias("a".repeat(17));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "abc/", "abc?", "abc.", " abc", "abc ", "abc\n", "ébc", "   "})
    void create_aliasWithInvalidCharacters_throwsInvalidUrlException(String alias) {
        assertInvalidAlias(alias);
    }

    @Test
    void create_aliasAlreadyTaken_throwsDuplicateAliasException() {
        when(repository.findByShortCode("taken")).thenReturn(Optional.of(
                new ShortUrl("taken", "https://example.com/first", false, null)));

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", "taken", null)))
                .isInstanceOf(DuplicateAliasException.class)
                .hasMessage("Alias 'taken' is already taken");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_aliasClaimedConcurrently_throwsDuplicateAliasException() {
        var violation = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
                "uq_short_url_short_code");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", violation));

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", "taken", null)))
                .isInstanceOf(DuplicateAliasException.class)
                .hasMessage("Alias 'taken' is already taken");
    }

    @Test
    void create_unrelatedIntegrityFailure_doesNotReportDuplicateAlias() {
        var violation = new DataIntegrityViolationException("unrelated constraint");
        when(repository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", "alias", null)))
                .isSameAs(violation);
    }

    @Test
    void resolve_unknownCode_throwsUrlNotFoundException() {
        assertThatThrownBy(() -> service.resolve("unknown"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessage("No short URL found for code 'unknown'");
    }

    @Test
    void resolve_expiredCode_throwsUrlExpiredExceptionAndDoesNotIncrementClickCount() {
        var url = new ShortUrl("expired", "https://example.com", true, Instant.now().minusSeconds(60));
        url.recordAccess();
        var lastAccessedAt = url.getLastAccessedAt();
        when(repository.findByShortCodeForUpdate("expired")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> service.resolve("expired"))
                .isInstanceOf(UrlExpiredException.class)
                .hasMessage("Short URL 'expired' has expired");
        assertThat(url.getClickCount()).isEqualTo(1);
        assertThat(url.getLastAccessedAt()).isEqualTo(lastAccessedAt);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resolve_validCode_returnsOriginalUrl(boolean expiring) {
        var url = new ShortUrl("My_link-1", "https://example.com/path?q=1#section", true,
                expiring ? Instant.now().plusSeconds(3600) : null);
        when(repository.findByShortCodeForUpdate("My_link-1")).thenReturn(Optional.of(url));

        assertThat(service.resolve("My_link-1")).isEqualTo("https://example.com/path?q=1#section");
    }

    @Test
    void resolve_validCode_incrementsClickCount() {
        var url = new ShortUrl("10", "https://example.com", false, null);
        url.recordAccess();
        when(repository.findByShortCodeForUpdate("10")).thenReturn(Optional.of(url));

        service.resolve("10");

        assertThat(url.getClickCount()).isEqualTo(2);
    }

    @Test
    void resolve_validCode_updatesLastAccessedAt() {
        var url = new ShortUrl("10", "https://example.com", false, null);
        when(repository.findByShortCodeForUpdate("10")).thenReturn(Optional.of(url));
        var before = Instant.now();

        service.resolve("10");

        assertThat(url.getLastAccessedAt()).isBetween(before, Instant.now());
    }

    @Test
    void getStats_unknownCode_throwsUrlNotFoundException() {
        assertThatThrownBy(() -> service.getStats("unknown"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessage("No short URL found for code 'unknown'");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void getStats_existingCode_returnsCorrectStatsDto(boolean expired) {
        var expiresAt = Instant.now().plusSeconds(expired ? -60 : 3600);
        var url = new ShortUrl("my-link", "https://example.com/path?q=1", true, expiresAt);
        url.recordAccess();
        url.recordAccess();
        when(repository.findByShortCode("my-link")).thenReturn(Optional.of(url));

        var stats = service.getStats("my-link");

        assertThat(stats.shortCode()).isEqualTo("my-link");
        assertThat(stats.originalUrl()).isEqualTo("https://example.com/path?q=1");
        assertThat(stats.createdAt()).isEqualTo(url.getCreatedAt());
        assertThat(stats.expiresAt()).isEqualTo(expiresAt);
        assertThat(stats.clickCount()).isEqualTo(2);
        assertThat(stats.lastAccessedAt()).isEqualTo(url.getLastAccessedAt());
    }

    @Test
    void getStats_existingCode_doesNotIncrementClickCount() {
        var url = new ShortUrl("my-link", "https://example.com", true, null);
        url.recordAccess();
        var lastAccessedAt = url.getLastAccessedAt();
        when(repository.findByShortCode("my-link")).thenReturn(Optional.of(url));

        service.getStats("my-link");
        service.getStats("my-link");

        assertThat(url.getClickCount()).isEqualTo(1);
        assertThat(url.getLastAccessedAt()).isEqualTo(lastAccessedAt);
        verify(repository, org.mockito.Mockito.times(2)).findByShortCode("my-link");
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void getStats_unvisitedPermanentLink_returnsZeroClicksAndNullTimestamps() {
        var url = new ShortUrl("10", "https://example.com", false, null);
        when(repository.findByShortCode("10")).thenReturn(Optional.of(url));

        var stats = service.getStats("10");

        assertThat(stats.clickCount()).isZero();
        assertThat(stats.expiresAt()).isNull();
        assertThat(stats.lastAccessedAt()).isNull();
        assertThat(url.getLastAccessedAt()).isNull();
    }

    @Test
    void delete_unknownCode_throwsUrlNotFoundException() {
        assertThatThrownBy(() -> service.delete("unknown"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessage("No short URL found for code 'unknown'");
        verify(repository, never()).delete(any(ShortUrl.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delete_existingCode_removesEntity(boolean expired) {
        var url = new ShortUrl("my-link", "https://example.com", true,
                expired ? Instant.now().minusSeconds(60) : null);
        when(repository.findByShortCodeForUpdate("my-link")).thenReturn(Optional.of(url));

        service.delete("my-link");

        verify(repository).delete(url);
    }

    private void assertInvalidAlias(String alias) {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", alias, null)))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("customAlias");
        verifyNoInteractions(repository);
    }

    private void assertInvalid(String url) {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest(url, null, null)))
                .isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(repository);
    }
}
