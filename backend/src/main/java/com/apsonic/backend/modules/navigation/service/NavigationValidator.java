package com.apsonic.backend.modules.navigation.service;

import com.apsonic.backend.modules.navigation.domain.MenuKey;
import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.apsonic.backend.shared.validation.SafeUrls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Checks a menu tree and returns a normalised copy. A tree cannot contain a cycle by construction; the rules that
 * remain are the ones a nested structure can still break: depth, size, duplicates, dangling page references and
 * unsafe targets.
 */
@Component
public class NavigationValidator {

    public static final Set<String> ROUTES = Set.of("/", "/products", "/services", "/contact", "/news");
    static final int MAX_TOTAL_ITEMS = 40;
    private static final int MAX_LABEL = 60;
    private static final Pattern TAG_LIKE = Pattern.compile("<\\s*[A-Za-z/!?]");
    private static final Set<String> LOCALES = Set.of("en", "fr", "zh");
    private static final Set<String> KINDS = Set.of("PAGE", "ROUTE", "EXTERNAL", "GROUP");
    private static final Set<String> FIELDS = Set.of("id", "kind", "pageId", "route", "url", "labels", "visible", "openInNewTab", "children");
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    public record Issue(String path, String message) {
    }

    public record Result(JsonNode items, List<Issue> issues) {
        public boolean valid() {
            return issues.isEmpty();
        }
    }

    /**
     * @param pages        every page the tree references, by id (soft-deleted pages are simply absent)
     * @param forPublish   additionally requires referenced pages to be published and dropdown groups to have children
     */
    public Result validate(MenuKey menu, JsonNode input, Map<Long, CmsPage> pages, boolean forPublish) {
        Run run = new Run(menu, pages, forPublish);
        ArrayNode out = run.tree(input);
        return new Result(out, run.issues);
    }

    private static final class Run {
        private final MenuKey menu;
        private final Map<Long, CmsPage> pages;
        private final boolean forPublish;
        private final List<Issue> issues = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();
        private final Set<Long> pageRefs = new HashSet<>();
        private final Set<String> routes = new HashSet<>();
        private final Set<String> externals = new HashSet<>();
        private int total;

        Run(MenuKey menu, Map<Long, CmsPage> pages, boolean forPublish) {
            this.menu = menu;
            this.pages = pages;
            this.forPublish = forPublish;
        }

        ArrayNode tree(JsonNode input) {
            ArrayNode out = F.arrayNode();
            if (input == null || input.isNull()) {
                return out;
            }
            if (!input.isArray()) {
                issues.add(new Issue("items", "The menu must be a list"));
                return out;
            }
            if (input.size() > menu.maxTopLevel) {
                issues.add(new Issue("items", "At most " + menu.maxTopLevel + " top-level items are allowed"));
                return out;
            }
            for (int i = 0; i < input.size(); i++) {
                ObjectNode item = item(input.get(i), "items[" + i + "]", 1);
                if (item != null) {
                    out.add(item);
                }
            }
            return out;
        }

        private ObjectNode item(JsonNode in, String path, int depth) {
            if (!in.isObject()) {
                issues.add(new Issue(path, "A menu item must be an object"));
                return null;
            }
            if (++total > MAX_TOTAL_ITEMS) {
                if (total == MAX_TOTAL_ITEMS + 1) {
                    issues.add(new Issue(path, "A menu can have at most " + MAX_TOTAL_ITEMS + " items in total"));
                }
                return null;
            }
            for (Iterator<String> it = in.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!FIELDS.contains(name)) {
                    issues.add(new Issue(path + "." + name, "Unknown field: " + name));
                }
            }
            ObjectNode out = F.objectNode();
            String id = text(in.get("id"));
            if (id == null) {
                id = UUID.randomUUID().toString();
            } else if (!isUuid(id)) {
                issues.add(new Issue(path + ".id", "Item ids must be UUIDs"));
            }
            if (!ids.add(id)) {
                issues.add(new Issue(path + ".id", "Two items share the same id"));
            }
            out.put("id", id);

            String kind = text(in.get("kind"));
            if (kind == null || !KINDS.contains(kind)) {
                issues.add(new Issue(path + ".kind", "Item type must be one of " + KINDS));
                kind = "GROUP";
            }
            out.put("kind", kind);
            target(in, out, kind, path);

            out.set("labels", labels(in.get("labels"), path + ".labels"));
            out.put("visible", !in.has("visible") || in.path("visible").asBoolean(true));
            out.put("openInNewTab", in.path("openInNewTab").asBoolean(false));

