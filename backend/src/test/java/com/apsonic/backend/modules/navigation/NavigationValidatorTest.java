package com.apsonic.backend.modules.navigation;

import com.apsonic.backend.modules.navigation.domain.MenuKey;
import com.apsonic.backend.modules.navigation.service.NavigationValidator;
import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NavigationValidator validator = new NavigationValidator();

    private JsonNode json(String json) throws Exception {
        return mapper.readTree(json.replace('\'', '"'));
    }

    private CmsPage page(long id, PageStatus status) {
        CmsPage page = new CmsPage();
        page.setId(id);
        page.setSlug("page-" + id);
        page.setTitle("Page " + id);
        page.setStatus(status);
        return page;
    }

    private String item(String body) {
        return "{'kind':'ROUTE','route':'/services','labels':{'en':'Services'}" + (body.isEmpty() ? "" : "," + body) + "}";
    }

    private boolean complains(NavigationValidator.Result result, String part) {
        return result.issues().stream().anyMatch(i -> i.message().toLowerCase().contains(part.toLowerCase()) || i.path().contains(part));
    }

    @Test
    void acceptsANestedMenuWithLocalisedLabels() throws Exception {
        Map<Long, CmsPage> pages = Map.of(1L, page(1, PageStatus.PUBLISHED), 2L, page(2, PageStatus.PUBLISHED));
        JsonNode menu = json("[{'kind':'ROUTE','route':'/products','labels':{'en':'Products','fr':'Produits','zh':'产品'}},"
                + "{'kind':'PAGE','pageId':1,'labels':{'en':'About us','fr':'A propos'},'children':["
                + "{'kind':'PAGE','pageId':2,'labels':{'en':'History'}},{'kind':'EXTERNAL','url':'https://example.com','labels':{'en':'Partner'}}]}]");
        NavigationValidator.Result result = validator.validate(MenuKey.HEADER, menu, pages, true);
        assertThat(result.issues()).isEmpty();
        assertThat(result.items().at("/0/labels/zh").asText()).isEqualTo("产品");
        assertThat(result.items().at("/1/children")).hasSize(2);
        assertThat(result.items().at("/1/id").asText()).hasSize(36);
    }

    @Test
    void rejectsDuplicatePagesRoutesAndLinks() throws Exception {
        Map<Long, CmsPage> pages = Map.of(1L, page(1, PageStatus.PUBLISHED));
        assertThat(complains(validator.validate(MenuKey.HEADER, json("[{'kind':'PAGE','pageId':1,'labels':{'en':'A'}},{'kind':'PAGE','pageId':1,'labels':{'en':'B'}}]"), pages, false), "already in the menu")).isTrue();
        assertThat(complains(validator.validate(MenuKey.HEADER, json("[" + item("") + "," + item("") + "]"), pages, false), "already in the menu")).isTrue();
        assertThat(complains(validator.validate(MenuKey.HEADER, json("[{'kind':'EXTERNAL','url':'https://a.example','labels':{'en':'A'}},{'kind':'EXTERNAL','url':'HTTPS://A.EXAMPLE','labels':{'en':'B'}}]"), pages, false), "already in the menu")).isTrue();
    }

    @Test
    void rejectsUnsafeOrNonAbsoluteExternalLinks() throws Exception {
        for (String url : new String[]{"javascript:alert(1)", "/relative", "//evil.example", "data:text/html,x", "ftp://x.example", "#top"}) {
            NavigationValidator.Result result = validator.validate(MenuKey.HEADER,
                    json("[{'kind':'EXTERNAL','url':'" + url + "','labels':{'en':'X'}}]"), Map.of(), false);
            assertThat(result.valid()).as(url).isFalse();
        }
    }

    @Test
    void rejectsUnknownRoutesKindsAndFields() throws Exception {
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'ROUTE','route':'/admin','labels':{'en':'X'}}]"), Map.of(), false).valid()).isFalse();
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'SCRIPT','labels':{'en':'X'}}]"), Map.of(), false).valid()).isFalse();
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'GROUP','labels':{'en':'X'},'href':'x'}]"), Map.of(), false).valid()).isFalse();
    }

    @Test
    void enforcesDepthPerMenu() throws Exception {
        String threeLevels = "[{'kind':'GROUP','labels':{'en':'1'},'children':[{'kind':'GROUP','labels':{'en':'2'},'children':[{'kind':'ROUTE','route':'/news','labels':{'en':'3'}}]}]}]";
        assertThat(validator.validate(MenuKey.HEADER, json(threeLevels), Map.of(), false).valid()).isTrue();
        assertThat(complains(validator.validate(MenuKey.FOOTER, json(threeLevels), Map.of(), false), "2 levels")).isTrue();
        String fourLevels = "[{'kind':'GROUP','labels':{'en':'1'},'children':[{'kind':'GROUP','labels':{'en':'2'},'children':[{'kind':'GROUP','labels':{'en':'3'},'children':[{'kind':'ROUTE','route':'/news','labels':{'en':'4'}}]}]}]}]";
        assertThat(complains(validator.validate(MenuKey.HEADER, json(fourLevels), Map.of(), false), "3 levels")).isTrue();
    }

    @Test
    void externalLinksAndTheProductsMegaMenuCannotHaveDropdowns() throws Exception {
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'EXTERNAL','url':'https://a.example','labels':{'en':'A'},'children':[" + item("") + "]}]"), Map.of(), false).valid()).isFalse();
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'ROUTE','route':'/products','labels':{'en':'P'},'children':[" + item("") + "]}]"), Map.of(), false).valid()).isFalse();
    }

    @Test
    void requiresAnEnglishLabelAndPlainText() throws Exception {
        assertThat(complains(validator.validate(MenuKey.HEADER, json("[{'kind':'ROUTE','route':'/news','labels':{'fr':'Actualites'}}]"), Map.of(), false), "English")).isTrue();
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'ROUTE','route':'/news','labels':{'en':'<b>News</b>'}}]"), Map.of(), false).valid()).isFalse();
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'ROUTE','route':'/news','labels':{'en':'" + "x".repeat(80) + "'}}]"), Map.of(), false).valid()).isFalse();
        assertThat(validator.validate(MenuKey.HEADER, json("[{'kind':'ROUTE','route':'/news','labels':{'de':'Nachrichten','en':'News'}}]"), Map.of(), false).valid()).isFalse();
    }

    @Test
    void pageReferencesMustExistAndPublishNeedsPublishedPages() throws Exception {
        JsonNode menu = json("[{'kind':'PAGE','pageId':9,'labels':{'en':'Gone'}}]");
        assertThat(complains(validator.validate(MenuKey.HEADER, menu, Map.of(), false), "no longer exists")).isTrue();
        Map<Long, CmsPage> draft = Map.of(9L, page(9, PageStatus.DRAFT));
        assertThat(validator.validate(MenuKey.HEADER, menu, draft, false).valid()).isTrue();
        assertThat(complains(validator.validate(MenuKey.HEADER, menu, draft, true), "Publish the page")).isTrue();
        assertThat(validator.validate(MenuKey.HEADER, menu, Map.of(9L, page(9, PageStatus.ARCHIVED)), false).valid()).isFalse();
    }

    @Test
    void dropdownHeadingsNeedItemsToPublish() throws Exception {
        JsonNode menu = json("[{'kind':'GROUP','labels':{'en':'Company'}}]");
        assertThat(validator.validate(MenuKey.HEADER, menu, Map.of(), false).valid()).isTrue();
        assertThat(validator.validate(MenuKey.HEADER, menu, Map.of(), true).valid()).isFalse();
    }

    @Test
    void enforcesSizeLimitsAndUniqueIds() throws Exception {
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 13; i++) {
            many.append(i == 0 ? "" : ",").append("{'kind':'GROUP','labels':{'en':'g" + i + "'}}");
        }
        assertThat(validator.validate(MenuKey.HEADER, json(many + "]"), Map.of(), false).valid()).isFalse();
        String id = "123e4567-e89b-42d3-a456-426614174000";
        assertThat(complains(validator.validate(MenuKey.HEADER, json("[{'id':'" + id + "','kind':'GROUP','labels':{'en':'a'}},{'id':'" + id + "','kind':'GROUP','labels':{'en':'b'}}]"), Map.of(), false), "same id")).isTrue();
    }

    @Test
    void hiddenFlagIsKeptAndDefaultsToVisible() throws Exception {
        NavigationValidator.Result result = validator.validate(MenuKey.HEADER,
                json("[{'kind':'ROUTE','route':'/news','labels':{'en':'News'},'visible':false},{'kind':'ROUTE','route':'/contact','labels':{'en':'Contact'}}]"), Map.of(), false);
        assertThat(result.items().at("/0/visible").asBoolean()).isFalse();
        assertThat(result.items().at("/1/visible").asBoolean()).isTrue();
    }
}
