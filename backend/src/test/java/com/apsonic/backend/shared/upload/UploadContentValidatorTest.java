package com.apsonic.backend.shared.upload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UploadContentValidatorTest {

    private final UploadContentValidator validator = new UploadContentValidator();

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};
    private static final byte[] GIF = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] WEBP = "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] AVI = "RIFF$\u0000\u0000\u0000AVI LIST".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 1, 0x42, (byte) 0xF7, (byte) 0x81, 1, 0x42, (byte) 0xF2, (byte) 0x81};
    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] SVG_XSS = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(document.domain)\"/>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTML = "<html><script>fetch('/steal')</script></html>".getBytes(StandardCharsets.UTF_8);

    private UploadContentValidator.Result check(byte[] bytes, String filename, String contentType) throws Exception {
        return validator.validate(new ByteArrayInputStream(bytes), filename, contentType);
    }

    @Test
    void acceptsEachSupportedFormatWhenContentExtensionAndMimeAgree() throws Exception {
        assertThat(check(PNG, "a.png", "image/png").type()).isEqualTo(UploadFileType.PNG);
        assertThat(check(JPEG, "a.jpg", "image/jpeg").type()).isEqualTo(UploadFileType.JPEG);
        assertThat(check(JPEG, "A.JPEG", "image/jpeg").accepted()).isTrue();
        assertThat(check(GIF, "a.gif", "image/gif").type()).isEqualTo(UploadFileType.GIF);
        assertThat(check(WEBP, "a.webp", "image/webp").type()).isEqualTo(UploadFileType.WEBP);
        assertThat(check(MP4, "a.mp4", "video/mp4").type()).isEqualTo(UploadFileType.MP4);
        assertThat(check(WEBM, "a.webm", "video/webm").type()).isEqualTo(UploadFileType.WEBM);
        assertThat(check(PDF, "a.pdf", "application/pdf").type()).isEqualTo(UploadFileType.PDF);
        assertThat(check(PNG, "a.png", "image/png; charset=binary").accepted()).isTrue();
    }

    @Test
    void rejectsSvgBecauseItCanCarryScript() throws Exception {
        assertThat(check(SVG_XSS, "logo.svg", "image/svg+xml").accepted()).isFalse();
        assertThat(check(SVG_XSS, "logo.png", "image/png").accepted()).isFalse();
    }

    @Test
    void rejectsHtmlAndScriptDisguisedAsImages() throws Exception {
        assertThat(check(HTML, "photo.jpg", "image/jpeg").accepted()).isFalse();
        assertThat(check(HTML, "page.html", "text/html").accepted()).isFalse();
        assertThat(check(new byte[0], "empty.png", "image/png").accepted()).isFalse();
    }

    @Test
    void rejectsExtensionThatDoesNotMatchRealContent() throws Exception {
        assertThat(check(PNG, "evil.jpg", "image/jpeg").accepted()).isFalse();
        assertThat(check(PDF, "doc.png", "image/png").accepted()).isFalse();
        assertThat(check(PNG, "noextension", "image/png").accepted()).isFalse();
        assertThat(check(PNG, "double.png.svg", "image/png").accepted()).isFalse();
    }

    @Test
    void rejectsClientContentTypeThatDoesNotMatchRealContent() throws Exception {
        assertThat(check(PNG, "a.png", "text/html").accepted()).isFalse();
        assertThat(check(PNG, "a.png", null).accepted()).isFalse();
    }

    @Test
    void riffContainerThatIsNotWebpIsRejected() throws Exception {
        assertThat(check(AVI, "movie.webp", "image/webp").accepted()).isFalse();
    }
}
