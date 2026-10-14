package com.apsonic.backend.shared.html;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Single allow-list policy for all CMS-authored HTML (news articles, rich-text page sections). It permits what the
 * editor produces - text structure, links, images, YouTube embeds and uploaded video - and drops everything else:
 * scripts, event handlers, inline styles other than text alignment, forms, SVG/MathML, non-YouTube iframes and any
 * URL scheme other than http, https, mailto and tel. It is applied when content is saved and again when it is served,
 * so content stored before the policy existed is covered without a data migration.
 */
@Component
public class HtmlSanitizer {

    private static final Pattern TAG_LIKE = Pattern.compile("<\\s*[A-Za-z/!?]");
    private static final Pattern CSS_CLASSES = Pattern.compile("[\\w\\s\\-:/\\[\\]\\.%&#,()=>~*!@]{0,500}");
    private static final Pattern TEXT_ALIGN = Pattern.compile("\\s*text-align\\s*:\\s*(left|right|center|justify)\\s*;?\\s*");
    private static final Pattern DATA_TOKEN = Pattern.compile("[a-z][a-z0-9\\-]{0,40}");
    private static final Pattern YOUTUBE_EMBED = Pattern.compile(
            "https://(www\\.)?youtube(-nocookie)?\\.com/embed/[\\w\\-]{6,20}(\\?[\\w=&%\\-.]{0,200})?");
    private static final Pattern DIMENSION = Pattern.compile("[0-9]{1,4}%?");
    private static final Pattern BOOLEAN_ATTR = Pattern.compile("(?i)[a-z]*");
    private static final Pattern SRC_LESS_IFRAME = Pattern.compile("<iframe(?![^>]*\\ssrc=)[^>]*></iframe>");
    private static final Pattern LINK_TARGET = Pattern.compile("_blank|_self");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "hr", "h1", "h2", "h3", "h4", "strong", "b", "em", "i", "u", "s", "del", "ins",
                    "sub", "sup", "code", "pre", "blockquote", "ul", "ol", "li", "figure", "figcaption", "span", "div",
                    "a", "img", "video", "source")
            .allowUrlProtocols("http", "https", "mailto", "tel")
            .allowAttributes("class").matching(CSS_CLASSES).globally()
            .allowAttributes("data-type").matching(DATA_TOKEN).onElements("figure", "div", "span", "img")
            .allowAttributes("style").matching(TEXT_ALIGN).onElements("p", "h1", "h2", "h3", "h4", "div")
            .allowAttributes("href").onElements("a")
            .allowAttributes("target").matching(LINK_TARGET).onElements("a")
            .requireRelsOnLinks("noopener", "noreferrer")
            .allowAttributes("src", "alt", "title").onElements("img")
            .allowAttributes("width", "height").matching(DIMENSION).onElements("img", "video", "iframe")
            .allowAttributes("src", "poster").onElements("video")
            .allowAttributes("src").onElements("source")
            .allowAttributes("controls", "muted", "loop", "playsinline").matching(BOOLEAN_ATTR).onElements("video")
            .allowAttributes("type").matching(Pattern.compile("video/(mp4|webm)")).onElements("source")
            .allowElements("iframe")
            .allowAttributes("src").matching(YOUTUBE_EMBED).onElements("iframe")
            .allowAttributes("allowfullscreen", "frameborder").matching(Pattern.compile("[a-z0-9]{0,16}")).onElements("iframe")
            .toFactory();

    /** Returns safe HTML; null stays null. Plain text without markup is returned unchanged. */
    public String sanitize(String html) {
        if (html == null || html.isEmpty() || !TAG_LIKE.matcher(html).find()) {
            return html;
        }
        // An iframe whose src was rejected is dropped entirely rather than left behind as an empty shell.
        return SRC_LESS_IFRAME.matcher(POLICY.sanitize(html)).replaceAll("");
    }

    /**
     * Sanitizes every string that contains markup anywhere in a JSON tree (CMS section configuration, published
     * snapshots). Strings without tags - titles, URLs, labels - are untouched, so plain text is never entity-encoded.
     * Returns a new tree; the input is not modified.
     */
    public JsonNode sanitizeTree(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return JsonNodeFactory.instance.textNode(sanitize(node.textValue()));
        }
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), sanitizeTree(field.getValue()));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> copy.add(sanitizeTree(child)));
            return copy;
        }
        return node;
    }
}
