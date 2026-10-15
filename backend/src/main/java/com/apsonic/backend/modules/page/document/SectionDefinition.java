package com.apsonic.backend.modules.page.document;

import java.util.List;

/** One kind of section the page builder offers: its identity, library grouping and the fields of its content. */
public record SectionDefinition(String type, String label, String description, String category, List<FieldDef> fields) {
}
