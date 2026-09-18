package com.parvez.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.parvez.urlshortener.dto.request.QrOptionsRequest;
import com.parvez.urlshortener.dto.response.ShortUrlStatsResponse;
import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.exception.UrlNotFoundException;
import com.parvez.urlshortener.security.OwnerPrincipal;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Decodes both output formats to verify the encoded address and safe geometry. */
@Tag("unit")
class QrCodeServiceTest {
    private final UrlShortenerService urls = mock(UrlShortenerService.class);
    private final QrCodeService qr = new QrCodeService(urls);
    private final OwnerPrincipal owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");
    private final String publicUrl = "https://sho.rt/owned";

    @Test
    void generateQr_existingOwnedCode_returnsScannableImage() throws Exception {
        stub(publicUrl);
        for (String correction : new String[]{"L", "M", "Q", "H"}) {
            var result = qr.generateQr("owned", owner, new QrOptionsRequest(256, 4, correction), QrCodeService.Format.PNG);
            var image = ImageIO.read(new ByteArrayInputStream(result.content()));
            assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(image.getWidth()).isEqualTo(256);
            assertThat(decode(image)).isEqualTo(publicUrl);
        }
    }

    @Test
    void generateQr_svg_containsOnlySafeGeometryAndDecodesToPublicUrl() throws Exception {
        stub(publicUrl);
        var result = qr.generateQr("owned", owner, new QrOptionsRequest(null, null, null), QrCodeService.Format.SVG);
        assertThat(result.contentType()).isEqualTo("image/svg+xml");
        String svg = new String(result.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(svg).doesNotContain("script", "href", "foreignObject", "example.com", publicUrl);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(result.content()));
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            var rectangles = document.getElementsByTagName("rect");
            for (int i = 0; i < rectangles.getLength(); i++) {
                var rectangle = (org.w3c.dom.Element) rectangles.item(i);
                graphics.setColor(i == 0 ? java.awt.Color.WHITE : java.awt.Color.BLACK);
                graphics.fillRect(i == 0 ? 0 : Integer.parseInt(rectangle.getAttribute("x")),
                        i == 0 ? 0 : Integer.parseInt(rectangle.getAttribute("y")),
                        Integer.parseInt(rectangle.getAttribute("width")), Integer.parseInt(rectangle.getAttribute("height")));
            }
        } finally { graphics.dispose(); }
        assertThat(decode(image)).isEqualTo(publicUrl);
    }

    @Test
    void generateQr_unknownOrForeignCode_doesNotDiscloseResource() {
        for (String code : new String[]{"unknown", "foreign"}) {
            when(urls.getStats(code, owner)).thenThrow(new UrlNotFoundException(code));
            for (var format : QrCodeService.Format.values()) {
                assertThatThrownBy(() -> qr.generateQr(code, owner, new QrOptionsRequest(null, null, null), format))
                        .isInstanceOf(UrlNotFoundException.class);
            }
        }
    }

    @Test
    void generateQr_invalidOptions_rejectsBeforeLookup() {
        for (var options : new QrOptionsRequest[]{new QrOptionsRequest(127, 4, "M"),
                new QrOptionsRequest(1025, 4, "M"), new QrOptionsRequest(256, 3, "M"),
                new QrOptionsRequest(256, 9, "M"), new QrOptionsRequest(256, 4, "script")}) {
            assertThatThrownBy(() -> qr.generateQr("owned", owner, options, QrCodeService.Format.PNG))
                    .isInstanceOf(InvalidUrlException.class);
        }
        verifyNoInteractions(urls);
    }

    @Test
    void generateQr_boundaryOptions_remainScannable() throws Exception {
        stub(publicUrl);
        for (int size : new int[]{128, 1024}) for (int margin : new int[]{4, 8}) {
            var result = qr.generateQr("owned", owner, new QrOptionsRequest(size, margin, "H"), QrCodeService.Format.PNG);
            assertThat(decode(ImageIO.read(new ByteArrayInputStream(result.content())))).isEqualTo(publicUrl);
        }
    }

    @Test
    void generateQr_longPublicUrl_rejectsInsufficientSizeAndCapacity() {
        stub("https://sho.rt/" + "a".repeat(1000));
        assertThatThrownBy(() -> qr.generateQr("owned", owner, new QrOptionsRequest(128, 8, "H"), QrCodeService.Format.PNG))
                .isInstanceOf(InvalidUrlException.class);
        stub("https://sho.rt/" + "a".repeat(2000));
        assertThatThrownBy(() -> qr.generateQr("owned", owner, new QrOptionsRequest(1024, 4, "H"), QrCodeService.Format.PNG))
                .isInstanceOf(InvalidUrlException.class);
        stub("https://sho.rt/" + "a".repeat(2048));
        assertThatThrownBy(() -> qr.generateQr("owned", owner, new QrOptionsRequest(null, null, null), QrCodeService.Format.PNG))
                .isInstanceOf(InvalidUrlException.class);
    }

    private void stub(String shortUrl) {
        when(urls.getStats(eq("owned"), any(OwnerPrincipal.class))).thenReturn(new ShortUrlStatsResponse("owned",
                "https://example.com/private-destination", Instant.now(), Instant.EPOCH, 0, null, shortUrl, true));
    }

    private String decode(BufferedImage image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        var source = new RGBLuminanceSource(width, height, image.getRGB(0, 0, width, height, null, 0, width));
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }
}
