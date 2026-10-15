package com.apsonic.backend.modules.page.document;

/** One problem found in a page document. {@code path} is a JSON-pointer-like location, e.g. sections[2].content.heading.en. */
public record DocumentIssue(String path, String code, String message, Severity severity) {

    public enum Severity { ERROR, WARNING }

    public static DocumentIssue error(String path, String code, String message) {
        return new DocumentIssue(path, code, message, Severity.ERROR);
    }

    public static DocumentIssue warning(String path, String code, String message) {
        return new DocumentIssue(path, code, message, Severity.WARNING);
    }
}
