package com.parvez.urlshortener.exception;

/** Reports a safe, public-facing link-safety failure. */
public class LinkNotActiveException extends RuntimeException {
    /** Supplies a fixed message without destination or provider details. */
    public LinkNotActiveException() { super("This link is not active"); }
}
