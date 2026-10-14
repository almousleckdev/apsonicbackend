package com.apsonic.backend.shared.html;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<SCRIPT SRC=//evil.example/x.js></SCRIPT>",
            "<img src=x onerror=alert(1)>",
            "<img src=\"x\" onerror=\"alert(1)\">",
            "<svg onload=alert(1)>",
            "<svg><script>alert(1)</script></svg>",
            "<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>",
            "<a href=\"javascript:alert(1)\">click</a>",
            "<a href=\"JaVaScRiPt:alert(1)\">click</a>",
            "<a href=\"&#106;avascript:alert(1)\">click</a>",
            "<a href=\"  javascript:alert(1)\">click</a>",
            "<a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">x</a>",
            "<a href=\"vbscript:msgbox(1)\">x</a>",
            "<img src=\"javascript:alert(1)\">",
            "<img src=\"data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+\">",
            "<iframe src=\"https://evil.example/\"></iframe>",
            "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>",
            "<iframe src=\"javascript:alert(1)\"></iframe>",
            "<object data=\"https://evil.example/x.swf\"></object>",
            "<embed src=\"https://evil.example/x.swf\">",
            "<form action=\"https://evil.example\"><input name=p></form>",
            "<style>*{background:url(javascript:alert(1))}</style>",
            "<p style=\"background:url(javascript:alert(1))\">x</p>",
            "<p style=\"position:fixed;top:0;left:0;width:100%;height:100%\">overlay</p>",
            "<body onload=alert(1)>",
            "<div onmouseover=\"alert(1)\">x</div>",
            "<p onclick=alert(1)>x</p>",
            "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">",
            "<base href=\"https://evil.example/\">",
            "<link rel=stylesheet href=\"https://evil.example/x.css\">",
            "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\">",
            "<table><td><style><img src=x onerror=alert(1)></style>",
            "<video src=x onerror=alert(1)></video>",
            "<video><source src=\"javascript:alert(1)\"></video>",
            "<img src=x onerror=alert(1)//",
            "<<script>alert(1)//<</script>",
            "<a href=\"https://ok.example\" onclick=\"alert(1)\">x</a>",
    })
    void attackPayloadsLoseAllExecutableContent(String payload) {
        String out = sanitizer.sanitize(payload).toLowerCase(Locale.ROOT);
        assertThat(out).doesNotContain("<script", "onerror", "onload", "onclick", "onmouseover", "javascript:",
                "vbscript:", "data:text", "data:image", "<svg", "<math", "<form", "<style", "<object", "<embed",
                "<meta", "<base", "<link", "srcdoc", "evil.example/x", "position:fixed");
        assertThat(out).doesNotContain("<iframe");
        assertThat(out).doesNotContainPattern("<[a-z]+[^>]*\\son[a-z]+\\s*=");
    }

    @Test
    void legitimateEditorOutputIsPreserved() {
        String html = "<h2 style=\"text-align: center\">Title</h2><p>Hello <strong>bold</strong>, <em>it</em>, <u>u</u>, "
                + "<s>s</s> and <a href=\"https://apsonic.africa/x?a=1&amp;b=2\" target=\"_blank\" "
                + "class=\"text-brand-green font-bold underline cursor-pointer\">a link</a>.</p>"
                + "<ul><li>one</li><li>two</li></ul><ol><li>1</li></ol><blockquote><p>q</p></blockquote>"
                + "<pre><code>x = 1</code></pre><hr>"
                + "<figure data-type=\"image-block\" class=\"my-6 text-center block max-w-2xl mx-auto group relative\">"
                + "<img src=\"/uploads/3b0f6e1e-0000-4000-8000-000000000000.png\" alt=\"A bike\" width=\"600\">"
                + "<figcaption>Caption</figcaption></figure>";

        String out = sanitizer.sanitize(html);

        assertThat(out).contains("<h2 style=\"text-align: center\">Title</h2>")
                .contains("<strong>bold</strong>").contains("<em>it</em>").contains("<u>u</u>")
                .contains("<ul>").contains("<ol>").contains("<blockquote>").contains("<pre><code>x &#61; 1</code></pre>")
                .contains("<figcaption>Caption</figcaption>").contains("data-type=\"image-block\"")
                .contains("src=\"/uploads/3b0f6e1e-0000-4000-8000-000000000000.png\"").contains("alt=\"A bike\"")
                .contains("href=\"https://apsonic.africa/x?a&#61;1&amp;b&#61;2\"").contains("target=\"_blank\"");
        // Sanitizing happens on save and again on read, so it must be idempotent.
        assertThat(sanitizer.sanitize(out)).isEqualTo(out);
        // External links always get a safe rel, whatever the author wrote.
        assertThat(out).contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void youtubeEmbedAndUploadedVideoSurviveButOtherIframesDoNot() {
        String yt = "<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\" width=\"560\" height=\"315\" allowfullscreen></iframe>";
        assertThat(sanitizer.sanitize(yt)).contains("<iframe").contains("youtube.com/embed/dQw4w9WgXcQ");
        assertThat(sanitizer.sanitize("<iframe src=\"https://www.youtube.com.evil.example/embed/abcdefgh\"></iframe>"))
                .doesNotContain("<iframe");
        assertThat(sanitizer.sanitize("<iframe src=\"https://evil.example/embed/dQw4w9WgXcQ\"></iframe>"))
                .doesNotContain("<iframe");

        String video = "<video src=\"/uploads/clip.mp4\" controls width=\"640\"></video>";
        assertThat(sanitizer.sanitize(video)).contains("<video").contains("src=\"/uploads/clip.mp4\"").contains("controls");
    }

    @Test
    void plainTextIsNeverEntityEncoded() {
        assertThat(sanitizer.sanitize("Fast & Furious: 5 > 3")).isEqualTo("Fast & Furious: 5 > 3");
        assertThat(sanitizer.sanitize("https://apsonic.africa/a?x=1&y=2")).isEqualTo("https://apsonic.africa/a?x=1&y=2");
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void jsonTreesAreSanitizedOnlyWhereTheyContainMarkup() throws Exception {
        JsonNode config = mapper.readTree("""
                {"heading":"Fast & Furious","href":"/products",
                 "contentHtml":{"en":"<p>ok</p><script>alert(1)</script>","fr":"<img src=x onerror=alert(1)>"},
                 "cards":[{"description":"<a href=\\"javascript:alert(1)\\">x</a>"},{"n":3,"flag":true,"none":null}]}
                """);

        JsonNode out = sanitizer.sanitizeTree(config);

        assertThat(out.at("/heading").asText()).isEqualTo("Fast & Furious");
        assertThat(out.at("/href").asText()).isEqualTo("/products");
        assertThat(out.at("/contentHtml/en").asText()).isEqualTo("<p>ok</p>");
        assertThat(out.at("/contentHtml/fr").asText()).doesNotContain("onerror");
        assertThat(out.at("/cards/0/description").asText()).doesNotContain("javascript:");
        assertThat(out.at("/cards/1/n").asInt()).isEqualTo(3);
        assertThat(out.at("/cards/1/flag").asBoolean()).isTrue();
        // The input tree is untouched.
        assertThat(config.at("/contentHtml/en").asText()).contains("<script>");
    }
}
