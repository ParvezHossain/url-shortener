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

    @Test
    void create_unsupportedExpiry_throwsInvalidUrlException() {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", null, Instant.MAX)))
                .isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(repository);
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
