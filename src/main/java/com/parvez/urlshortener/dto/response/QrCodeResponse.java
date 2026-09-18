package com.parvez.urlshortener.dto.response;

/** Carries an ephemeral encoded image and its explicit media type to the HTTP layer. */
public record QrCodeResponse(byte[] content, String contentType) {}
