package com.parvez.urlshortener.exception;

/** Reports a safe, public-facing link-safety failure. */
public class SafetyScanUnavailableException extends RuntimeException {
    /** Supplies a fixed message without destination or provider details. */
    public SafetyScanUnavailableException() { super("Link safety could not be verified; the link is not active"); }
}