            ArrayNode children = F.arrayNode();
            JsonNode inChildren = in.get("children");
            if (inChildren != null && !inChildren.isNull()) {
                if (!inChildren.isArray()) {
                    issues.add(new Issue(path + ".children", "children must be a list"));
                } else if (!inChildren.isEmpty()) {
                    if (depth >= menu.maxDepth) {
                        issues.add(new Issue(path + ".children", "The " + menu.name().toLowerCase()
                                + " menu supports " + menu.maxDepth + " levels at most"));
                    } else if ("EXTERNAL".equals(kind) || ("ROUTE".equals(kind) && "/products".equals(in.path("route").asText()))) {
                        issues.add(new Issue(path + ".children", "This item cannot have a dropdown. Move its sub-items out first"));
                    } else {
                        for (int i = 0; i < inChildren.size(); i++) {
                            ObjectNode child = item(inChildren.get(i), path + ".children[" + i + "]", depth + 1);
                            if (child != null) {
                                children.add(child);
                            }
                        }
                    }
                }
            }
            out.set("children", children);
            if (forPublish && "GROUP".equals(kind) && children.isEmpty()) {
                issues.add(new Issue(path, "This heading is empty. Put at least one item under it, or remove it"));
            }
            return out;
        }

        private void target(JsonNode in, ObjectNode out, String kind, String path) {
            switch (kind) {
                case "PAGE" -> {
                    JsonNode raw = in.get("pageId");
                    if (raw == null || !raw.isIntegralNumber()) {
                        issues.add(new Issue(path + ".pageId", "Pick the page this link should open"));
                        return;
                    }
                    long pageId = raw.longValue();
                    // Same node type Jackson produces when the JSON is read back (IntNode when it fits). Hibernate
                    // compares JSON trees node by node; a LongNode here would look "changed" after every flush and
                    // cause a second UPDATE (and version bump) at commit.
                    if (pageId <= Integer.MAX_VALUE && pageId >= Integer.MIN_VALUE) {
                        out.put("pageId", (int) pageId);
                    } else {
                        out.put("pageId", pageId);
                    }
                    if (!pageRefs.add(pageId)) {
                        issues.add(new Issue(path + ".pageId", "This page is already in the menu. Each page can be added only once"));
                    }
                    CmsPage page = pages.get(pageId);
                    if (page == null || page.getStatus() == PageStatus.ARCHIVED) {
                        issues.add(new Issue(path + ".pageId", "The page this link points to was deleted or archived. Pick another page"));
                    } else if (forPublish && page.getStatus() != PageStatus.PUBLISHED) {
                        issues.add(new Issue(path + ".pageId", "The page \"" + page.getTitle() + "\" is not published yet. Publish that page first, then publish the menu"));
                    }
                }
                case "ROUTE" -> {
                    String route = text(in.get("route"));
                    if (route == null || !ROUTES.contains(route)) {
                        issues.add(new Issue(path + ".route", "Pick which built-in page this link should open"));
                        return;
                    }
                    out.put("route", route);
                    if (!routes.add(route)) {
                        issues.add(new Issue(path + ".route", "This page is already in the menu. Each page can be added only once"));
                    }
                }
                case "EXTERNAL" -> {
                    String url = text(in.get("url"));
                    boolean absolute = url != null && !url.startsWith("/") && !url.startsWith("#") && SafeUrls.isSafeLink(url);
                    if (!absolute) {
                        issues.add(new Issue(path + ".url", "Type the full web address, starting with https:// (or mailto: / tel:)"));
                        return;
                    }
                    out.put("url", url);
                    if (!externals.add(url.toLowerCase())) {
                        issues.add(new Issue(path + ".url", "This link is already in the menu. Each link can be added only once"));
                    }
                }
                default -> {
                    // GROUP: a heading with no destination of its own.
                }
            }
        }

        private ObjectNode labels(JsonNode in, String path) {
            ObjectNode out = F.objectNode();
            if (in != null && !in.isNull()) {
                if (!in.isObject()) {
                    issues.add(new Issue(path, "labels must be a {en, fr, zh} object"));
                } else {
                    for (Iterator<String> it = in.fieldNames(); it.hasNext(); ) {
                        String locale = it.next();
                        if (!LOCALES.contains(locale)) {
                            issues.add(new Issue(path + "." + locale, "Unsupported language: " + locale));
                            continue;
                        }
                        String label = text(in.get(locale));
                        if (label == null) {
                            continue;
                        }
                        if (label.length() > MAX_LABEL || TAG_LIKE.matcher(label).find()) {
                            issues.add(new Issue(path + "." + locale, "Labels are plain text up to " + MAX_LABEL + " characters"));
                            continue;
                        }
                        out.put(locale, label);
                    }
                }
            }
            if (!out.has("en")) {
                issues.add(new Issue(path + ".en", "Type the English name that visitors will see"));
            }
            return out;
        }

        private static String text(JsonNode node) {
            if (node == null || node.isNull() || !node.isTextual()) {
                return null;
            }
            String value = node.textValue().strip();
            return value.isEmpty() ? null : value;
        }

        private static boolean isUuid(String value) {
            try {
                return UUID.fromString(value).toString().equalsIgnoreCase(value);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
