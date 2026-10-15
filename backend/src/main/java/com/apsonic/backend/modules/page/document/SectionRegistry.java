package com.apsonic.backend.modules.page.document;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.apsonic.backend.modules.page.document.FieldDef.choice;
import static com.apsonic.backend.modules.page.document.FieldDef.flag;
import static com.apsonic.backend.modules.page.document.FieldDef.list;
import static com.apsonic.backend.modules.page.document.FieldDef.media;
import static com.apsonic.backend.modules.page.document.FieldDef.number;
import static com.apsonic.backend.modules.page.document.FieldDef.richText;
import static com.apsonic.backend.modules.page.document.FieldDef.text;
import static com.apsonic.backend.modules.page.document.FieldDef.textarea;
import static com.apsonic.backend.modules.page.document.FieldDef.url;

/**
 * The closed catalogue of section types. To add a section type: declare it here (fields, limits, defaults) and add a
 * renderer to the frontend registry; validation, defaults and the property panel need no further code.
 */
@Component
public class SectionRegistry {

    public static final Set<String> LOCALES = Set.of("en", "fr", "zh");

    /** Layout options shared by every section (stored in the section's "style" object, never as free CSS). */
    private static final List<FieldDef> STYLE_FIELDS = List.of(
            choice("background", "Background", "white", "white", "light", "dark", "brand"),
            choice("paddingTop", "Space above", "md", "none", "sm", "md", "lg", "xl"),
            choice("paddingBottom", "Space below", "md", "none", "sm", "md", "lg", "xl"),
            choice("width", "Content width", "normal", "narrow", "normal", "wide"));

    private static final String[] ICONS = {"shield", "truck", "wrench", "zap", "award", "globe", "users", "check",
            "star", "clock", "map-pin", "phone", "heart", "settings", "leaf", "bike"};

    private final Map<String, SectionDefinition> definitions = new LinkedHashMap<>();

