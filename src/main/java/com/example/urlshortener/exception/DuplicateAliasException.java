package com.example.urlshortener.exception;

/** Thrown when a requested custom alias is already in use. */
public class DuplicateAliasException extends RuntimeException {
    public DuplicateAliasException(String alias) {
        super("Alias '" + alias + "' is already taken");
    }
}
