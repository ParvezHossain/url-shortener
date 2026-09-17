package com.parvez.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.exception.ApiPermissionException;
import com.parvez.urlshortener.repository.ApiKeyRepository;
import com.parvez.urlshortener.security.OwnerPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks authentication, credential secrecy, permission boundaries, and key lifecycle. */
@Tag("unit")
class ApiKeyServiceTest {
    private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
    private final ApiKeyService service = new ApiKeyService(repository);
    private final String prefix = "a".repeat(24);
    private final String raw = "usk_" + prefix + "_" + "b".repeat(64);
    private final OwnerPrincipal owner = new OwnerPrincipal(UUID.randomUUID(), prefix);

    @Test
    void authenticate_validApiKey_returnsOwnerPrincipal() throws Exception {
        when(repository.findForUpdate(prefix)).thenReturn(Optional.of(
                new ApiKeyRepository.KeyRecord(owner.ownerId(), hash(raw), false)));
        assertThat(service.authenticate(raw)).isEqualTo(owner);
        verify(repository).recordUse(prefix);
    }

    @Test
    void authenticate_invalidOrRevokedApiKey_returns401() throws Exception {
        when(repository.findForUpdate(prefix)).thenReturn(Optional.empty(),
                Optional.of(new ApiKeyRepository.KeyRecord(owner.ownerId(), "0".repeat(64), false)),
                Optional.of(new ApiKeyRepository.KeyRecord(owner.ownerId(), hash(raw), true)));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.authenticate(raw)).isInstanceOf(ApiAuthenticationException.class)
                    .hasMessage("Valid API key required");
        }
        verify(repository, never()).recordUse(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong", "Bearer secret", "usk_", " secret "})
    void authenticate_malformedKey_rejectsWithoutLookup(String value) {
        assertThatThrownBy(() -> service.authenticate(value)).isInstanceOf(ApiAuthenticationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rotate_currentKey_storesOnlyHashAndAuditsReplacement() throws Exception {
        when(repository.revoke(prefix, owner.ownerId())).thenReturn(true);
        var result = service.rotate(owner, prefix);
        assertThat(result.apiKey()).matches("usk_[0-9a-f]{24}_[0-9a-f]{64}").isNotEqualTo(raw);
        assertThat(result.toString()).doesNotContain(result.apiKey());
        verify(repository).insert(result.prefix(), owner.ownerId(), hash(result.apiKey()));
        verify(repository).audit(owner.ownerId(), "KEY_ROTATED", prefix, result.prefix());
    }

    @Test
    void revoke_currentKey_recordsRevocationAndAudit() {
        when(repository.revoke(prefix, owner.ownerId())).thenReturn(true);
        service.revoke(owner, prefix);
        verify(repository).audit(owner.ownerId(), "KEY_REVOKED", prefix, prefix);
    }

    @Test
    void lifecycle_otherKey_returnsForbiddenWithoutMutation() {
        assertThatThrownBy(() -> service.rotate(owner, "other")).isInstanceOf(ApiPermissionException.class);
        assertThatThrownBy(() -> service.revoke(owner, "other")).isInstanceOf(ApiPermissionException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void lifecycle_alreadyRevoked_rejectsWithoutReplacementOrAudit() {
        assertThatThrownBy(() -> service.rotate(owner, prefix)).isInstanceOf(ApiAuthenticationException.class);
        assertThatThrownBy(() -> service.revoke(owner, prefix)).isInstanceOf(ApiAuthenticationException.class);
        verify(repository, never()).insert(anyString(), eq(owner.ownerId()), anyString());
        verify(repository, never()).audit(eq(owner.ownerId()), anyString(), anyString(), anyString());
    }

    @Test
    void lifecycle_missingPrincipal_rejectsWithoutMutation() {
        assertThatThrownBy(() -> service.rotate(null, prefix)).isInstanceOf(ApiAuthenticationException.class);
        assertThatThrownBy(() -> service.revoke(null, prefix)).isInstanceOf(ApiAuthenticationException.class);
        verifyNoInteractions(repository);
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
