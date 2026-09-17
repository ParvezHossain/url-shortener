package com.parvez.urlshortener.security;

import java.util.UUID;

/** Identifies the owner and non-secret key prefix authenticated for one request. */
public record OwnerPrincipal(UUID ownerId, String keyPrefix) {}
