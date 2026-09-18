package com.parvez.urlshortener.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Bounds QR dimensions, quiet-zone modules, and error correction with safe defaults. */
public record QrOptionsRequest(
        @Min(128) @Max(1024) Integer size,
        @Min(4) @Max(8) Integer margin,
        @Pattern(regexp = "[LMQH]") String correction) {
    /** Defaults omitted options to 256 pixels, four modules, and medium correction. */
    public QrOptionsRequest {
        size = size == null ? 256 : size;
        margin = margin == null ? 4 : margin;
        correction = correction == null ? "M" : correction;
    }
}
