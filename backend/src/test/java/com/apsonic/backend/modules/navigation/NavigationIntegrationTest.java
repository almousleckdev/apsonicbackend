package com.apsonic.backend.modules.navigation;

import com.apsonic.backend.modules.navigation.domain.MenuKey;
import com.apsonic.backend.modules.navigation.repository.NavigationMenuRepository;
import com.apsonic.backend.modules.page.PageApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Navigation managed independently of pages: ordering, nesting, localisation, draft vs published, integrity. */
class NavigationIntegrationTest extends PageApiTestSupport {

    @Autowired
    private NavigationMenuRepository menus;
    @Autowired
    private TransactionTemplate tx;

    /** The menus are global state shared by every test in the run, so each test starts from an unconfigured, empty menu. */
    @BeforeEach
    void resetMenus() {
        tx.executeWithoutResult(status -> {
            for (MenuKey key : MenuKey.values()) {
                menus.findByMenuKey(key).ifPresent(menu -> {
                    menu.setDraftItems(mapper.createArrayNode());
                    menu.setPublishedItems(mapper.createArrayNode());
                    menu.setPublishedAt(null);
                    menus.save(menu);
                });
            }
        });
    }

    private ObjectNode route(String route, String en) {
        ObjectNode item = mapper.createObjectNode();
        item.put("kind", "ROUTE").put("route", route);
        item.putObject("labels").put("en", en);
        return item;
    }

    private ObjectNode pageItem(JsonNode page, String en) {
        ObjectNode item = mapper.createObjectNode();
        item.put("kind", "PAGE").put("pageId", page.path("id").asLong());
        item.putObject("labels").put("en", en);
        return item;
    }

