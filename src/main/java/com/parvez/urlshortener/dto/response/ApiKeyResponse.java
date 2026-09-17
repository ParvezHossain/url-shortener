package com.parvez.urlshortener.dto.response;

/** Returns a newly issued credential once; callers must store it securely. */
public record ApiKeyResponse(String apiKey, String prefix) {
    /** Prevents accidental credential disclosure through diagnostic rendering. */
    @Override public String toString() { return "ApiKeyResponse[redacted]"; }
}
