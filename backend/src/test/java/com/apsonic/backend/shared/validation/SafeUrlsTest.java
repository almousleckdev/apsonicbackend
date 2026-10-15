package com.apsonic.backend.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SafeUrlsTest {

    @ParameterizedTest
    @ValueSource(strings = {"/contact", "/products?category=atv#top", "#specs", "https://example.com/a?b=1", "http://example.com",
            "mailto:sales@apsonic.africa", "tel:+2250700000000", "/uploads/2026/photo.png"})
    void acceptsOrdinaryLinks(String url) {
        assertThat(SafeUrls.isSafeLink(url)).as(url).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html;base64,PHNjcmlwdD4=", "vbscript:x",
            "//evil.example/x", "///evil.example", "\\\\evil.example", "/path with space", "/a\"onmouseover=\"x", "/<script>",
            "https://user:pass@example.com", "https://", "ftp://example.com/file", "/../etc/passwd", "/a/%2e%2e/b", "", " "})
    void rejectsDangerousOrMalformedLinks(String url) {
        assertThat(SafeUrls.isSafeLink(url)).as(url).isFalse();
    }

    @Test
    void rejectsNullAndOverlongValues() {
        assertThat(SafeUrls.isSafeLink(null)).isFalse();
        assertThat(SafeUrls.isSafeLink("/" + "a".repeat(3000))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/uploads/a.png", "/uploads/2026/05/a%20b.mp4", "https://cdn.example.com/a.jpg", "/categories/atv/x.png"})
    void acceptsMedia(String url) {
        assertThat(SafeUrls.isSafeMedia(url)).as(url).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:image/png;base64,AAAA", "//evil.example/a.png", "/uploads/../../etc/passwd",
            "/uploads/a b.png", "file:///etc/passwd", "ftp://x/y.png"})
    void rejectsUnsafeMedia(String url) {
        assertThat(SafeUrls.isSafeMedia(url)).as(url).isFalse();
    }

    @Test
    void canonicalUrlsMustBeHttps() {
        assertThat(SafeUrls.isHttpsUrl("https://www.apsonic.africa/about")).isTrue();
        assertThat(SafeUrls.isHttpsUrl("http://www.apsonic.africa/about")).isFalse();
        assertThat(SafeUrls.isHttpsUrl("/about")).isFalse();
    }
}
