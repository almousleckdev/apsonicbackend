package com.apsonic.backend.modules.page.domain;

public enum RevisionKind {
    /** First version of a page. */
    CREATE,
    /** A saved draft (consecutive saves by one editor are merged). */
    DRAFT,
    /** The content that went live. */
    PUBLISH,
    /** A draft restored from an earlier revision. */
    ROLLBACK,
    /** Converted from the pre-builder page format. */
    MIGRATION,
    /** Pre-builder snapshot; kept for history, cannot be restored. */
    LEGACY
}
