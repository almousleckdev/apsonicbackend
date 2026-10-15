package com.apsonic.backend.modules.page.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.apsonic.backend.modules.page.document.FieldDef.choice;
import static com.apsonic.backend.modules.page.document.FieldDef.color;
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
 *
 * <p>Every value that influences appearance is either a closed choice, a number in a range, or a #rrggbb color:
 * nothing a CMS user types can become CSS, markup or script.
 */
@Component
public class SectionRegistry {

    public static final Set<String> LOCALES = Set.of("en", "fr", "zh");

    private static final String[] POSITIONS = {"center", "top", "bottom", "left", "right", "top-left", "top-right", "bottom-left", "bottom-right"};
    private static final String[] ICONS = {"none", "shield", "truck", "wrench", "zap", "award", "globe", "users", "check",
            "star", "clock", "map-pin", "phone", "heart", "settings", "leaf", "bike"};

    /** Typefaces an editor can pick for a piece of text. The frontend maps each name to a bundled font stack. */
    private static final String[] FONTS = {"default", "serif", "condensed", "mono"};
    private static final String[] FONT_LABELS = {"Apsonic default", "Elegant serif", "Bold condensed", "Typewriter"};

    /** Size (as % of the normal size), color and typeface for one piece of text: "eyebrow", "heading" or "subheading". */
    private static List<FieldDef> textStyle(String role, String group) {
        return List.of(
                number(role + "Size", "Size (%)", 50, 300, 100).group(group),
                color(role + "Color", "Color").help("Leave empty to use the section's text color.").group(group),
                choice(role + "Font", "Font", "default", FONTS).labels(FONT_LABELS).group(group));
    }

    @SafeVarargs
    private static List<FieldDef> concatFields(List<FieldDef>... parts) {
        List<FieldDef> all = new java.util.ArrayList<>();
        for (List<FieldDef> part : parts) {
            all.addAll(part);
        }
        return List.copyOf(all);
    }

    private static final List<String> TEXT_STYLE_NAMES = List.of(
            "eyebrowSize", "eyebrowColor", "eyebrowFont", "headingSize", "headingColor", "headingFont",
            "subheadingSize", "subheadingColor", "subheadingFont");

    /** Layout and look shared by sections. Which of them a type uses is decided by its definition. */
    private static final List<FieldDef> STYLE_FIELDS = concatFields(List.of(
            choice("background", "Background", "white", "white", "light", "dark", "brand", "custom")
                    .labels("White", "Light grey", "Dark", "Apsonic green", "My own color").group("Background"),
            color("backgroundColor", "Background color").showWhen("background", "custom").group("Background"),
            media("backgroundImage", "Background picture").group("Background"),
            choice("backgroundSize", "Picture size", "cover", "cover", "contain", "auto")
                    .labels("Fill the section", "Fit inside", "Original size").showWhen("backgroundImage", "*").group("Background"),
            choice("backgroundPosition", "Picture position", "center", POSITIONS).showWhen("backgroundImage", "*").group("Background"),
            flag("backgroundFixed", "Keep the picture still while scrolling", false).showWhen("backgroundImage", "*").group("Background"),
            color("backgroundOverlayColor", "Tint color").showWhen("backgroundImage", "*").group("Background"),
            number("backgroundOverlay", "Tint strength (%)", 0, 90, 0).showWhen("backgroundImage", "*").group("Background"),

            color("textColor", "Text color").help("Leave empty to choose automatically from the background.").group("Text"),
            color("accentColor", "Accent color").help("Small labels, icons and figures. Empty = Apsonic green.").group("Text"),

            color("buttonColor", "Button color").group("Buttons"),
            color("buttonTextColor", "Button text color").group("Buttons"),
            choice("buttonShape", "Button shape", "pill", "pill", "rounded", "square").labels("Pill", "Rounded", "Square").group("Buttons"),

            choice("paddingTop", "Space above", "md", "none", "sm", "md", "lg", "xl").labels("None", "Small", "Medium", "Large", "Extra large").group("Spacing"),
            choice("paddingBottom", "Space below", "md", "none", "sm", "md", "lg", "xl").labels("None", "Small", "Medium", "Large", "Extra large").group("Spacing"),
            choice("width", "Content width", "normal", "narrow", "normal", "wide").labels("Narrow", "Normal", "Wide").group("Spacing"),

            choice("showOn", "Show on", "all", "all", "desktop", "mobile").labels("All screens", "Computers and tablets only", "Phones only").group("Visibility")),
            textStyle("eyebrow", "Small label"), textStyle("heading", "Heading"), textStyle("subheading", "Subheading"));

