package com.apsonic.backend.modules.page.service;

import java.util.List;

/**
 * Lets the page module ask "is this page still used elsewhere?" without depending on the module that answers
 * (navigation implements it). Keeps the dependency pointing one way.
 */
public interface PageReferenceGuard {

    /** Human-readable places that still reference the page, empty when nothing does. */
    List<String> referencesTo(long pageId);
}
