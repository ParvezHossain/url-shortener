package com.parvez.urlshortener.exception;

/** Reports a safe, public-facing link-safety failure. */
public class UnsafeDestinationException extends RuntimeException {
    /** Supplies a fixed message without destination or provider details. */
    public UnsafeDestinationException() { super("Destination is blocked by link-safety policy"); }
}