    private static final List<String> STYLE_BUTTONS = List.of("buttonColor", "buttonTextColor", "buttonShape");
    private static final List<String> STYLE_COMMON = List.of("textColor", "accentColor", "paddingTop", "paddingBottom", "width", "showOn");

    private final Map<String, SectionDefinition> definitions = new LinkedHashMap<>();

    // --- the three pieces of text most sections open with ---------------------------------------------------

    private static FieldDef eyebrow() {
        return text("eyebrow", "Small label above").localized().maxLength(60).group("Text");
    }

    private static FieldDef heading() {
        return text("heading", "Heading").localized().maxLength(160).group("Text");
    }

    private static FieldDef intro() {
        return textarea("intro", "Subheading").localized().maxLength(600).group("Text");
    }

    // --- reusable field groups ---------------------------------------------------------------------------------

    private static FieldDef position(String name, String label) {
        return choice(name, label, "center", POSITIONS);
    }

    /** Buttons with their own wording, link and (optionally) colors. */
    private static FieldDef buttons(int max) {
        return list("ctas", "Buttons", 0, max,
                text("label", "Label").localized().required().maxLength(60),
                url("url", "Link").required(),
                choice("variant", "Style", "primary", "primary", "secondary").labels("Solid", "Outline"),
                color("color", "Color").help("Overrides the section's button color."),
                color("textColor", "Text color"),
                flag("newTab", "Open in a new tab", false)).group("Buttons");
    }

    private static final String[] COLUMNS_234 = {"2", "3", "4"};

