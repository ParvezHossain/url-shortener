package com.parvez.urlshortener.dto.response;

import java.util.List;

/** Exposes a bounded owner-scoped page without persistence internals. */
public record UrlPageResponse(List<ShortUrlStatsResponse> content, int page, int size, long totalElements) {}
