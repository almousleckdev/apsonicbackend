package com.apsonic.backend.modules.category.dto;

/** Input rules shared by the create and update requests, so both reject the same malformed values. */
final class CategoryRules {

    /** Letters, digits, dashes and underscores; no spaces, slashes or dots. */
    static final String SLUG = "^[A-Za-z0-9][A-Za-z0-9_-]*$";

    /** Lowercase words joined by dashes: used for the brand and for the menu group a category belongs to. */
    static final String KEY = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** A site-relative path or an http(s) URL. Rejects javascript:, data: and protocol-relative (//host) values. */
    static final String IMAGE = "^(/(?!/)|https?://).*$";

    /** At least one non-space character (null stays allowed on partial updates). */
    static final String NOT_BLANK = "^(?s).*\\S.*$";

    private CategoryRules() {
    }
}