    public SectionRegistry() {
        register(new SectionDefinition("hero", "Hero banner", "Large banner with a heading, picture or video and buttons.",
                "Banners", List.of(
                eyebrow(),
                text("heading", "Heading").localized().required().maxLength(160).group("Text"),
                textarea("subheading", "Subheading").localized().maxLength(400).group("Text"),
                choice("align", "Text alignment", "left", "left", "center", "right").group("Text"),
                choice("verticalAlign", "Text position", "center", "top", "center", "bottom").labels("Top", "Middle", "Bottom").group("Text"),

                media("desktopImage", "Picture").group("Picture"),
                media("mobileImage", "Picture for phones").help("Shown on small screens. Falls back to the main picture.").group("Picture"),
                media("videoUrl", "Background video").help("Optional MP4/WebM. The picture is shown while it loads.").group("Picture"),
                choice("imageSize", "Picture size", "cover", "cover", "contain").labels("Fill the banner", "Fit inside").group("Picture"),
                position("imagePosition", "Picture position").help("Which part of the picture stays in view when it is cropped.").group("Picture"),

                color("overlayColor", "Tint color").help("Needs a picture, a video or a background color to be visible.").group("Tint over the picture"),
                number("overlay", "Tint strength (%)", 0, 90, 40).group("Tint over the picture"),
                choice("overlayStyle", "Tint style", "solid", "solid", "bottom", "top").labels("Even", "Darker at the bottom", "Darker at the top").group("Tint over the picture"),
                color("backgroundColor", "Background color").help("Shown behind the picture. Empty = near black.").group("Tint over the picture"),

                choice("height", "Height", "lg", "md", "lg", "full", "custom").labels("Medium", "Large", "Full screen", "My own height").group("Size"),
                number("minHeight", "Height (px)", 240, 1000, 560).showWhen("height", "custom").group("Size"),
                buttons(3)))
                .withStyle(concat(concat(List.of("textColor", "accentColor", "showOn"), TEXT_STYLE_NAMES), STYLE_BUTTONS), Map.of()));

        register(new SectionDefinition("rich-text", "Rich text", "Formatted text: headings, lists, links and images.",
                "Content", List.of(
                richText("body", "Content").localized().required().group("Content"),
                choice("align", "Alignment", "left", "left", "center", "right").group("Look"),
                choice("size", "Text size", "md", "md", "lg").labels("Normal", "Large").group("Look"))));

        register(new SectionDefinition("text-image", "Text and image", "Text next to an image, on either side.",
                "Content", List.of(
                eyebrow(),
                text("heading", "Heading").localized().required().maxLength(160).group("Text"),
                richText("body", "Text").localized().maxLength(10_000).group("Text"),
                choice("textAlign", "Text alignment", "left", "left", "center").group("Text"),
                buttons(2),

                media("image", "Image").group("Image"),
                text("imageAlt", "Image description").localized().maxLength(200).group("Image"),
                choice("imagePosition", "Image side", "left", "left", "right").group("Image"),
                choice("imageRatio", "Image shape", "4-3", "4-3", "1-1", "16-9", "3-4").labels("Landscape 4:3", "Square", "Wide 16:9", "Portrait 3:4").group("Image"),
                choice("imageFit", "Image fit", "cover", "cover", "contain").labels("Fill the frame", "Show the whole image").group("Image"),
                choice("imageStyle", "Image corners", "rounded", "rounded", "square", "circle").labels("Rounded", "Square", "Circle").group("Image"),
                choice("verticalAlign", "Align with image", "center", "top", "center", "bottom").labels("Top", "Middle", "Bottom").group("Image"))));

        register(new SectionDefinition("gallery", "Image gallery", "A grid of images with optional captions.",
                "Media", List.of(
                eyebrow(), heading(), intro(),
                list("items", "Images", 1, 36,
                        media("image", "Image").required(),
                        text("caption", "Caption").localized().maxLength(200)).group("Images"),
                choice("columns", "Columns", "3", "2", "3", "4", "5").group("Look"),
                choice("ratio", "Image shape", "4-3", "4-3", "1-1", "16-9", "auto").labels("Landscape 4:3", "Square", "Wide 16:9", "Original").group("Look"),
                choice("gap", "Space between", "md", "sm", "md", "lg").labels("Small", "Medium", "Large").group("Look"),
                choice("rounded", "Corners", "xl", "none", "md", "xl").labels("Square", "Slightly rounded", "Rounded").group("Look"),
                flag("lightbox", "Open large when clicked", true).group("Look"))));

        register(new SectionDefinition("video", "Video", "A single video with a poster image.",
                "Media", List.of(
                eyebrow(), heading(), intro(),
                text("caption", "Caption").localized().maxLength(300).group("Text"),
                media("videoUrl", "Video file").required().help("MP4 or WebM, uploaded or an https link.").group("Video"),
                media("poster", "Poster image").group("Video"),
                choice("ratio", "Shape", "16-9", "16-9", "4-3", "1-1", "21-9").labels("Wide 16:9", "Classic 4:3", "Square", "Cinema 21:9").group("Video"),
                flag("controls", "Show play controls", true).group("Video"),
                flag("autoplay", "Start by itself (muted)", false).group("Video"),
                flag("loop", "Repeat", false).group("Video"),
                flag("rounded", "Rounded corners", true).group("Video"))));

        register(new SectionDefinition("feature-grid", "Feature grid", "Icon cards that highlight strengths or steps.",
                "Content", List.of(
                eyebrow(), heading(), intro(),
                list("items", "Cards", 1, 12,
                        choice("icon", "Icon", "check", ICONS),
                        text("title", "Title").localized().required().maxLength(100),
                        textarea("text", "Text").localized().maxLength(400),
                        url("url", "Link (optional)")).group("Cards"),
                choice("columns", "Columns", "3", "2", "3", "4").group("Look"),
                choice("cardStyle", "Card style", "boxed", "boxed", "filled", "plain").labels("With border", "Filled", "No card").group("Look"),
                choice("align", "Alignment", "left", "left", "center").group("Look"),
                choice("iconStyle", "Icon shape", "square", "square", "circle", "plain").labels("Rounded square", "Circle", "Just the icon").group("Look"),
                color("cardColor", "Card color").group("Look"))));

        register(new SectionDefinition("stats", "Statistics", "Key numbers and achievements.",
                "Content", List.of(
                eyebrow(), heading(), intro(),
                list("items", "Figures", 1, 12,
                        text("value", "Value").required().maxLength(20).help("For example 25+ or 1.2M"),
                        text("label", "Label").localized().required().maxLength(100),
                        text("note", "Small note").localized().maxLength(120),
                        choice("icon", "Icon", "none", ICONS),
                        color("color", "Figure color").help("Overrides the color for this figure only.")).group("Figures"),
                choice("columns", "Per row", "auto", "auto", "2", "3", "4", "6").labels("Automatic", "2", "3", "4", "6").group("Look"),
                choice("align", "Alignment", "center", "left", "center").group("Look"),
                choice("cardStyle", "Card style", "plain", "plain", "boxed", "filled").labels("No card", "With border", "Filled").group("Look"),
                choice("valueSize", "Figure size", "lg", "md", "lg", "xl").labels("Medium", "Large", "Extra large").group("Look"),
                color("valueColor", "Figures color").help("Empty = the section's accent color.").group("Colors"),
                color("labelColor", "Labels color").group("Colors"),
                color("cardColor", "Card color").showWhen("cardStyle", "boxed", "filled").group("Colors"))));

        register(new SectionDefinition("product-showcase", "Product showcase", "Shows products from the catalogue.",
                "Showcase", List.of(
                eyebrow(), heading(), intro(),
                text("categorySlug", "Category").maxLength(80).help("Leave empty to show the latest products.").group("Products"),
                number("limit", "Number of products", 1, 12, 4).group("Products"),
                choice("columns", "Per row", "4", COLUMNS_234).group("Look"),
                flag("showCategory", "Show the category name", true).group("Look"),
                buttons(2))));

        register(new SectionDefinition("service-showcase", "Service showcase", "Cards describing the services you offer.",
                "Showcase", List.of(
                eyebrow(), heading(), intro(),
                list("items", "Services", 1, 12,
                        text("title", "Title").localized().required().maxLength(100),
                        textarea("text", "Text").localized().maxLength(400),
                        media("image", "Image"),
                        url("url", "Link")).group("Services"),
                choice("columns", "Per row", "3", COLUMNS_234).group("Look"),
                color("cardColor", "Card color").group("Look"))));

        register(new SectionDefinition("cta-banner", "Call to action", "A bold banner that sends visitors somewhere.",
                "Conversion", List.of(
                eyebrow(),
                text("heading", "Heading").localized().required().maxLength(160).group("Text"),
                textarea("text", "Subheading").localized().maxLength(400).group("Text"),
                choice("align", "Text alignment", "center", "left", "center", "right").group("Text"),

                media("image", "Background picture").group("Picture"),
                choice("imageSize", "Picture size", "cover", "cover", "contain", "auto").labels("Fill the banner", "Fit inside", "Original size").showWhen("image", "*").group("Picture"),
                position("imagePosition", "Picture position").showWhen("image", "*").group("Picture"),
                flag("imageFixed", "Keep the picture still while scrolling", false).showWhen("image", "*").group("Picture"),
                color("overlayColor", "Tint color").showWhen("image", "*").group("Picture"),
                number("overlay", "Tint strength (%)", 0, 90, 50).showWhen("image", "*").group("Picture"),

                choice("height", "Height", "md", "sm", "md", "lg", "custom").labels("Small", "Medium", "Large", "My own height").group("Size"),
                number("minHeight", "Height (px)", 160, 800, 320).showWhen("height", "custom").group("Size"),
                buttons(2)))
                .withStyle(concat(List.of("background", "backgroundColor", "paddingTop", "paddingBottom", "width"), concat(concat(STYLE_COMMON, TEXT_STYLE_NAMES), STYLE_BUTTONS)),
                        Map.of("background", "brand", "paddingTop", "lg", "paddingBottom", "lg")));

        register(new SectionDefinition("testimonials", "Testimonials", "What customers say about Apsonic, with their photo and role.",
                "Social proof", List.of(
                eyebrow(), heading(), intro(),
                                choice("source", "Where the testimonials come from", "custom", "custom", "site")
                        .labels("Written here", "The site list (Home Page > Testimonials)").group("Testimonials"),
                number("limit", "Number shown", 1, 12, 3).showWhen("source", "site").group("Testimonials"),
                list("items", "Testimonials", 0, 12,
                        textarea("quote", "What they said").localized().required().maxLength(600),
                        text("name", "Name").required().maxLength(80),
                        text("role", "Role or company").localized().maxLength(100),
                        media("photo", "Photo"),
                        number("rating", "Stars (0 = none)", 0, 5, 5)).showWhen("source", "custom").group("Testimonials"),
                choice("columns", "Per row", "3", "1", "2", "3").group("Look"),
                choice("cardStyle", "Card style", "filled", "filled", "boxed", "plain").labels("Filled", "With border", "No card").group("Look"),
                choice("align", "Alignment", "left", "left", "center").group("Look"),
                flag("showQuoteIcon", "Show the quote mark", true).group("Look"),
                color("cardColor", "Card color").group("Colors"),
                color("starColor", "Star color").group("Colors"))));

        register(new SectionDefinition("news-list", "News", "The latest published news articles.",
                "Showcase", List.of(
                eyebrow(), heading(), intro(),
                number("limit", "Number of articles", 1, 6, 3).group("News"),
                choice("columns", "Per row", "3", "2", "3").group("News"),
                flag("showDate", "Show the date", true).group("News"),
                text("ctaLabel", "Button label").localized().maxLength(60).group("Buttons"))));

        register(new SectionDefinition("faq", "FAQ", "Questions and answers.",
                "Content", List.of(
                eyebrow(), heading(), intro(),
                list("items", "Questions", 1, 30,
                        text("question", "Question").localized().required().maxLength(250),
                        richText("answer", "Answer").localized().required().maxLength(5000)).group("Questions"),
                choice("style", "Look", "boxed", "boxed", "lines").labels("Cards", "Simple lines").group("Look"),
                flag("openFirst", "Open the first question", false).group("Look"))));

        register(new SectionDefinition("dealers", "Dealers and locations", "Dealer addresses and contact details.",
                "Showcase", List.of(
                eyebrow(), heading(), intro(),
                list("items", "Locations", 1, 40,
                        text("name", "Name").required().maxLength(120),
                        text("city", "City").maxLength(80),
                        text("country", "Country").maxLength(80),
                        textarea("address", "Address").localized().maxLength(300),
                        text("phone", "Phone").maxLength(40),
                        text("email", "Email").maxLength(120),
                        url("mapUrl", "Map link")).group("Locations"),
                choice("columns", "Per row", "3", "2", "3").group("Look"),
                color("cardColor", "Card color").group("Look"))));

        register(new SectionDefinition("contact", "Contact details", "Address, phone and email, with a link to the form.",
                "Conversion", List.of(
                eyebrow(), heading(), intro(),
                text("email", "Email").maxLength(120).group("Details"),
                text("phone", "Phone").maxLength(40).group("Details"),
                textarea("address", "Address").localized().maxLength(300).group("Details"),
                text("hours", "Opening hours").localized().maxLength(200).group("Details"),
                flag("showFormLink", "Link to the contact form", true).group("Look"))));

        register(new SectionDefinition("spacer", "Spacer", "Empty vertical space, optionally with a line.",
                "Layout", List.of(
                choice("size", "Height", "md", "sm", "md", "lg", "xl").labels("Small", "Medium", "Large", "Extra large").group("Space"),
                flag("showLine", "Draw a line", false).group("Line"),
                color("lineColor", "Line color").showWhen("showLine", "true").group("Line")))
                .withStyle(List.of("background", "backgroundColor", "showOn"), Map.of()));
    }