    private ArrayNode items(JsonNode... nodes) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonNode n : nodes) {
            array.add(n);
        }
        return array;
    }

    private JsonNode menu(String key) throws Exception {
        return data(send("GET", "/api/v1/admin/navigation/" + key, admin(), null).andExpect(status().isOk()));
    }

    private JsonNode save(String key, JsonNode current, ArrayNode items) throws Exception {
        return data(send("PUT", "/api/v1/admin/navigation/" + key, admin(),
                Map.of("expectedVersion", current.path("version").asLong(), "items", items)).andExpect(status().isOk()));
    }

    private JsonNode publishMenu(String key, JsonNode current) throws Exception {
        return data(send("POST", "/api/v1/admin/navigation/" + key + "/publish", admin(),
                Map.of("expectedVersion", current.path("version").asLong())).andExpect(status().isOk()));
    }

    private JsonNode livePage(String title) throws Exception {
        return publish(saveDraft(admin(), createPage(slug()), document(title, hero(title))));
    }

    @Test
    void siteKeepsItsBuiltInNavigationUntilAMenuIsPublished() throws Exception {
        send("GET", "/api/v1/navigation/header", null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void administratorCanInsertAPageBetweenExistingItemsAndReorderItWithoutCode() throws Exception {
        JsonNode about = livePage("About Us");
        JsonNode current = menu("header");
        current = save("header", current, items(route("/products", "Products"), route("/services", "Services"), route("/contact", "Contact Us")));
        current = publishMenu("header", current);

        // Home → Products → About Us → Services → Contact Us
        current = save("header", current, items(route("/products", "Products"), pageItem(about, "About Us"), route("/services", "Services"), route("/contact", "Contact Us")));
        current = publishMenu("header", current);
        JsonNode live = data(send("GET", "/api/v1/navigation/header", null, null).andExpect(status().isOk()));
        assertThat(live.path("configured").asBoolean()).isTrue();
        assertThat(live.at("/items/0/labels/en").asText()).isEqualTo("Products");
        assertThat(live.at("/items/1/labels/en").asText()).isEqualTo("About Us");
        assertThat(live.at("/items/1/href").asText()).isEqualTo("/" + about.path("slug").asText());
        assertThat(live.at("/items/2/labels/en").asText()).isEqualTo("Services");

        // Move About Us to the front.
        ArrayNode reordered = items(current.at("/draft/1"), current.at("/draft/0"), current.at("/draft/2"), current.at("/draft/3"));
        publishMenu("header", save("header", current, reordered));
        JsonNode after = data(send("GET", "/api/v1/navigation/header", null, null));
        assertThat(after.at("/items/0/labels/en").asText()).isEqualTo("About Us");
        assertThat(after.at("/items/1/labels/en").asText()).isEqualTo("Products");
        // Item ids are stable across the move.
        assertThat(after.at("/items/0/id").asText()).isEqualTo(current.at("/draft/1/id").asText());
    }

    @Test
    void nestedDropdownsAreStoredAndServedWithTheirLabelsInAllLanguages() throws Exception {
        JsonNode history = livePage("History");
        JsonNode team = livePage("Team");
        ObjectNode group = mapper.createObjectNode();
        group.put("kind", "GROUP");
        group.putObject("labels").put("en", "Company").put("fr", "Entreprise").put("zh", "公司");
        ObjectNode historyItem = pageItem(history, "Our History");
        ((ObjectNode) historyItem.get("labels")).put("fr", "Notre histoire").put("zh", "我们的历史");
        group.putArray("children").add(historyItem).add(pageItem(team, "Team"));

        JsonNode current = save("header", menu("header"), items(group));
        publishMenu("header", current);
        JsonNode live = data(send("GET", "/api/v1/navigation/header", null, null));
        assertThat(live.at("/items/0/kind").asText()).isEqualTo("GROUP");
        assertThat(live.at("/items/0/href").isNull() || live.at("/items/0/href").isMissingNode()).isTrue();
        assertThat(live.at("/items/0/labels/fr").asText()).isEqualTo("Entreprise");
        assertThat(live.at("/items/0/labels/zh").asText()).isEqualTo("公司");
        assertThat(live.at("/items/0/children")).hasSize(2);
        assertThat(live.at("/items/0/children/0/labels/zh").asText()).isEqualTo("我们的历史");
        assertThat(live.at("/items/0/children/0/href").asText()).isEqualTo("/" + history.path("slug").asText());
    }

    @Test
    void footerAndHeaderAreIndependent() throws Exception {
        JsonNode footer = save("footer", menu("footer"), items(route("/contact", "Contact")));
        publishMenu("footer", footer);
        send("GET", "/api/v1/navigation/footer", null, null).andExpect(jsonPath("$.data.items[0].labels.en").value("Contact"));
        send("GET", "/api/v1/navigation/header", null, null).andExpect(jsonPath("$.data.configured").value(false));
        send("GET", "/api/v1/navigation/sidebar", null, null).andExpect(status().isNotFound());
    }

    @Test
    void hiddenItemsStayInTheMenuButAreNotServed() throws Exception {
        ObjectNode hidden = route("/news", "News");
        hidden.put("visible", false);
        JsonNode current = save("header", menu("header"), items(route("/products", "Products"), hidden));
        current = publishMenu("header", current);
        assertThat(current.at("/published")).hasSize(2);
        JsonNode live = data(send("GET", "/api/v1/navigation/header", null, null));
        assertThat(live.path("items")).hasSize(1);
        // Un-hiding does not need the page or the item to be recreated.
        ObjectNode shown = (ObjectNode) current.at("/draft/1").deepCopy();
        shown.put("visible", true);
        publishMenu("header", save("header", current, items(current.at("/draft/0"), shown)));
        assertThat(data(send("GET", "/api/v1/navigation/header", null, null)).path("items")).hasSize(2);
    }

    @Test
    void draftChangesAreNotLiveUntilPublishedCanBePreviewedAndCanBeDiscarded() throws Exception {
        JsonNode current = publishMenu("header", save("header", menu("header"), items(route("/products", "Products"))));
        JsonNode changed = save("header", current, items(route("/products", "Products"), route("/news", "Latest news")));
        assertThat(changed.path("hasUnpublishedChanges").asBoolean()).isTrue();

        assertThat(data(send("GET", "/api/v1/navigation/header", null, null)).path("items")).hasSize(1);
        JsonNode preview = data(send("GET", "/api/v1/admin/navigation/header/preview", admin(), null).andExpect(status().isOk()));
        assertThat(preview.path("items")).hasSize(2);
        assertThat(preview.at("/items/1/labels/en").asText()).isEqualTo("Latest news");

        JsonNode discarded = data(send("POST", "/api/v1/admin/navigation/header/discard", admin(),
                Map.of("expectedVersion", changed.path("version").asLong())).andExpect(status().isOk()));
        assertThat(discarded.path("hasUnpublishedChanges").asBoolean()).isFalse();
        assertThat(discarded.path("draft")).hasSize(1);
    }

    @Test
    void unpublishedAndArchivedPagesDisappearFromThePublicMenuWithoutBreakingIt() throws Exception {
        JsonNode keep = livePage("Keep");
        JsonNode drop = livePage("Drop");
        publishMenu("header", save("header", menu("header"), items(pageItem(keep, "Keep"), pageItem(drop, "Drop"))));
        assertThat(data(send("GET", "/api/v1/navigation/header", null, null)).path("items")).hasSize(2);

        versioned("unpublish", admin(), getAdmin(drop.path("id").asLong())).andExpect(status().isOk());
        JsonNode live = data(send("GET", "/api/v1/navigation/header", null, null));
        assertThat(live.path("items")).hasSize(1);
        assertThat(live.at("/items/0/labels/en").asText()).isEqualTo("Keep");
    }

    @Test
    void menusReferToPagesByIdSoRenamingAPageAddressKeepsTheLink() throws Exception {
        JsonNode page = livePage("Movable");
        publishMenu("header", save("header", menu("header"), items(pageItem(page, "Movable"))));
        String newSlug = slug();
        trySave(admin(), page.path("id").asLong(), page.path("version").asLong(), newSlug, page.path("document")).andExpect(status().isOk());
        assertThat(data(send("GET", "/api/v1/navigation/header", null, null)).at("/items/0/href").asText()).isEqualTo("/" + newSlug);
    }

    @Test
    void invalidMenusAreRejected() throws Exception {
        JsonNode current = menu("header");
        JsonNode page = livePage("Once");
        String[] bad = {
                // duplicate page
                "[" + pageItem(page, "A") + "," + pageItem(page, "B") + "]",
                // duplicate route
                "[" + route("/news", "A") + "," + route("/news", "B") + "]",
                // unsafe external link
                "[{\"kind\":\"EXTERNAL\",\"url\":\"javascript:alert(1)\",\"labels\":{\"en\":\"x\"}}]",
                // unknown route
                "[" + route("/admin", "Admin") + "]",
                // no English label
                "[{\"kind\":\"ROUTE\",\"route\":\"/news\",\"labels\":{\"fr\":\"Actualites\"}}]",
                // dangling page
                "[{\"kind\":\"PAGE\",\"pageId\":99999999,\"labels\":{\"en\":\"Ghost\"}}]",
                // four levels in the header
                "[{\"kind\":\"GROUP\",\"labels\":{\"en\":\"1\"},\"children\":[{\"kind\":\"GROUP\",\"labels\":{\"en\":\"2\"},\"children\":[{\"kind\":\"GROUP\",\"labels\":{\"en\":\"3\"},\"children\":[" + route("/news", "4") + "]}]}]}]"};
        for (String body : bad) {
            send("PUT", "/api/v1/admin/navigation/header", admin(),
                    Map.of("expectedVersion", current.path("version").asLong(), "items", mapper.readTree(body))).andExpect(status().isBadRequest());
        }
        // The footer allows only two levels.
        String threeLevels = "[{\"kind\":\"GROUP\",\"labels\":{\"en\":\"1\"},\"children\":[{\"kind\":\"GROUP\",\"labels\":{\"en\":\"2\"},\"children\":[" + route("/news", "3") + "]}]}]";
        send("PUT", "/api/v1/admin/navigation/footer", admin(),
                Map.of("expectedVersion", menu("footer").path("version").asLong(), "items", mapper.readTree(threeLevels))).andExpect(status().isBadRequest());
        // Nothing was stored.
        assertThat(menu("header").path("draft")).isEmpty();
    }

    @Test
    void aMenuCannotBePublishedWhileItPointsAtAnUnpublishedPage() throws Exception {
        JsonNode draftOnly = createPage(slug());
        JsonNode current = save("header", menu("header"), items(pageItem(draftOnly, "Soon")));
        send("POST", "/api/v1/admin/navigation/header/publish", admin(), Map.of("expectedVersion", current.path("version").asLong()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].message").value(containsString("is not published yet")));
        publish(draftOnly);
        publishMenu("header", current);
    }

    @Test
    void aPageInTheNavigationCannotBeArchivedOrDeletedUntilRemovedFromIt() throws Exception {
        JsonNode draftPage = createPage(slug());
        JsonNode current = save("header", menu("header"), items(pageItem(draftPage, "Draft page")));
        versioned("archive", admin(), draftPage).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("navigation")));
        send("DELETE", "/api/v1/admin/pages/" + draftPage.path("id").asLong(), admin(), null).andExpect(status().isConflict());

        save("header", current, items());
        send("DELETE", "/api/v1/admin/pages/" + draftPage.path("id").asLong(), admin(), null).andExpect(status().isOk());
    }

    @Test
    void staleMenuVersionsAreRejected() throws Exception {
        JsonNode original = menu("header");
        save("header", original, items(route("/news", "News")));
        send("PUT", "/api/v1/admin/navigation/header", admin(), Map.of("expectedVersion", original.path("version").asLong(), "items", items(route("/contact", "Contact"))))
                .andExpect(status().isConflict());
        send("POST", "/api/v1/admin/navigation/header/publish", admin(), Map.of("expectedVersion", original.path("version").asLong())).andExpect(status().isConflict());
    }

    @Test
    void onlyUsersWithNavigationPermissionCanEditMenusButAnyoneCanReadTheLiveOne() throws Exception {
        String ed = editor();
        JsonNode current = menu("header");
        send("GET", "/api/v1/admin/navigation/header", ed, null).andExpect(status().isForbidden());
        send("PUT", "/api/v1/admin/navigation/header", ed, Map.of("expectedVersion", current.path("version").asLong(), "items", items())).andExpect(status().isForbidden());
        send("POST", "/api/v1/admin/navigation/header/publish", ed, Map.of("expectedVersion", current.path("version").asLong())).andExpect(status().isForbidden());
        send("GET", "/api/v1/admin/navigation/page-options", ed, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/admin/navigation/header", null, null).andExpect(status().isUnauthorized());
        send("PUT", "/api/v1/admin/navigation/header", null, Map.of("expectedVersion", 0, "items", items())).andExpect(status().isUnauthorized());
        send("GET", "/api/v1/navigation/header", null, null).andExpect(status().isOk());
    }

    @Test
    void pageOptionsListEveryUsablePageWithItsStatus() throws Exception {
        JsonNode draftOnly = createPage(slug());
        JsonNode live = livePage("Options");
        String body = send("GET", "/api/v1/admin/navigation/page-options", admin(), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(draftOnly.path("slug").asText()).contains(live.path("slug").asText());
    }
}
