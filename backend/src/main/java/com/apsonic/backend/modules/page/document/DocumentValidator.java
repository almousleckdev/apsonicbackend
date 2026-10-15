package com.apsonic.backend.modules.page.document;

import com.apsonic.backend.shared.html.HtmlSanitizer;
import com.apsonic.backend.shared.validation.SafeUrls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Checks a page document against the section registry and returns a normalised copy. The backend is the only
 * authority on what may be stored: unknown fields, unknown section types, unsafe URLs and markup in plain-text fields
 * are rejected, and rich text is sanitised here (and again when served).
 *
 * <p>{@link Mode#DRAFT} reports structural problems only, so incomplete work can always be saved.
 * {@link Mode#PUBLISH} additionally requires the fields marked required (English for translated fields) and reports
 * per-language completeness as warnings.
 */
@Component
@RequiredArgsConstructor
public class DocumentValidator {

    public enum Mode { DRAFT, PUBLISH }

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SECTIONS = 60;
    public static final int MAX_DOCUMENT_CHARS = 900_000;

    private static final List<String> LOCALE_ORDER = List.of("en", "fr", "zh");
    private static final Pattern TAG_LIKE = Pattern.compile("<\\s*[A-Za-z/!?]");
    private static final Map<String, Integer> META_FIELDS = Map.of(
            "title", 150, "seoTitle", 120, "seoDescription", 320, "ogTitle", 120, "ogDescription", 320);
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private final SectionRegistry registry;
    private final HtmlSanitizer htmlSanitizer;

    public DocumentCheck check(JsonNode input, Mode mode) {
        Run run = new Run(mode);
        JsonNode normalised = run.document(input);
        return new DocumentCheck(normalised, run.issues, run.completenessResult());
    }

    /** A new empty document that is valid for drafts. */
    public ObjectNode emptyDocument(String englishTitle) {
        ObjectNode doc = F.objectNode();
        doc.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode meta = doc.putObject("meta");
        meta.putObject("en").put("title", englishTitle);
        doc.putObject("seo").put("noIndex", false);
        doc.putArray("sections");
        return doc;
    }

    private final class Run {
        private final Mode mode;
        private final List<DocumentIssue> issues = new ArrayList<>();
        private final Map<String, int[]> completeness = new LinkedHashMap<>();

        Run(Mode mode) {
            this.mode = mode;
            LOCALE_ORDER.forEach(l -> completeness.put(l, new int[2]));
        }

        Map<String, DocumentCheck.Completeness> completenessResult() {
            Map<String, DocumentCheck.Completeness> result = new LinkedHashMap<>();
            completeness.forEach((locale, c) -> result.put(locale, new DocumentCheck.Completeness(c[0], c[1])));
            return result;
        }

        private void error(String path, String code, String message) {
            issues.add(DocumentIssue.error(path, code, message));
        }

        JsonNode document(JsonNode input) {
            ObjectNode out = F.objectNode();
            if (input == null || !input.isObject()) {
                error("", "invalid_document", "The page document must be an object");
                return out;
            }
            if (input.toString().length() > MAX_DOCUMENT_CHARS) {
                error("", "document_too_large", "The page is too large to save");
                return out;
            }
            rejectUnknown(input, Set.of("schemaVersion", "meta", "seo", "sections"), "");
            if (!input.path("schemaVersion").isInt() || input.path("schemaVersion").intValue() != SCHEMA_VERSION) {
                error("schemaVersion", "unsupported_schema", "Unsupported schema version");
            }
            out.put("schemaVersion", SCHEMA_VERSION);
            out.set("meta", meta(input.get("meta")));
            out.set("seo", seo(input.get("seo")));
            out.set("sections", sections(input.get("sections")));
            return out;
        }

        private ObjectNode meta(JsonNode meta) {
            ObjectNode out = F.objectNode();
            if (meta == null || meta.isNull()) {
                meta = F.objectNode();
            }
            if (!meta.isObject()) {
                error("meta", "invalid_type", "meta must be an object");
                return out;
            }
            for (Iterator<String> it = meta.fieldNames(); it.hasNext(); ) {
                String locale = it.next();
                String path = "meta." + locale;
                if (!SectionRegistry.LOCALES.contains(locale)) {
                    error(path, "unknown_locale", "Unsupported language: " + locale);
                    continue;
                }
                JsonNode value = meta.get(locale);
                if (!value.isObject()) {
                    error(path, "invalid_type", "Must be an object");
                    continue;
                }
                rejectUnknown(value, META_FIELDS.keySet(), path);
                ObjectNode localeOut = F.objectNode();
                META_FIELDS.forEach((name, max) -> {
                    String text = plainText(value.get(name), path + "." + name, max, false);
                    if (text != null) {
                        localeOut.put(name, text);
                    }
                });
                out.set(locale, localeOut);
            }
            if (mode == Mode.PUBLISH) {
                if (blank(out.path("en").path("title").asText(null))) {
                    error("meta.en.title", "required", "An English page title is required to publish");
                }
                for (String locale : List.of("fr", "zh")) {
                    if (blank(out.path(locale).path("title").asText(null))) {
                        issues.add(DocumentIssue.warning("meta." + locale + ".title", "missing_translation",
                                "No " + locale + " page title; the English title will be used"));
                    }
                }
            }
            return out;
        }

        private ObjectNode seo(JsonNode seo) {
            ObjectNode out = F.objectNode();
            out.put("noIndex", false);
            if (seo == null || seo.isNull()) {
                return out;
            }
            if (!seo.isObject()) {
                error("seo", "invalid_type", "seo must be an object");
                return out;
            }
            rejectUnknown(seo, Set.of("canonicalUrl", "ogImage", "noIndex"), "seo");
            String canonical = trimmed(seo.get("canonicalUrl"));
            if (canonical != null) {
                if (SafeUrls.isHttpsUrl(canonical)) {
                    out.put("canonicalUrl", canonical);
                } else {
                    error("seo.canonicalUrl", "invalid_url", "The canonical URL must be a full https:// address");
                }
            }
            String image = trimmed(seo.get("ogImage"));
            if (image != null) {
                if (SafeUrls.isSafeMedia(image)) {
                    out.put("ogImage", image);
                } else {
                    error("seo.ogImage", "invalid_media", "The sharing image must be an uploaded file or an http(s) address");
                }
            }
            JsonNode noIndex = seo.get("noIndex");
            if (noIndex != null && !noIndex.isNull()) {
                if (noIndex.isBoolean()) {
                    out.put("noIndex", noIndex.booleanValue());
                } else {
                    error("seo.noIndex", "invalid_type", "Must be true or false");
                }
            }
            return out;
        }

        private ArrayNode sections(JsonNode sections) {
            ArrayNode out = F.arrayNode();
            if (sections == null || sections.isNull()) {
                return out;
            }
            if (!sections.isArray()) {
                error("sections", "invalid_type", "sections must be a list");
                return out;
            }
            if (sections.size() > MAX_SECTIONS) {
                error("sections", "too_many_sections", "A page can have at most " + MAX_SECTIONS + " sections");
                return out;
            }
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < sections.size(); i++) {
                ObjectNode section = section(sections.get(i), "sections[" + i + "]", ids);
                if (section != null) {
                    out.add(section);
                }
            }
            return out;
        }

        private ObjectNode section(JsonNode input, String path, Set<String> ids) {
            if (!input.isObject()) {
                error(path, "invalid_type", "A section must be an object");
                return null;
            }
            rejectUnknown(input, Set.of("id", "type", "hidden", "style", "content"), path);
            String type = input.path("type").asText(null);
            SectionDefinition definition = registry.find(type).orElse(null);
            if (definition == null) {
                error(path + ".type", "unknown_section_type", "Unknown section type: " + type);
                return null;
            }
            String id = trimmed(input.get("id"));
            if (id == null) {
                id = UUID.randomUUID().toString();
            } else if (!isUuid(id)) {
                error(path + ".id", "invalid_id", "Section ids must be UUIDs");
            }
            if (!ids.add(id)) {
                error(path + ".id", "duplicate_id", "Two sections share the same id");
            }
            boolean hidden = input.path("hidden").asBoolean(false);

            ObjectNode out = F.objectNode();
            out.put("id", id);
            out.put("type", type);
            out.put("hidden", hidden);
            out.set("style", style(input.get("style"), path + ".style"));
            // Required-field checks only apply to sections that will be visible.
            boolean enforceRequired = mode == Mode.PUBLISH && !hidden;
            out.set("content", object(input.get("content"), definition.fields(), path + ".content", enforceRequired, !hidden));
            return out;
        }

        private ObjectNode style(JsonNode style, String path) {
            ObjectNode out = F.objectNode();
            if (style == null || style.isNull()) {
                return out;
            }
            if (!style.isObject()) {
                error(path, "invalid_type", "style must be an object");
                return out;
            }
            return object(style, registry.styleFields(), path, false, false);
        }

        /** Validates an object against a field list; unknown keys are errors. */
        private ObjectNode object(JsonNode input, List<FieldDef> fields, String path, boolean enforceRequired, boolean countLocales) {
            ObjectNode out = F.objectNode();
            if (input == null || input.isNull()) {
                input = F.objectNode();
            }
            if (!input.isObject()) {
                error(path, "invalid_type", "Must be an object");
                return out;
            }
            Set<String> known = new HashSet<>();
            for (FieldDef field : fields) {
                known.add(field.getName());
            }
            rejectUnknown(input, known, path);
            for (FieldDef field : fields) {
                JsonNode value = input.get(field.getName());
                String fieldPath = path + "." + field.getName();
                JsonNode normalised = field(field, value, fieldPath, enforceRequired, countLocales);
                if (normalised != null) {
                    out.set(field.getName(), normalised);
                }
            }
            return out;
        }

        private JsonNode field(FieldDef field, JsonNode value, String path, boolean enforceRequired, boolean countLocales) {
            if (field.getKind() == FieldKind.LIST) {
                return list(field, value, path, enforceRequired, countLocales);
            }
            if (field.isLocalized()) {
                return localized(field, value, path, enforceRequired, countLocales);
            }
            JsonNode scalar = scalar(field, value, path);
            if (enforceRequired && field.isRequired() && scalar == null) {
                error(path, "required", field.getLabel() + " is required");
            }
            return scalar;
        }

        private JsonNode localized(FieldDef field, JsonNode value, String path, boolean enforceRequired, boolean countLocales) {
            ObjectNode out = F.objectNode();
            if (value != null && !value.isNull()) {
                if (!value.isObject()) {
                    error(path, "invalid_type", "Must be a {en, fr, zh} object");
                    return null;
                }
                for (Iterator<String> it = value.fieldNames(); it.hasNext(); ) {
                    String locale = it.next();
                    if (!SectionRegistry.LOCALES.contains(locale)) {
                        error(path + "." + locale, "unknown_locale", "Unsupported language: " + locale);
                        continue;
                    }
                    JsonNode text = scalar(field, value.get(locale), path + "." + locale);
                    if (text != null) {
                        out.set(locale, text);
                    }
                }
            }
            boolean hasEnglish = out.has("en");
            if (enforceRequired && field.isRequired() && !hasEnglish) {
                error(path + ".en", "required", field.getLabel() + " is required in English");
            }
            if (countLocales && hasEnglish) {
                for (String locale : LOCALE_ORDER) {
                    int[] c = completeness.get(locale);
                    c[1]++;
                    if (out.has(locale)) {
                        c[0]++;
                    }
                }
            }
            return out.isEmpty() ? null : out;
        }

        private JsonNode list(FieldDef field, JsonNode value, String path, boolean enforceRequired, boolean countLocales) {
            ArrayNode out = F.arrayNode();
            if (value != null && !value.isNull()) {
                if (!value.isArray()) {
                    error(path, "invalid_type", "Must be a list");
                    return out;
                }
                if (value.size() > field.getMaxItems()) {
                    error(path, "too_many_items", field.getLabel() + " can have at most " + field.getMaxItems() + " items");
                    return out;
                }
                for (int i = 0; i < value.size(); i++) {
                    out.add(object(value.get(i), field.getItemFields(), path + "[" + i + "]", enforceRequired, countLocales));
                }
            }
            if (enforceRequired && out.size() < field.getMinItems()) {
                error(path, "too_few_items", field.getLabel() + " needs at least " + field.getMinItems() + " item(s)");
            }
            return out;
        }

        /** Validates and normalises a single value of the field's kind; null means "empty". */
        private JsonNode scalar(FieldDef field, JsonNode value, String path) {
            if (value == null || value.isNull()) {
                return null;
            }
            switch (field.getKind()) {
                case TEXT, TEXTAREA -> {
                    String text = plainText(value, path, field.getMaxLength(), field.getKind() == FieldKind.TEXTAREA);
                    return text == null ? null : F.textNode(text);
                }
                case RICHTEXT -> {
                    if (!value.isTextual()) {
                        error(path, "invalid_type", "Must be text");
                        return null;
                    }
                    String html = value.textValue();
                    if (html.length() > field.getMaxLength()) {
                        error(path, "too_long", "Longer than " + field.getMaxLength() + " characters");
                        return null;
                    }
                    String clean = htmlSanitizer.sanitize(html);
                    boolean empty = blank(clean) || (blank(stripTags(clean)) && !clean.contains("<img") && !clean.contains("<video"));
                    return empty ? null : F.textNode(clean);
                }
                case URL -> {
                    String text = text(value, path);
                    if (text == null) {
                        return null;
                    }
                    if (!SafeUrls.isSafeLink(text)) {
                        error(path, "invalid_url", "Not a valid link. Use a site path such as /contact, an https:// address, mailto: or tel:");
                        return null;
                    }
                    return F.textNode(text);
                }
                case MEDIA -> {
                    String text = text(value, path);
                    if (text == null) {
                        return null;
                    }
                    if (!SafeUrls.isSafeMedia(text)) {
                        error(path, "invalid_media", "Must be an uploaded file or an http(s) address");
                        return null;
                    }
                    return F.textNode(text);
                }
                case ENUM -> {
                    String text = text(value, path);
                    if (text == null) {
                        return null;
                    }
                    if (!field.getOptions().contains(text)) {
                        error(path, "invalid_option", "Must be one of " + field.getOptions());
                        return null;
                    }
                    return F.textNode(text);
                }
                case NUMBER -> {
                    if (!value.isIntegralNumber() || value.intValue() < field.getMin() || value.intValue() > field.getMax()) {
                        error(path, "out_of_range", "Must be a whole number from " + field.getMin() + " to " + field.getMax());
                        return null;
                    }
                    return F.numberNode(value.intValue());
                }
                case BOOLEAN -> {
                    if (!value.isBoolean()) {
                        error(path, "invalid_type", "Must be true or false");
                        return null;
                    }
                    return F.booleanNode(value.booleanValue());
                }
                default -> {
                    error(path, "invalid_type", "Unsupported field");
                    return null;
                }
            }
        }

        private String plainText(JsonNode value, String path, int maxLength, boolean multiline) {
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isTextual()) {
                error(path, "invalid_type", "Must be text");
                return null;
            }
            String text = value.textValue().strip();
            if (text.isEmpty()) {
                return null;
            }
            if (text.length() > maxLength) {
                error(path, "too_long", "Longer than " + maxLength + " characters");
                return null;
            }
            if (TAG_LIKE.matcher(text).find()) {
                error(path, "html_not_allowed", "HTML is not allowed in this field");
                return null;
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 0x20 && !(multiline && (c == '\n' || c == '\r' || c == '\t')) || c == 0x7f) {
                    error(path, "invalid_characters", "Contains control characters");
                    return null;
                }
            }
            return text;
        }

        private void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!allowed.contains(name)) {
                    error(path.isEmpty() ? name : path + "." + name, "unknown_field", "Unknown field: " + name);
                }
            }
        }

        /** Trimmed text of a value that must be a string; anything else is reported and treated as empty. */
        private String text(JsonNode value, String path) {
            if (value != null && !value.isNull() && !value.isTextual()) {
                error(path, "invalid_type", "Must be text");
                return null;
            }
            return trimmed(value);
        }

        private String trimmed(JsonNode value) {
            if (value == null || value.isNull() || !value.isTextual()) {
                return null;
            }
            String text = value.textValue().strip();
            return text.isEmpty() ? null : text;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTags(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ");
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