    public SectionRegistry() {
        register(new SectionDefinition("hero", "Hero banner", "Large banner with a heading, image or video and buttons.",
                "Banners", List.of(
                text("heading", "Heading").localized().required().maxLength(160),
                textarea("subheading", "Subheading").localized().maxLength(400),
                media("desktopImage", "Desktop image"),
                media("mobileImage", "Mobile image").help("Shown on small screens. Falls back to the desktop image."),
                media("videoUrl", "Background video").help("Optional MP4/WebM; the image is the poster."),
                number("overlay", "Dark overlay (%)", 0, 90, 40),
                choice("align", "Text alignment", "left", "left", "center", "right"),
                choice("height", "Height", "lg", "md", "lg", "full"),
                list("ctas", "Buttons", 0, 2,
                        text("label", "Label").localized().required().maxLength(60),
                        url("url", "Link").required(),
                        choice("variant", "Style", "primary", "primary", "secondary")))));

        register(new SectionDefinition("rich-text", "Rich text", "Formatted text: headings, lists, links and images.",
                "Content", List.of(
                richText("body", "Content").localized().required())));

        register(new SectionDefinition("text-image", "Text and image", "Text next to an image, on either side.",
                "Content", List.of(
                text("eyebrow", "Small label").localized().maxLength(60),
                text("heading", "Heading").localized().required().maxLength(160),
                richText("body", "Text").localized().maxLength(10_000),
                media("image", "Image"),
                text("imageAlt", "Image description").localized().maxLength(200),
                choice("imagePosition", "Image position", "left", "left", "right"),
                text("ctaLabel", "Button label").localized().maxLength(60),
                url("ctaUrl", "Button link"))));

        register(new SectionDefinition("gallery", "Image gallery", "A grid of images with optional captions.",
                "Media", List.of(
                text("heading", "Heading").localized().maxLength(160),
                choice("columns", "Columns", "3", "2", "3", "4"),
                list("items", "Images", 1, 24,
                        media("image", "Image").required(),
                        text("caption", "Caption").localized().maxLength(200)))));

        register(new SectionDefinition("video", "Video", "A single video with a poster image.",
                "Media", List.of(
                text("heading", "Heading").localized().maxLength(160),
                media("videoUrl", "Video file").required().help("MP4 or WebM, uploaded or an https link."),
                media("poster", "Poster image"),
                text("caption", "Caption").localized().maxLength(300))));

        register(new SectionDefinition("feature-grid", "Feature grid", "Icon cards that highlight strengths or steps.",
                "Content", List.of(
                text("heading", "Heading").localized().maxLength(160),
                textarea("intro", "Introduction").localized().maxLength(600),
                choice("columns", "Columns", "3", "2", "3", "4"),
                list("items", "Cards", 1, 12,
                        choice("icon", "Icon", "check", ICONS),
                        text("title", "Title").localized().required().maxLength(100),
                        textarea("text", "Text").localized().maxLength(400)))));

        register(new SectionDefinition("stats", "Statistics", "Key numbers and achievements.",
                "Content", List.of(
                text("heading", "Heading").localized().maxLength(160),
                list("items", "Figures", 1, 6,
                        text("value", "Value").required().maxLength(20).help("For example 25+ or 1.2M"),
                        text("label", "Label").localized().required().maxLength(100)))));

        register(new SectionDefinition("product-showcase", "Product showcase", "Shows products from the catalogue.",
                "Showcase", List.of(
                text("heading", "Heading").localized().maxLength(160),
                textarea("intro", "Introduction").localized().maxLength(600),
                text("categorySlug", "Category").maxLength(80).help("Leave empty to show the latest products."),
                number("limit", "Number of products", 1, 12, 4),
                text("ctaLabel", "Button label").localized().maxLength(60),
                url("ctaUrl", "Button link"))));

        register(new SectionDefinition("service-showcase", "Service showcase", "Cards describing the services you offer.",
                "Showcase", List.of(
                text("heading", "Heading").localized().maxLength(160),
                textarea("intro", "Introduction").localized().maxLength(600),
                list("items", "Services", 1, 8,
                        text("title", "Title").localized().required().maxLength(100),
                        textarea("text", "Text").localized().maxLength(400),
                        media("image", "Image"),
                        url("url", "Link")))));

        register(new SectionDefinition("cta-banner", "Call to action", "A bold banner that sends visitors somewhere.",
                "Conversion", List.of(
                text("heading", "Heading").localized().required().maxLength(160),
                textarea("text", "Text").localized().maxLength(400),
                media("image", "Background image"),
                text("buttonLabel", "Button label").localized().required().maxLength(60),
                url("buttonUrl", "Button link").required())));

        register(new SectionDefinition("testimonials", "Testimonials", "Customer voices from the Testimonials list.",
                "Social proof", List.of(
                text("heading", "Heading").localized().maxLength(160),
                number("limit", "Number shown", 1, 9, 3))));

        register(new SectionDefinition("news-list", "News", "The latest published news articles.",
                "Showcase", List.of(
                text("heading", "Heading").localized().maxLength(160),
                number("limit", "Number of articles", 1, 6, 3),
                text("ctaLabel", "Button label").localized().maxLength(60))));

        register(new SectionDefinition("faq", "FAQ", "Questions and answers.",
                "Content", List.of(
                text("heading", "Heading").localized().maxLength(160),
                list("items", "Questions", 1, 30,
                        text("question", "Question").localized().required().maxLength(250),
                        richText("answer", "Answer").localized().required().maxLength(5000)))));

        register(new SectionDefinition("dealers", "Dealers and locations", "Dealer addresses and contact details.",
                "Showcase", List.of(
                text("heading", "Heading").localized().maxLength(160),
                textarea("intro", "Introduction").localized().maxLength(600),
                list("items", "Locations", 1, 40,
                        text("name", "Name").required().maxLength(120),
                        text("city", "City").maxLength(80),
                        text("country", "Country").maxLength(80),
                        textarea("address", "Address").localized().maxLength(300),
                        text("phone", "Phone").maxLength(40),
                        text("email", "Email").maxLength(120),
                        url("mapUrl", "Map link")))));

        register(new SectionDefinition("contact", "Contact details", "Address, phone and email, with a link to the form.",
                "Conversion", List.of(
                text("heading", "Heading").localized().maxLength(160),
                textarea("intro", "Introduction").localized().maxLength(600),
                text("email", "Email").maxLength(120),
                text("phone", "Phone").maxLength(40),
                textarea("address", "Address").localized().maxLength(300),
                text("hours", "Opening hours").localized().maxLength(200),
                flag("showFormLink", "Link to the contact form", true))));

        register(new SectionDefinition("spacer", "Spacer", "Empty vertical space between sections.",
                "Layout", List.of(
                choice("size", "Height", "md", "sm", "md", "lg", "xl"))));
    }

    private void register(SectionDefinition definition) {
        definitions.put(definition.type(), definition);
    }

    public Optional<SectionDefinition> find(String type) {
        return Optional.ofNullable(type == null ? null : definitions.get(type));
    }

    public List<SectionDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public List<FieldDef> styleFields() {
        return STYLE_FIELDS;
    }

    /** A new, valid-for-draft section with the registry defaults filled in. */
    public ObjectNode newSection(String type, String id) {
        SectionDefinition definition = find(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown section type: " + type));
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode section = f.objectNode();
        section.put("id", id);
        section.put("type", type);
        section.put("hidden", false);
        section.set("style", defaults(STYLE_FIELDS));
        section.set("content", defaults(definition.fields()));
        return section;
    }

    private static ObjectNode defaults(List<FieldDef> fields) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode node = f.objectNode();
        for (FieldDef field : fields) {
            Object value = field.getDefaultValue();
            if (field.getKind() == FieldKind.LIST) {
                ArrayNode array = f.arrayNode();
                node.set(field.getName(), array);
            } else if (value instanceof String s) {
                node.put(field.getName(), s);
            } else if (value instanceof Integer i) {
                node.put(field.getName(), i);
            } else if (value instanceof Boolean b) {
                node.put(field.getName(), b);
            }
        }
        return node;
    }
}
