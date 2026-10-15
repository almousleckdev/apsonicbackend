package com.apsonic.backend.modules.page.document;

/** What a section field holds. The kind decides how the value is validated and which editor control is shown. */
public enum FieldKind {
    /** Single-line plain text (no HTML). */
    TEXT,
    /** Multi-line plain text (no HTML). */
    TEXTAREA,
    /** HTML produced by the rich-text editor; always passed through the HTML sanitizer. */
    RICHTEXT,
    /** A link a visitor can follow (site path, #anchor, http(s), mailto:, tel:). */
    URL,
    /** An uploaded file (/uploads/...) or an absolute http(s) URL. */
    MEDIA,
    /** One of a closed list of values. */
    ENUM,
    /** An integer within a range. */
    NUMBER,
    BOOLEAN,
    /** A list of items that each follow the field's item schema. */
    LIST
}
