package com.example.urlshortener.exception;

/** Thrown when a submitted URL, alias, or expiry fails validation rules. */
public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }
}
