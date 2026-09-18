package com.parvez.urlshortener.controller;

import com.parvez.urlshortener.dto.request.QrOptionsRequest;
import com.parvez.urlshortener.dto.response.QrCodeResponse;
import com.parvez.urlshortener.security.OwnerPrincipal;
import com.parvez.urlshortener.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves owner-protected QR images without storing image blobs. */
@RestController
@RequestMapping("/api/v2/urls")
@SecurityRequirement(name = "ApiKey")
@ApiResponses({
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
    @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited"),
    @ApiResponse(responseCode = "503", ref = "#/components/responses/QuotaUnavailable")
})
public class QrCodeController {
    private final QrCodeService qr;

    /** Supplies transient image generation. */
    public QrCodeController(QrCodeService qr) { this.qr = qr; }

    /** Returns a PNG encoding of the owned public link. */
    @Operation(summary = "Generate an owned link QR code as PNG")
    @ApiResponse(responseCode = "200", description = "PNG encoding of the public short URL",
            content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
    @GetMapping(value = "/{code}/qr.png", produces = "image/png")
    public ResponseEntity<byte[]> png(@PathVariable String code,
            @RequestAttribute("owner") OwnerPrincipal owner, @Valid @ModelAttribute QrOptionsRequest options) {
        return image(qr.generateQr(code, owner, options, QrCodeService.Format.PNG));
    }

    /** Returns script-free SVG geometry encoding the owned public link. */
    @Operation(summary = "Generate an owned link QR code as SVG")
    @ApiResponse(responseCode = "200", description = "Safe SVG encoding of the public short URL",
            content = @Content(mediaType = "image/svg+xml", schema = @Schema(type = "string", format = "binary")))
    @GetMapping(value = "/{code}/qr.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> svg(@PathVariable String code,
            @RequestAttribute("owner") OwnerPrincipal owner, @Valid @ModelAttribute QrOptionsRequest options) {
        return image(qr.generateQr(code, owner, options, QrCodeService.Format.SVG));
    }

    private ResponseEntity<byte[]> image(QrCodeResponse result) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Vary", "X-API-Key").body(result.content());
    }
}
