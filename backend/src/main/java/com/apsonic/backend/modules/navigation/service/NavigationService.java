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
import lombok.RequiredArgsConstructor;
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
