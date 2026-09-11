package com.parvez.urlshortener.exception;

/** Thrown when no short URL exists for a given code. */
public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String shortCode) {
        super("No short URL found for code '" + shortCode + "'");
    }
}
