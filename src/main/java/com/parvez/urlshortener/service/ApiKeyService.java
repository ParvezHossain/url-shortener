package com.parvez.urlshortener.service;

import com.parvez.urlshortener.dto.response.ApiKeyResponse;
import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.exception.ApiPermissionException;
import com.parvez.urlshortener.repository.ApiKeyRepository;
import com.parvez.urlshortener.security.OwnerPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates high-entropy credentials and records atomic key lifecycle events. */
@Service
public class ApiKeyService {
    private final ApiKeyRepository repository;
    private final SecureRandom random = new SecureRandom();

    /** Supplies key persistence. */
    public ApiKeyService(ApiKeyRepository repository) { this.repository = repository; }

    /** Returns an owner for an active key and commits its last-used timestamp. */
    @Transactional
    public OwnerPrincipal authenticate(String raw) {
        if (raw == null || !raw.matches("usk_[0-9a-f]{24}_[0-9a-f]{64}")) {
            throw new ApiAuthenticationException();
        }

        String prefix = raw.substring(4, 28);

        var row = repository.findForUpdate(prefix);
        String expected = row.map(ApiKeyRepository.KeyRecord::hash).orElse("0".repeat(64));
        boolean matches = MessageDigest.isEqual(hash(raw).getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
        if (!matches || row.isEmpty() || row.orElseThrow().revoked()) throw new ApiAuthenticationException();
        repository.recordUse(prefix);
        return new OwnerPrincipal(row.orElseThrow().owner(), prefix);
    }

    /** Atomically revokes the current key and returns its replacement once. */
    @Transactional
    public ApiKeyResponse rotate(OwnerPrincipal principal, String prefix) {
        requireCurrentKey(principal, prefix);
        revokeActive(principal, prefix);
        String nextPrefix = randomHex(12);
        String raw = "usk_" + nextPrefix + "_" + randomHex(32);
        repository.insert(nextPrefix, principal.ownerId(), hash(raw));
        audit(principal, "KEY_ROTATED", nextPrefix);
        return new ApiKeyResponse(raw, nextPrefix);
    }

    /** Revokes the current credential; recovery requires operator provisioning. */
    @Transactional
    public void revoke(OwnerPrincipal principal, String prefix) {
        requireCurrentKey(principal, prefix);
        revokeActive(principal, prefix);
        audit(principal, "KEY_REVOKED", prefix);
    }

    private void revokeActive(OwnerPrincipal principal, String prefix) {
        if (!repository.revoke(prefix, principal.ownerId())) throw new ApiAuthenticationException();
    }

    private void requireCurrentKey(OwnerPrincipal principal, String prefix) {
        if (principal == null) throw new ApiAuthenticationException();
        if (!principal.keyPrefix().equals(prefix)) throw new ApiPermissionException();
    }

    private void audit(OwnerPrincipal principal, String event, String target) {
        repository.audit(principal.ownerId(), event, principal.keyPrefix(), target);
    }

    private String randomHex(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

}
