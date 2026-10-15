package com.apsonic.backend.modules.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The page lifecycle end to end through the HTTP API: drafts, sections, publishing, URLs, history and security. */
class PageBuilderIntegrationTest extends PageApiTestSupport {

    // --- creating, editing, duplicating, deleting --------------------------------------------------------------

    @Test
    void newPageIsAPrivateDraft() throws Exception {
        String slug = slug();
        JsonNode page = createPage(slug);
        assertThat(page.path("status").asText()).isEqualTo("DRAFT");
        assertThat(page.path("hasUnpublishedChanges").asBoolean()).isFalse();
        assertThat(page.path("document").path("schemaVersion").asInt()).isEqualTo(1);
        publicPage(slug).andExpect(status().isNotFound());
    }

    @Test
    void savingADraftNeverPublishes() throws Exception {
        String slug = slug();
        JsonNode page = createPage(slug);
        JsonNode saved = saveDraft(admin(), page, document("About", hero("Welcome")));
        assertThat(saved.path("status").asText()).isEqualTo("DRAFT");
        assertThat(saved.path("version").asLong()).isGreaterThan(page.path("version").asLong());
        publicPage(slug).andExpect(status().isNotFound());
    }

    @Test
    void savingAnUnchangedDraftDoesNotBumpTheVersion() throws Exception {
        JsonNode page = createPage(slug());
        JsonNode first = saveDraft(admin(), page, document("Same", hero("A")));
        JsonNode second = saveDraft(admin(), first, first.path("document"));
        assertThat(second.path("version").asLong()).isEqualTo(first.path("version").asLong());
    }

