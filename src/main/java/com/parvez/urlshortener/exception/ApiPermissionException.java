package com.parvez.urlshortener.exception;

/** Rejects an authenticated request without the required permission. */
public class ApiPermissionException extends RuntimeException {
    /** Supplies a resource-independent error message. */
    public ApiPermissionException() { super("Operation not permitted"); }
}
