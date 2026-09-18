package com.parvez.urlshortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.parvez.urlshortener.dto.request.QrOptionsRequest;
import com.parvez.urlshortener.dto.response.QrCodeResponse;
import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.security.OwnerPrincipal;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/** Generates transient QR images after the existing owner-scoped metadata check. */
@Service
public class QrCodeService {
    private final UrlShortenerService urls;

    /** Reuses ownership rules without resolving redirects or incrementing analytics. */
    public QrCodeService(UrlShortenerService urls) { this.urls = urls; }

    /** Selects the two supported application-generated image formats. */
    public enum Format { PNG, SVG }

    /**
     * Encodes only the public short URL, including for expired owned links.
     * @throws com.parvez.urlshortener.exception.UrlNotFoundException for absent or foreign links
     * @throws InvalidUrlException for unsafe options or insufficient encoding dimensions
     */
    public QrCodeResponse generateQr(String code, OwnerPrincipal owner, QrOptionsRequest options, Format format) {
        if (options == null || options.size() < 128 || options.size() > 1024
                || options.margin() < 4 || options.margin() > 8
                || !options.correction().matches("[LMQH]") || format == null) {
            throw new InvalidUrlException("QR options require size 128–1024, margin 4–8, and correction L, M, Q, or H");
        }
        String shortUrl = urls.getStats(code, owner).shortUrl();
        if (shortUrl.getBytes(StandardCharsets.UTF_8).length > 2048) {
            throw new InvalidUrlException("Public short URL is too long for QR generation");
        }
        try {
            var matrix = new QRCodeWriter().encode(shortUrl, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, options.margin(),
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(options.correction()),
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            int size = options.size();
            int scale = size / matrix.getWidth();
            if (scale < 2) throw new InvalidUrlException("QR size is too small for this public URL; increase size");
            int offset = (size - matrix.getWidth() * scale) / 2;
            var image = format == Format.PNG ? new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB) : null;
            var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                    .append(size).append("\" height=\"").append(size)
                    .append("\" viewBox=\"0 0 ").append(size).append(' ').append(size)
                    .append("\"><title>QR code for a short URL</title><rect width=\"")
                    .append(size).append("\" height=\"").append(size).append("\" fill=\"white\"/>");
            if (image != null) {
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) image.setRGB(x, y, 0xffffff);
            }
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (!matrix.get(x, y)) continue;
                    int left = offset + x * scale;
                    int top = offset + y * scale;
                    if (image != null) {
                        for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) {
                            image.setRGB(left + dx, top + dy, 0);
                        }
                    } else {
                        svg.append("<rect x=\"").append(left).append("\" y=\"").append(top)
                                .append("\" width=\"").append(scale).append("\" height=\"")
                                .append(scale).append("\"/>");
                    }
                }
            }
            if (format == Format.SVG) {
                return new QrCodeResponse(svg.append("</svg>").toString().getBytes(StandardCharsets.UTF_8), "image/svg+xml");
            }
            var output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return new QrCodeResponse(output.toByteArray(), "image/png");
        } catch (WriterException ex) {
            throw new InvalidUrlException("Public short URL exceeds QR encoding capacity");
        } catch (IOException ex) {
            throw new IllegalStateException("QR image generation failed", ex);
        }
    }
}
