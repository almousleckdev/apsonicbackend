package com.apsonic.backend.modules.page.document;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Outcome of checking a page document: the normalised copy (sanitised rich text, trimmed strings, assigned ids),
 * every issue found, and how complete each language is.
 */
public record DocumentCheck(JsonNode document, List<DocumentIssue> issues, Map<String, Completeness> completeness) {

    /** How many translatable text values a language has filled in, out of those the English copy has. */
    public record Completeness(int filled, int total) {
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == DocumentIssue.Severity.ERROR);
    }

    public List<DocumentIssue> errors() {
        return issues.stream().filter(i -> i.severity() == DocumentIssue.Severity.ERROR).toList();
    }
}