    @Test
    void sectionsCanBeAddedConfiguredReorderedAndRemoved() throws Exception {
        JsonNode page = createPage(slug());
        JsonNode saved = saveDraft(admin(), page, document("Builder", hero("First"), richText("<p>Second</p>"), spacer()));
        ArrayNode sections = (ArrayNode) saved.at("/document/sections");
        assertThat(sections).hasSize(3);
        assertThat(sections.get(0).path("id").asText()).hasSize(36);

        // Reorder: move the last section to the top, keeping the server-assigned ids.
        ArrayNode reordered = mapper.createArrayNode().add(sections.get(2)).add(sections.get(0)).add(sections.get(1));
        JsonNode doc = saved.path("document").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) doc).set("sections", reordered);
        JsonNode afterReorder = saveDraft(admin(), saved, doc);
        assertThat(afterReorder.at("/document/sections/0/type").asText()).isEqualTo("spacer");
        assertThat(afterReorder.at("/document/sections/0/id").asText()).isEqualTo(sections.get(2).path("id").asText());
        assertThat(getAdmin(page.path("id").asLong()).at("/document/sections/1/type").asText()).isEqualTo("hero");

        // Configure then remove.
        com.fasterxml.jackson.databind.node.ObjectNode edited = afterReorder.path("document").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.at("/sections/1/content")).putObject("heading").put("en", "Renamed");
        JsonNode afterEdit = saveDraft(admin(), afterReorder, edited);
        assertThat(afterEdit.at("/document/sections/1/content/heading/en").asText()).isEqualTo("Renamed");

        com.fasterxml.jackson.databind.node.ObjectNode removed = afterEdit.path("document").deepCopy();
        ((ArrayNode) removed.get("sections")).remove(0);
        assertThat(saveDraft(admin(), afterEdit, removed).at("/document/sections")).hasSize(2);
    }

    @Test
    void duplicatingCreatesAnIndependentDraftWithNewSectionIds() throws Exception {
        String slug = slug();
        JsonNode original = saveDraft(admin(), createPage(slug), document("Original", hero("Hello")));
        JsonNode copy = data(send("POST", "/api/v1/admin/pages/" + original.path("id").asLong() + "/duplicate", admin(), null)
                .andExpect(status().isCreated()));
        assertThat(copy.path("slug").asText()).isEqualTo(slug + "-copy");
        assertThat(copy.path("status").asText()).isEqualTo("DRAFT");
        assertThat(copy.path("title").asText()).isEqualTo("Original (copy)");
        assertThat(copy.at("/document/sections/0/id").asText()).isNotEqualTo(original.at("/document/sections/0/id").asText());
        assertThat(copy.at("/document/sections/0/content/heading/en").asText()).isEqualTo("Hello");
        // A second duplicate gets the next free address.
        JsonNode second = data(send("POST", "/api/v1/admin/pages/" + original.path("id").asLong() + "/duplicate", admin(), null)
                .andExpect(status().isCreated()));
        assertThat(second.path("slug").asText()).isEqualTo(slug + "-copy-2");
    }

    @Test
    void draftsCanBeDeletedButTheirAddressStaysReserved() throws Exception {
        String slug = slug();
        JsonNode page = createPage(slug);
        send("DELETE", "/api/v1/admin/pages/" + page.path("id").asLong(), admin(), null).andExpect(status().isOk());
        send("GET", "/api/v1/admin/pages/" + page.path("id").asLong(), admin(), null).andExpect(status().isNotFound());
        send("POST", "/api/v1/admin/pages", admin(), Map.of("slug", slug, "title", "Again")).andExpect(status().isConflict());
    }

    @Test
    void aLivePageMustBeUnpublishedBeforeItCanBeDeleted() throws Exception {
        JsonNode page = createPage(slug());
        JsonNode live = publish(saveDraft(admin(), page, document("Live", hero("x"))));
        send("DELETE", "/api/v1/admin/pages/" + live.path("id").asLong(), admin(), null).andExpect(status().isConflict());
    }

    @Test
    void unknownPagesAreNotFoundNotServerErrors() throws Exception {
        send("GET", "/api/v1/admin/pages/99999999", admin(), null).andExpect(status().isNotFound());
        send("DELETE", "/api/v1/admin/pages/99999999", admin(), null).andExpect(status().isNotFound());
        publicPage("does-not-exist").andExpect(status().isNotFound());
    }

    // --- publishing --------------------------------------------------------------------------------------------

    @Test
    void publishingGoesLiveAndLaterDraftEditsStayPrivateUntilRepublished() throws Exception {
        String slug = slug();
        JsonNode draft = saveDraft(admin(), createPage(slug), document("Story", hero("Version one")));
        JsonNode live = publish(draft);
        assertThat(live.path("status").asText()).isEqualTo("PUBLISHED");
        publicPage(slug).andExpect(status().isOk()).andExpect(jsonPath("$.data.document.sections[0].content.heading.en").value("Version one"));

        JsonNode edited = saveDraft(admin(), live, document("Story", hero("Version two")));
        assertThat(edited.path("hasUnpublishedChanges").asBoolean()).isTrue();
        publicPage(slug).andExpect(status().isOk()).andExpect(jsonPath("$.data.document.sections[0].content.heading.en").value("Version one"))
                .andExpect(content().string(not(containsString("Version two"))));

        publish(edited);
        publicPage(slug).andExpect(jsonPath("$.data.document.sections[0].content.heading.en").value("Version two"));
    }

    @Test
    void unpublishingRemovesThePageFromThePublicSiteButKeepsTheDraft() throws Exception {
        String slug = slug();
        JsonNode live = publish(saveDraft(admin(), createPage(slug), document("Temp", hero("x"))));
        JsonNode down = data(versioned("unpublish", admin(), live).andExpect(status().isOk()));
        assertThat(down.path("status").asText()).isEqualTo("DRAFT");
        publicPage(slug).andExpect(status().isNotFound());
        assertThat(getAdmin(live.path("id").asLong()).at("/document/sections/0/content/heading/en").asText()).isEqualTo("x");
        // ...and it can go live again.
        publish(down);
        publicPage(slug).andExpect(status().isOk());
    }

    @Test
    void publishingNeedsTheRequiredContentButDraftsDoNot() throws Exception {
        JsonNode page = saveDraft(admin(), createPage(slug()), document("Incomplete", mapper.readTree("{\"type\":\"hero\",\"content\":{}}")));
        versioned("publish", admin(), page).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(containsString("heading")));
        assertThat(getAdmin(page.path("id").asLong()).path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void validationReportListsBlockersAndLanguageCompleteness() throws Exception {
        String hero = "{\"type\":\"hero\",\"content\":{\"heading\":{\"en\":\"Hello\",\"fr\":\"Bonjour\"}}}";
        JsonNode page = saveDraft(admin(), createPage(slug()), document("Report", mapper.readTree(hero)));
        JsonNode report = data(send("POST", "/api/v1/admin/pages/" + page.path("id").asLong() + "/validate", admin(), null).andExpect(status().isOk()));
        assertThat(report.path("publishable").asBoolean()).isTrue();
        assertThat(report.at("/completeness/fr/filled").asInt()).isEqualTo(1);
        assertThat(report.at("/completeness/zh/filled").asInt()).isEqualTo(0);
        assertThat(report.at("/completeness/en/total").asInt()).isEqualTo(1);
    }

    @Test
    void publicApiServesOnlyPublishedContentWithoutInternalFields() throws Exception {
        String slug = slug();
        JsonNode live = publish(saveDraft(admin(), createPage(slug), document("Public", hero("Public heading"))));
        saveDraft(admin(), live, document("Public", hero("SECRET DRAFT TEXT")));
        publicPage(slug).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("SECRET DRAFT TEXT"))))
                .andExpect(content().string(not(containsString("draftDocument"))))
                .andExpect(content().string(not(containsString("createdBy"))))
                .andExpect(content().string(not(containsString("expectedVersion"))));
    }

    @Test
    void archivedPagesAreHiddenAndReadOnlyUntilRestored() throws Exception {
        String slug = slug();
        JsonNode live = publish(saveDraft(admin(), createPage(slug), document("Old", hero("x"))));
        JsonNode archived = data(versioned("archive", admin(), live).andExpect(status().isOk()));
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        publicPage(slug).andExpect(status().isNotFound());
        trySave(admin(), archived.path("id").asLong(), archived.path("version").asLong(), null, document("Old", hero("y"))).andExpect(status().isConflict());
        versioned("publish", admin(), archived).andExpect(status().isConflict());

        String listing = send("GET", "/api/v1/admin/pages?q=" + slug, admin(), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listing).doesNotContain(slug);
        String archivedListing = send("GET", "/api/v1/admin/pages?status=ARCHIVED&q=" + slug, admin(), null).andReturn().getResponse().getContentAsString();
        assertThat(archivedListing).contains(slug);

        JsonNode restored = data(versioned("restore", admin(), archived).andExpect(status().isOk()));
        assertThat(restored.path("status").asText()).isEqualTo("DRAFT");
        publicPage(slug).andExpect(status().isNotFound());
    }

    // --- addresses (slugs) -------------------------------------------------------------------------------------

    @Test
    void addressRulesAreEnforced() throws Exception {
        for (String bad : new String[]{"Has Space", "UPPER", "under_score", "-lead", "trail-", "double--dash", "../etc", "a/b", "x".repeat(81)}) {
            send("POST", "/api/v1/admin/pages", admin(), Map.of("slug", bad, "title", "T")).andExpect(status().isBadRequest());
        }
        for (String reserved : new String[]{"admin", "api", "products", "contact", "preview", "uploads", "en"}) {
            send("POST", "/api/v1/admin/pages", admin(), Map.of("slug", reserved, "title", "T")).andExpect(status().isBadRequest());
        }
        String slug = slug();
        createPage(slug);
        send("POST", "/api/v1/admin/pages", admin(), Map.of("slug", slug, "title", "Again")).andExpect(status().isConflict());
        send("GET", "/api/v1/admin/pages/slug-available?slug=" + slug, admin(), null).andExpect(jsonPath("$.data.available").value(false));
        send("GET", "/api/v1/admin/pages/slug-available?slug=" + slug() , admin(), null).andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void renamingALivePageKeepsTheOldAddressWorkingAsARedirect() throws Exception {
        String oldSlug = slug();
        String newSlug = slug();
        JsonNode live = publish(saveDraft(admin(), createPage(oldSlug), document("Moved", hero("x"))));
        JsonNode renamed = data(trySave(admin(), live.path("id").asLong(), live.path("version").asLong(), newSlug, live.path("document")).andExpect(status().isOk()));
        assertThat(renamed.path("slug").asText()).isEqualTo(newSlug);

        publicPage(newSlug).andExpect(status().isOk()).andExpect(jsonPath("$.data.slug").value(newSlug));
        publicPage(oldSlug).andExpect(status().isOk()).andExpect(jsonPath("$.data.redirectSlug").value(newSlug))
                .andExpect(jsonPath("$.data.document").doesNotExist());
        // The old address stays reserved: nobody else can take it and silently hijack inbound links.
        send("POST", "/api/v1/admin/pages", admin(), Map.of("slug", oldSlug, "title", "Squatter")).andExpect(status().isConflict());
    }

    @Test
    void renamingToAnAddressInUseIsAConflict() throws Exception {
        JsonNode a = createPage(slug());
        JsonNode b = createPage(slug());
        trySave(admin(), a.path("id").asLong(), a.path("version").asLong(), b.path("slug").asText(), a.path("document")).andExpect(status().isConflict());
    }

    // --- concurrency -------------------------------------------------------------------------------------------

    @Test
    void aStaleVersionIsRejectedInsteadOfOverwritingSomeoneElsesWork() throws Exception {
        JsonNode page = createPage(slug());
        JsonNode mine = saveDraft(admin(), page, document("Shared", hero("Editor A")));
        // Editor B still holds the original version.
        trySave(admin(), page.path("id").asLong(), page.path("version").asLong(), null, document("Shared", hero("Editor B"))).andExpect(status().isConflict());
        assertThat(getAdmin(page.path("id").asLong()).at("/document/sections/0/content/heading/en").asText()).isEqualTo("Editor A");
        // Publishing a stale version is refused too: you publish exactly what you reviewed.
        versioned("publish", admin(), page).andExpect(status().isConflict());
        publish(mine);
    }

    @Test
    void simultaneousSavesOfTheSameVersionLetExactlyOneWin() throws Exception {
        JsonNode page = createPage(slug());
        long id = page.path("id").asLong();
        long version = page.path("version").asLong();
        String token = admin();
        int writers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            String heading = "Writer " + i;
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return trySave(token, id, version, null, document("Race", hero(heading))).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        int ok = 0;
        int conflicts = 0;
        for (Future<Integer> f : results) {
            int s = f.get();
            if (s == 200) ok++;
            else if (s == 409) conflicts++;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(conflicts).isEqualTo(writers - 1);
    }

    // --- content safety ----------------------------------------------------------------------------------------

    @Test
    void unsafeLinksMediaAndUnknownContentAreRejectedOnSave() throws Exception {
        JsonNode page = createPage(slug());
        String[] badSections = {
                "{\"type\":\"hero\",\"content\":{\"ctas\":[{\"label\":{\"en\":\"Go\"},\"url\":\"javascript:alert(1)\"}]}}",
                "{\"type\":\"hero\",\"content\":{\"desktopImage\":\"data:image/svg+xml;base64,PHN2Zz4=\"}}",
                "{\"type\":\"cta-banner\",\"content\":{\"buttonUrl\":\"//evil.example/phish\"}}",
                "{\"type\":\"hero\",\"content\":{\"heading\":{\"en\":\"<script>alert(1)</script>\"}}}",
                "{\"type\":\"custom-react\",\"content\":{}}",
                "{\"type\":\"hero\",\"content\":{\"dangerouslySetInnerHTML\":\"<b>x</b>\"}}"};
        for (String section : badSections) {
            trySave(admin(), page.path("id").asLong(), page.path("version").asLong(), null, document("Bad", mapper.readTree(section)))
                    .andExpect(status().isBadRequest());
        }
        JsonNode unchanged = getAdmin(page.path("id").asLong());
        assertThat(unchanged.path("version").asLong()).isEqualTo(page.path("version").asLong());
    }

    @Test
    void richTextIsSanitisedWhenSavedAndWhenServed() throws Exception {
        String slug = slug();
        String attack = "<p onclick=\"steal()\">Hello</p><script>alert(1)</script><img src=x onerror=alert(1)>"
                + "<a href=\"javascript:alert(1)\">click</a><iframe src=\"https://evil.example\"></iframe>";
        JsonNode saved = saveDraft(admin(), createPage(slug), document("XSS", richText(attack)));
        String stored = saved.at("/document/sections/0/content/body/en").asText();
        assertThat(stored).contains("Hello").doesNotContain("<script").doesNotContain("onclick").doesNotContain("onerror")
                .doesNotContain("javascript:").doesNotContain("evil.example");
        publish(saved);
        String served = publicPage(slug).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(served).contains("Hello").doesNotContain("<script").doesNotContain("onerror").doesNotContain("javascript:");
    }

    // --- authorization -----------------------------------------------------------------------------------------

    @Test
    void adminEndpointsRequireSignIn() throws Exception {
        send("GET", "/api/v1/admin/pages", null, null).andExpect(status().isUnauthorized());
        send("POST", "/api/v1/admin/pages", null, Map.of("slug", slug(), "title", "T")).andExpect(status().isUnauthorized());
        send("POST", "/api/v1/admin/pages/1/publish", null, Map.of("expectedVersion", 0)).andExpect(status().isUnauthorized());
        send("POST", "/api/v1/admin/pages/1/preview-token", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void editorsCanDraftButCannotPublishUnpublishArchiveDeleteOrRestore() throws Exception {
        String ed = editor();
        JsonNode page = createPage(ed, slug(), document("By editor", hero("Draft")));
        JsonNode saved = saveDraft(ed, page, document("By editor", hero("Edited")));
        send("POST", "/api/v1/admin/pages/" + saved.path("id").asLong() + "/duplicate", ed, null).andExpect(status().isCreated());
        send("GET", "/api/v1/admin/pages", ed, null).andExpect(status().isOk());

        versioned("publish", ed, saved).andExpect(status().isForbidden());
        versioned("unpublish", ed, saved).andExpect(status().isForbidden());
        versioned("archive", ed, saved).andExpect(status().isForbidden());
        versioned("restore", ed, saved).andExpect(status().isForbidden());
        send("DELETE", "/api/v1/admin/pages/" + saved.path("id").asLong(), ed, null).andExpect(status().isForbidden());
        send("POST", "/api/v1/admin/pages/" + saved.path("id").asLong() + "/revisions/1/rollback", ed, Map.of("expectedVersion", saved.path("version").asLong()))
                .andExpect(status().isForbidden());
        // Nothing changed.
        assertThat(getAdmin(saved.path("id").asLong()).path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void editorsCannotChangeTheAddressOfAPageThatHasBeenLive() throws Exception {
        String ed = editor();
        JsonNode live = publish(saveDraft(admin(), createPage(slug()), document("Live", hero("x"))));
        JsonNode down = data(versioned("unpublish", admin(), live));
        trySave(ed, down.path("id").asLong(), down.path("version").asLong(), slug(), down.path("document")).andExpect(status().isForbidden());
        // A page that never went live can be renamed by an editor.
        JsonNode fresh = createPage(ed, slug(), document("Fresh", hero("x")));
        trySave(ed, fresh.path("id").asLong(), fresh.path("version").asLong(), slug(), fresh.path("document")).andExpect(status().isOk());
    }

    // --- preview -----------------------------------------------------------------------------------------------

    @Test
    void previewLinkShowsTheDraftSecurelyAndOnlyForItsOwnPage() throws Exception {
        String slug = slug();
        JsonNode live = publish(saveDraft(admin(), createPage(slug), document("Prev", hero("Live heading"))));
        JsonNode edited = saveDraft(admin(), live, document("Prev", hero("Draft heading")));
        JsonNode other = createPage(slug());

        long id = edited.path("id").asLong();
        String token = data(send("POST", "/api/v1/admin/pages/" + id + "/preview-token", admin(), null).andExpect(status().isOk())).path("token").asText();
        assertThat(token).isNotBlank().doesNotContain(slug);

        send("GET", "/api/v1/preview/pages/" + id + "?token=" + token, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.sections[0].content.heading.en").value("Draft heading"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        // The public page is untouched by the preview.
        publicPage(slug).andExpect(jsonPath("$.data.document.sections[0].content.heading.en").value("Live heading"));

        // Wrong page, no token, tampered token and a login token are all refused the same way.
        send("GET", "/api/v1/preview/pages/" + other.path("id").asLong() + "?token=" + token, null, null).andExpect(status().isNotFound());
        send("GET", "/api/v1/preview/pages/" + id, null, null).andExpect(status().is4xxClientError());
        send("GET", "/api/v1/preview/pages/" + id + "?token=" + token.substring(0, token.length() - 3) + "abc", null, null).andExpect(status().isNotFound());
        send("GET", "/api/v1/preview/pages/" + id + "?token=" + admin(), null, null).andExpect(status().isNotFound());
        send("GET", "/api/v1/preview/pages/99999999?token=" + token, null, null).andExpect(status().isNotFound());
    }

    @Test
    void previewLinksAreIssuedOnlyToSignedInUsersAndTheyDoNotWorkAsLogins() throws Exception {
        JsonNode page = createPage(slug());
        send("POST", "/api/v1/admin/pages/" + page.path("id").asLong() + "/preview-token", null, null).andExpect(status().isUnauthorized());
        String token = data(send("POST", "/api/v1/admin/pages/" + page.path("id").asLong() + "/preview-token", admin(), null)).path("token").asText();
        send("GET", "/api/v1/admin/pages", token, null).andExpect(status().isUnauthorized());
    }

    // --- history -----------------------------------------------------------------------------------------------

    @Test
    void revisionsAreRecordedAndRollbackRestoresTheDraftWithoutPublishing() throws Exception {
        String slug = slug();
        JsonNode page = createPage(slug);
        JsonNode v1 = publish(saveDraft(admin(), page, document("History", hero("First"))));
        JsonNode v2 = saveDraft(admin(), v1, document("History", hero("Second")));

        JsonNode list = data(send("GET", "/api/v1/admin/pages/" + page.path("id").asLong() + "/revisions", admin(), null).andExpect(status().isOk()));
        assertThat(list.size()).isGreaterThanOrEqualTo(3);
        assertThat(list.get(0).path("number").asInt()).isGreaterThan(list.get(1).path("number").asInt());
        int publishRevision = -1;
        for (JsonNode r : list) {
            if ("PUBLISH".equals(r.path("kind").asText())) {
                publishRevision = r.path("number").asInt();
            }
        }
        assertThat(publishRevision).isPositive();

        JsonNode detail = data(send("GET", "/api/v1/admin/pages/" + page.path("id").asLong() + "/revisions/" + publishRevision, admin(), null));
        assertThat(detail.at("/document/sections/0/content/heading/en").asText()).isEqualTo("First");

        JsonNode restored = data(send("POST", "/api/v1/admin/pages/" + page.path("id").asLong() + "/revisions/" + publishRevision + "/rollback", admin(),
                Map.of("expectedVersion", v2.path("version").asLong())).andExpect(status().isOk()));
        assertThat(restored.at("/document/sections/0/content/heading/en").asText()).isEqualTo("First");
        assertThat(restored.path("status").asText()).isEqualTo("PUBLISHED");
        // Rolling back changed only the draft, and the public copy was not touched.
        publicPage(slug).andExpect(jsonPath("$.data.document.sections[0].content.heading.en").value("First"));
        send("GET", "/api/v1/admin/pages/" + page.path("id").asLong() + "/revisions/9999", admin(), null).andExpect(status().isNotFound());
    }

    @Test
    void consecutiveAutosavesByOneEditorShareOneRevision() throws Exception {
        JsonNode page = createPage(slug());
        JsonNode current = page;
        for (int i = 0; i < 5; i++) {
            current = saveDraft(admin(), current, document("Typing", hero("Draft " + i)));
        }
        JsonNode list = data(send("GET", "/api/v1/admin/pages/" + page.path("id").asLong() + "/revisions", admin(), null));
        // CREATE + one merged DRAFT revision, not one per keystroke.
        assertThat(list.size()).isEqualTo(2);
        JsonNode newest = data(send("GET", "/api/v1/admin/pages/" + page.path("id").asLong() + "/revisions/" + list.get(0).path("number").asInt(), admin(), null));
        assertThat(newest.at("/document/sections/0/content/heading/en").asText()).isEqualTo("Draft 4");
    }

    // --- registry ----------------------------------------------------------------------------------------------

    @Test
    void sectionTypeCatalogueDescribesEveryFieldForTheEditor() throws Exception {
        JsonNode catalogue = data(send("GET", "/api/v1/admin/pages/section-types", admin(), null).andExpect(status().isOk()));
        List<String> types = new ArrayList<>();
        catalogue.path("sections").forEach(s -> types.add(s.path("type").asText()));
        assertThat(types).contains("hero", "rich-text", "text-image", "gallery", "video", "feature-grid", "stats",
                "product-showcase", "service-showcase", "cta-banner", "testimonials", "news-list", "faq", "dealers", "contact");
        JsonNode hero = null;
        for (JsonNode s : catalogue.path("sections")) {
            if ("hero".equals(s.path("type").asText())) {
                hero = s;
            }
        }
        assertThat(hero).isNotNull();
        assertThat(hero.path("fields").toString()).contains("\"name\":\"heading\"").contains("\"localized\":true").contains("\"kind\":\"LIST\"");
        assertThat(catalogue.path("locales")).hasSize(3);
    }
}
