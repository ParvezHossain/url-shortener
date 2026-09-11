package com.parvez.urlshortener.exception;

/** Thrown when a short code is resolved after its expiresAt timestamp. */
public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String shortCode) {
        super("Short URL '" + shortCode + "' has expired");
    }
}
