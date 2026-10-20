package com.apsonic.backend.modules.navigation.service;

import com.apsonic.backend.modules.navigation.domain.MenuKey;
import com.apsonic.backend.modules.navigation.domain.NavigationMenu;
import com.apsonic.backend.modules.navigation.dto.NavigationDtos.MenuView;
import com.apsonic.backend.modules.navigation.dto.NavigationDtos.PageOption;
import com.apsonic.backend.modules.navigation.dto.NavigationDtos.ResolvedItem;
import com.apsonic.backend.modules.navigation.dto.NavigationDtos.ResolvedMenu;
import com.apsonic.backend.modules.navigation.repository.NavigationMenuRepository;
import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.apsonic.backend.modules.page.repository.CmsPageRepository;
import com.apsonic.backend.modules.page.service.PageReferenceGuard;
import com.apsonic.backend.shared.api.ValidationError;
import com.apsonic.backend.shared.exception.ConflictException;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import com.apsonic.backend.shared.exception.ValidationFailedException;
import com.apsonic.backend.shared.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Header and footer navigation, managed independently of page content. Items reference pages by id, so renaming a
 * page's address never breaks a menu. Edits go to the draft; only an explicit publish changes what the site serves.
 */
@Service
@RequiredArgsConstructor
public class NavigationService implements PageReferenceGuard {