    /**
     * Brings content saved under an earlier definition up to date, so changing a section's fields never strands
     * existing drafts. Currently: the single "button label + link" fields became the "buttons" list. The result is
     * validated like any other content, so an old unsafe link is still rejected.
     */
    public JsonNode upgradeContent(String type, JsonNode content) {
        if (content == null || !content.isObject()) {
            return content;
        }
        if ("hero".equals(type) && content.has("headingSize")) {
            // The heading size moved to the shared text styles (style.headingSize, a percentage).
            ObjectNode withoutSize = content.deepCopy();
            withoutSize.remove("headingSize");
            return withoutSize;
        }
        String labelKey;
        String urlKey;
        switch (type == null ? "" : type) {
            case "cta-banner" -> { labelKey = "buttonLabel"; urlKey = "buttonUrl"; }
            case "text-image", "product-showcase" -> { labelKey = "ctaLabel"; urlKey = "ctaUrl"; }
            default -> { return content; }
        }
        if (!content.has(labelKey) && !content.has(urlKey)) {
            return content;
        }
        ObjectNode upgraded = content.deepCopy();
        JsonNode label = upgraded.remove(labelKey);
        JsonNode url = upgraded.remove(urlKey);
        boolean hasButtons = upgraded.path("ctas").isArray() && !upgraded.path("ctas").isEmpty();
        if (!hasButtons && url != null && url.isTextual() && !url.textValue().isBlank()) {
            ObjectNode button = JsonNodeFactory.instance.objectNode();
            if (label != null && !label.isNull()) {
                button.set("label", label);
            }
            button.set("url", url);
            button.put("variant", "primary");
            upgraded.putArray("ctas").add(button);
        }
        return upgraded;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
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
        ObjectNode style = defaults(applicableStyle(definition));
        definition.styleDefaults().forEach((k, v) -> {
            if (v instanceof String s) {
                style.put(k, s);
            }
        });
        section.set("style", style);
        section.set("content", defaults(definition.fields()));
        return section;
    }

    private List<FieldDef> applicableStyle(SectionDefinition definition) {
        if (definition.styleFieldNames() == null) {
            return STYLE_FIELDS;
        }
        Map<String, FieldDef> byName = new HashMap<>();
        STYLE_FIELDS.forEach(f -> byName.put(f.getName(), f));
        return definition.styleFieldNames().stream().map(byName::get).filter(java.util.Objects::nonNull).toList();
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
