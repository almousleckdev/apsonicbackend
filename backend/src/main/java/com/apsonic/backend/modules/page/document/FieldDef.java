package com.apsonic.backend.modules.page.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

/**
 * Declarative description of one field of a section. Built once at startup (see {@link SectionRegistry}); the same
 * description validates every save, supplies defaults for new sections and is served to the editor so its property
 * panel is generated instead of hand-written per section type.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FieldDef {

    private final String name;
    private final FieldKind kind;
    private final String label;
    private boolean localized;
    private boolean required;
    private Integer maxLength;
    private Integer min;
    private Integer max;
    private List<String> options;
    /** Friendly names for {@link #options}, in the same order (optional). */
    private List<String> optionLabels;
    private Object defaultValue;
    private List<FieldDef> itemFields;
    private Integer minItems;
    private Integer maxItems;
    private String help;
    /** Editor grouping ("Picture", "Buttons"...). Presentation only. */
    private String group;
    /** Show this field only while another field of the same object has one of these values ("*" = any non-empty value). */
    private String showWhenField;
    private List<String> showWhenValues;

    private FieldDef(String name, FieldKind kind, String label) {
        this.name = name;
        this.kind = kind;
        this.label = label;
    }

    public static FieldDef of(String name, FieldKind kind, String label) {
        return new FieldDef(name, kind, label);
    }

    public static FieldDef text(String name, String label) {
        return of(name, FieldKind.TEXT, label).maxLength(200);
    }

    public static FieldDef textarea(String name, String label) {
        return of(name, FieldKind.TEXTAREA, label).maxLength(2000);
    }

    public static FieldDef richText(String name, String label) {
        return of(name, FieldKind.RICHTEXT, label).maxLength(40_000);
    }

    public static FieldDef url(String name, String label) {
        return of(name, FieldKind.URL, label);
    }

    public static FieldDef media(String name, String label) {
        return of(name, FieldKind.MEDIA, label);
    }

    /** A #rrggbb color; leaving it empty means "automatic". */
    public static FieldDef color(String name, String label) {
        return of(name, FieldKind.COLOR, label);
    }

    public static FieldDef choice(String name, String label, String defaultValue, String... options) {
        FieldDef field = of(name, FieldKind.ENUM, label);
        field.options = List.of(options);
        field.defaultValue = defaultValue;
        return field;
    }

    public static FieldDef number(String name, String label, int min, int max, int defaultValue) {
        FieldDef field = of(name, FieldKind.NUMBER, label);
        field.min = min;
        field.max = max;
        field.defaultValue = defaultValue;
        return field;
    }

    public static FieldDef flag(String name, String label, boolean defaultValue) {
        FieldDef field = of(name, FieldKind.BOOLEAN, label);
        field.defaultValue = defaultValue;
        return field;
    }

    public static FieldDef list(String name, String label, int minItems, int maxItems, FieldDef... itemFields) {
        FieldDef field = of(name, FieldKind.LIST, label);
        field.minItems = minItems;
        field.maxItems = maxItems;
        field.itemFields = List.of(itemFields);
        return field;
    }

    /** The value is a {en, fr, zh} map instead of a single value. */
    public FieldDef localized() {
        this.localized = true;
        return this;
    }

    /** Must be filled to publish (English only for localized fields). Drafts may leave it empty. */
    public FieldDef required() {
        this.required = true;
        return this;
    }

    public FieldDef maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public FieldDef group(String group) {
        this.group = group;
        return this;
    }

    public FieldDef showWhen(String field, String... values) {
        this.showWhenField = field;
        this.showWhenValues = List.of(values);
        return this;
    }

    /** Friendly names for the options, in the same order, e.g. choice(..., "primary", "secondary").labels("Solid", "Outline"). */
    public FieldDef labels(String... labels) {
        this.optionLabels = List.of(labels);
        return this;
    }

    /** Sets the value a new section starts with. */
    public FieldDef withDefault(Object value) {
        this.defaultValue = value;
        return this;
    }

    public FieldDef help(String help) {
        this.help = help;
        return this;
    }
}