    private final NavigationMenuRepository menus;
    private final CmsPageRepository pages;
    private final NavigationValidator validator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    // --- admin ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MenuView get(MenuKey key) {
        return view(load(key));
    }

    @Transactional(readOnly = true)
    public List<PageOption> pageOptions() {
        return pages.findAll(Sort.by("title")).stream()
                .filter(p -> p.getStatus() != PageStatus.ARCHIVED)
                .map(p -> new PageOption(p.getId(), p.getSlug(), p.getTitle(), p.getStatus()))
                .toList();
    }

    @Transactional
    public MenuView saveDraft(MenuKey key, Long expectedVersion, JsonNode items) {
        NavigationMenu menu = load(key);
        assertVersion(menu, expectedVersion);
        NavigationValidator.Result result = validator.validate(key, items, pagesReferencedBy(items), false);
        failIfInvalid(result, "The menu is not valid");
        menu.setDraftItems(result.items());
        return view(menus.saveAndFlush(menu));
    }

    @Transactional
    public MenuView publish(MenuKey key, Long expectedVersion) {
        NavigationMenu menu = load(key);
        assertVersion(menu, expectedVersion);
        NavigationValidator.Result result = validator.validate(key, menu.getDraftItems(), pagesReferencedBy(menu.getDraftItems()), true);
        failIfInvalid(result, "The menu cannot be published yet");
        menu.setDraftItems(result.items());
        menu.setPublishedItems(result.items());
        menu.setPublishedAt(Instant.now());
        menu.setPublishedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        return view(menus.saveAndFlush(menu));
    }

    @Transactional
    public MenuView discardDraft(MenuKey key, Long expectedVersion) {
        NavigationMenu menu = load(key);
        assertVersion(menu, expectedVersion);
        menu.setDraftItems(menu.getPublishedItems());
        return view(menus.saveAndFlush(menu));
    }

    /**
     * Takes the menu offline so the website goes back to its built-in navigation. The draft is kept, so nothing the
     * administrators typed is lost.
     */
    @Transactional
    public MenuView resetToOriginal(MenuKey key, Long expectedVersion) {
        NavigationMenu menu = load(key);
        assertVersion(menu, expectedVersion);
        menu.setPublishedItems(JsonNodeFactory.instance.arrayNode());
        menu.setPublishedAt(null);
        menu.setPublishedBy(null);
        return view(menus.saveAndFlush(menu));
    }

    /**
     * Lists a page under a footer section. The draft always gets it; when the footer is already live it goes into the
     * live copy as well, so it appears as soon as the page itself is published.
     */
    @Transactional
    public void showPageInFooter(long pageId, String section) {
        CmsPage page = pages.findById(pageId).orElseThrow(() -> new ResourceNotFoundException("Page", "id", pageId));
        String sectionName = section.trim();
        NavigationMenu menu = load(MenuKey.FOOTER);
        menu.setDraftItems(withPageInSection(menu.getDraftItems(), page, sectionName));
        if (menu.getPublishedAt() != null) {
            menu.setPublishedItems(withPageInSection(menu.getPublishedItems(), page, sectionName));
        }
        menus.saveAndFlush(menu);
    }

    @Transactional
    public void hidePageFromFooter(long pageId) {
        NavigationMenu menu = load(MenuKey.FOOTER);
        menu.setDraftItems(withoutPage(menu.getDraftItems(), pageId));
        if (menu.getPublishedAt() != null) {
            menu.setPublishedItems(withoutPage(menu.getPublishedItems(), pageId));
        }
        menus.saveAndFlush(menu);
    }

    private static JsonNode withPageInSection(JsonNode items, CmsPage page, String sectionName) {
        ArrayNode top = (ArrayNode) withoutPage(items, page.getId());
        com.fasterxml.jackson.databind.node.ObjectNode group = null;
        for (JsonNode candidate : top) {
            if ("GROUP".equals(candidate.path("kind").asText())
                    && sectionName.equalsIgnoreCase(candidate.path("labels").path("en").asText())) {
                group = (com.fasterxml.jackson.databind.node.ObjectNode) candidate;
                break;
            }
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (group == null) {
            group = f.objectNode();
            group.put("id", java.util.UUID.randomUUID().toString());
            group.put("kind", "GROUP");
            group.set("labels", f.objectNode().put("en", sectionName));
            group.put("visible", true);
            group.put("openInNewTab", false);
            group.set("children", f.arrayNode());
            top.add(group);
        }
        com.fasterxml.jackson.databind.node.ObjectNode link = f.objectNode();
        link.put("id", java.util.UUID.randomUUID().toString());
        link.put("kind", "PAGE");
        link.put("pageId", page.getId().intValue());
        link.set("labels", f.objectNode().put("en", page.getTitle()));
        link.put("visible", true);
        link.put("openInNewTab", false);
        link.set("children", f.arrayNode());
        ((ArrayNode) group.get("children")).add(link);
        return top;
    }

    /** A copy of the tree without the page's link; sections left empty by that disappear too. */
    private static JsonNode withoutPage(JsonNode items, long pageId) {
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        if (items == null || !items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            if (item.path("pageId").isIntegralNumber() && item.path("pageId").longValue() == pageId) {
                continue;
            }
            com.fasterxml.jackson.databind.node.ObjectNode copy = item.deepCopy();
            boolean hadChildren = item.path("children").isArray() && item.path("children").size() > 0;
            copy.set("children", withoutPage(item.get("children"), pageId));
            if (hadChildren && "GROUP".equals(copy.path("kind").asText()) && copy.get("children").isEmpty()) {
                continue;
            }
            out.add(copy);
        }
        return out;
    }

    /** Keeps a removed item (with everything under it) in the trash so an administrator can restore it later. */
    @Transactional
    public MenuView trashItem(MenuKey key, JsonNode item, String title) {
        if (item == null || !item.isObject()) {
            throw new com.apsonic.backend.shared.exception.BadRequestException("A menu item is required");
        }
        String label = title == null || title.isBlank() ? item.path("labels").path("en").asText("Untitled item") : title.trim();
        jdbc.update("INSERT INTO cms_navigation_trash (menu_key, title, item, deleted_by) VALUES (?, ?, ?::jsonb, ?)",
                key.name(), label, item.toString(), SecurityUtils.getCurrentUsername().orElse("system"));
        // Take it out of the saved draft too, otherwise it would come back the next time the menu is opened.
        NavigationMenu menu = load(key);
        menu.setDraftItems(withoutId(menu.getDraftItems(), item.path("id").asText()));
        return view(menus.saveAndFlush(menu));
    }

    private static JsonNode withoutId(JsonNode items, String id) {
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        if (items == null || !items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            if (id.equals(item.path("id").asText())) {
                continue;
            }
            com.fasterxml.jackson.databind.node.ObjectNode copy = item.deepCopy();
            copy.set("children", withoutId(item.get("children"), id));
            out.add(copy);
        }
        return out;
    }

    /** Puts a trashed item back at the end of its menu's draft. Nothing goes live until the menu is published. */
    @Transactional
    public void restoreTrashedItem(long trashId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT menu_key, item::text AS item FROM cms_navigation_trash WHERE id = ?", trashId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Trashed menu item", "id", trashId);
        }
        NavigationMenu menu = load(MenuKey.valueOf((String) rows.get(0).get("menu_key")));
        JsonNode item;
        try {
            item = json.readTree((String) rows.get(0).get("item"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("The trashed menu item is unreadable", e);
        }
        ArrayNode draft = JsonNodeFactory.instance.arrayNode();
        menu.getDraftItems().forEach(draft::add);
        if (!containsId(draft, item.path("id").asText())) {
            draft.add(item);
            menu.setDraftItems(draft);
            menus.saveAndFlush(menu);
        }
        jdbc.update("DELETE FROM cms_navigation_trash WHERE id = ?", trashId);
    }

    private static boolean containsId(JsonNode items, String id) {
        if (items == null || !items.isArray()) {
            return false;
        }
        for (JsonNode item : items) {
            if (id.equals(item.path("id").asText()) || containsId(item.get("children"), id)) {
                return true;
            }
        }
        return false;
    }

    /** The draft as the site would render it after publishing; pages not yet published are flagged {@code pending}. */
    @Transactional(readOnly = true)
    public ResolvedMenu previewDraft(MenuKey key) {
        NavigationMenu menu = load(key);
        return new ResolvedMenu(key.name(), true, resolve(menu.getDraftItems(), true));
    }

    // --- public -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResolvedMenu published(MenuKey key) {
        NavigationMenu menu = load(key);
        boolean configured = menu.getPublishedAt() != null;
        return new ResolvedMenu(key.name(), configured, configured ? resolve(menu.getPublishedItems(), false) : List.of());
    }

    // --- page module hook -------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<String> referencesTo(long pageId) {
        List<String> places = new ArrayList<>();
        for (NavigationMenu menu : menus.findAll()) {
            String name = menu.getMenuKey().name().charAt(0) + menu.getMenuKey().name().substring(1).toLowerCase() + " menu";
            if (contains(menu.getDraftItems(), pageId)) {
                places.add(name + " (draft)");
            }
            if (contains(menu.getPublishedItems(), pageId)) {
                places.add(name);
            }
        }
        return places;
    }

    // ---------------------------------------------------------------------------------------------------------

    private NavigationMenu load(MenuKey key) {
        return menus.findByMenuKey(key).orElseThrow(() -> new ResourceNotFoundException("Menu", "key", key));
    }

    private void assertVersion(NavigationMenu menu, Long expected) {
        if (expected == null || !expected.equals(menu.getVersion())) {
            throw new ConflictException("This menu was changed by someone else. Reload it to see the latest version.");
        }
    }

    private void failIfInvalid(NavigationValidator.Result result, String message) {
        if (!result.valid()) {
            throw new ValidationFailedException(message, result.issues().stream()
                    .map(i -> new ValidationError(i.path(), i.message(), null)).toList());
        }
    }

    private MenuView view(NavigationMenu menu) {
        return new MenuView(menu.getMenuKey().name(), menu.getVersion(), menu.getDraftItems(), menu.getPublishedItems(),
                !menu.getDraftItems().equals(menu.getPublishedItems()), menu.getPublishedAt() != null,
                menu.getPublishedAt(), menu.getPublishedBy());
    }

    private Map<Long, CmsPage> pagesReferencedBy(JsonNode items) {
        Set<Long> ids = new HashSet<>();
        collectPageIds(items, ids);
        return ids.isEmpty() ? Map.of()
                : pages.findAllByIdIn(ids).stream().collect(Collectors.toMap(CmsPage::getId, p -> p));
    }

    private static void collectPageIds(JsonNode items, Set<Long> ids) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            if (item.path("pageId").isIntegralNumber()) {
                ids.add(item.path("pageId").longValue());
            }
            collectPageIds(item.get("children"), ids);
        }
    }

    private static boolean contains(JsonNode items, long pageId) {
        if (items == null || !items.isArray()) {
            return false;
        }
        for (JsonNode item : items) {
            if ((item.path("pageId").isIntegralNumber() && item.path("pageId").longValue() == pageId)
                    || contains(item.get("children"), pageId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns stored items into what the site needs. Hidden items disappear; a page item whose page is not published
     * disappears with its subtree (preview keeps it, flagged pending); a heading with nothing under it disappears.
     */
    private List<ResolvedItem> resolve(JsonNode items, boolean includePending) {
        Set<Long> ids = new HashSet<>();
        collectPageIds(items, ids);
        Map<Long, CmsPage> byId = ids.isEmpty() ? Map.of()
                : pages.findAllByIdIn(ids).stream().collect(Collectors.toMap(CmsPage::getId, p -> p));
        return resolveLevel(items, byId, includePending);
    }

    private List<ResolvedItem> resolveLevel(JsonNode items, Map<Long, CmsPage> byId, boolean includePending) {
        List<ResolvedItem> out = new ArrayList<>();
        if (items == null || !items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            if (!item.path("visible").asBoolean(true)) {
                continue;
            }
            String kind = item.path("kind").asText();
            String href = null;
            boolean pending = false;
            switch (kind) {
                case "PAGE" -> {
                    CmsPage page = byId.get(item.path("pageId").asLong());
                    if (page == null || page.getStatus() == PageStatus.ARCHIVED) {
                        continue;
                    }
                    if (page.getStatus() != PageStatus.PUBLISHED) {
                        if (!includePending) {
                            continue;
                        }
                        pending = true;
                    }
                    href = "/" + page.getSlug();
                }
                case "ROUTE" -> href = item.path("route").asText();
                case "EXTERNAL" -> href = item.path("url").asText();
                default -> {
                }
            }
            List<ResolvedItem> children = resolveLevel(item.get("children"), byId, includePending);
            if ("GROUP".equals(kind) && children.isEmpty()) {
                continue;
            }
            Map<String, String> labels = new LinkedHashMap<>();
            item.path("labels").fields().forEachRemaining(e -> labels.put(e.getKey(), e.getValue().asText()));
            out.add(new ResolvedItem(item.path("id").asText(), kind, labels, href, item.path("openInNewTab").asBoolean(false),
                    pending, children));
        }
        return out;
    }
}
