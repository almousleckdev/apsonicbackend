package com.apsonic.backend.modules.page.document;

import java.util.List;
import java.util.Map;

/**
 * One kind of section the page builder offers.
 *
 * @param styleFieldNames which of the shared layout/style fields apply to this section (null = all of them)
 * @param styleDefaults   style values a new section of this type starts with, overriding the shared defaults
 */
public record SectionDefinition(String type, String label, String description, String category, List<FieldDef> fields,
                                List<String> styleFieldNames, Map<String, Object> styleDefaults) {

    public SectionDefinition(String type, String label, String description, String category, List<FieldDef> fields) {
        this(type, label, description, category, fields, null, Map.of());
    }

    public SectionDefinition withStyle(List<String> names, Map<String, Object> defaults) {
        return new SectionDefinition(type, label, description, category, fields, names, defaults);
    }
}
