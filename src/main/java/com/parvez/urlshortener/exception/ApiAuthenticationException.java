package com.parvez.urlshortener.exception;

/** Rejects absent, malformed, unknown, or revoked API credentials uniformly. */
public class ApiAuthenticationException extends RuntimeException {
    /** Supplies a credential-independent error message. */
    public ApiAuthenticationException() { super("Valid API key required"); }
}
