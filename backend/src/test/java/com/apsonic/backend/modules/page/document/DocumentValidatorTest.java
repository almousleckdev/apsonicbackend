package com.apsonic.backend.modules.page.document;

import com.apsonic.backend.modules.page.document.DocumentValidator.Mode;
import com.apsonic.backend.shared.html.HtmlSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentValidator validator = new DocumentValidator(new SectionRegistry(), new HtmlSanitizer());

    private JsonNode json(String json) throws Exception {
        return mapper.readTree(json.replace('\'', '"'));
    }

    private String doc(String sections) {
        return "{'schemaVersion':1,'meta':{'en':{'title':'About us'}},'seo':{},'sections':[" + sections + "]}";
    }

    private boolean hasError(DocumentCheck check, String code, String pathPart) {
        return check.errors().stream().anyMatch(i -> i.code().equals(code) && i.path().contains(pathPart));
    }

    @Test
    void emptyDocumentIsValidForDraftAndForPublishWhenTitled() {
        JsonNode empty = validator.emptyDocument("Home");
        assertThat(validator.check(empty, Mode.DRAFT).hasErrors()).isFalse();
        assertThat(validator.check(empty, Mode.PUBLISH).hasErrors()).isFalse();
    }

    @Test
    void everySectionTypeWithRegistryDefaultsIsAcceptedAsADraft() {
        SectionRegistry registry = new SectionRegistry();
        com.fasterxml.jackson.databind.node.ObjectNode document = validator.emptyDocument("All sections");
        registry.all().forEach(def -> document.withArray("sections").add(registry.newSection(def.type(), java.util.UUID.randomUUID().toString())));
        DocumentCheck check = validator.check(document, Mode.DRAFT);
        assertThat(check.errors()).isEmpty();
        assertThat(check.document().get("sections")).hasSize(registry.all().size());
    }

    @Test
    void draftsMayBeIncompleteButPublishingRequiresRequiredFields() throws Exception {
        JsonNode incomplete = json(doc("{'type':'hero','content':{}}"));
        assertThat(validator.check(incomplete, Mode.DRAFT).hasErrors()).isFalse();
        DocumentCheck publish = validator.check(incomplete, Mode.PUBLISH);
        assertThat(hasError(publish, "required", "content.heading.en")).isTrue();
    }

    @Test
    void hiddenSectionsAreNotRequiredToBeComplete() throws Exception {
        JsonNode doc = json(doc("{'type':'hero','hidden':true,'content':{}}"));
        assertThat(validator.check(doc, Mode.PUBLISH).hasErrors()).isFalse();
    }

    @Test
    void englishTitleIsRequiredToPublish() throws Exception {
        JsonNode doc = json("{'schemaVersion':1,'meta':{'fr':{'title':'A propos'}},'sections':[]}");
        assertThat(hasError(validator.check(doc, Mode.PUBLISH), "required", "meta.en.title")).isTrue();
        assertThat(validator.check(doc, Mode.DRAFT).hasErrors()).isFalse();
    }

    @Test
    void rejectsUnknownSectionTypesAndUnknownFields() throws Exception {
        assertThat(hasError(validator.check(json(doc("{'type':'iframe-embed','content':{}}")), Mode.DRAFT), "unknown_section_type", "type")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'heading':{'en':'x'},'onclick':'x()'}}")), Mode.DRAFT), "unknown_field", "onclick")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','component':'Evil','content':{}}")), Mode.DRAFT), "unknown_field", "component")).isTrue();
        assertThat(hasError(validator.check(json("{'schemaVersion':1,'script':'x','sections':[]}"), Mode.DRAFT), "unknown_field", "script")).isTrue();
    }

    @Test
    void rejectsUnsafeLinksAndMedia() throws Exception {
        String hero = "{'type':'hero','content':{'heading':{'en':'x'},'ctas':[{'label':{'en':'Go'},'url':'javascript:alert(1)'}],'desktopImage':'data:image/png;base64,AAAA'}}";
        DocumentCheck check = validator.check(json(doc(hero)), Mode.DRAFT);
        assertThat(hasError(check, "invalid_url", "ctas[0].url")).isTrue();
        assertThat(hasError(check, "invalid_media", "desktopImage")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'cta-banner','content':{'ctas':[{'label':{'en':'Go'},'url':'//evil.example'}]}}")), Mode.DRAFT), "invalid_url", "ctas[0].url")).isTrue();
    }

    @Test
    void rejectsUnsafeSeoValues() throws Exception {
        JsonNode doc = json("{'schemaVersion':1,'meta':{},'seo':{'canonicalUrl':'http://insecure.example','ogImage':'javascript:x'},'sections':[]}");
        DocumentCheck check = validator.check(doc, Mode.DRAFT);
        assertThat(hasError(check, "invalid_url", "seo.canonicalUrl")).isTrue();
        assertThat(hasError(check, "invalid_media", "seo.ogImage")).isTrue();
    }

    @Test
    void plainTextFieldsRejectHtml() throws Exception {
        DocumentCheck check = validator.check(json(doc("{'type':'hero','content':{'heading':{'en':'<script>alert(1)</script>'}}}")), Mode.DRAFT);
        assertThat(hasError(check, "html_not_allowed", "heading.en")).isTrue();
        DocumentCheck title = validator.check(json("{'schemaVersion':1,'meta':{'en':{'title':'<img src=x onerror=alert(1)>'}},'sections':[]}"), Mode.DRAFT);
        assertThat(hasError(title, "html_not_allowed", "meta.en.title")).isTrue();
    }

    @Test
    void richTextIsSanitised() throws Exception {
        String body = "<p onclick=\\\"x()\\\">Hi</p><script>alert(1)</script><a href=\\\"javascript:alert(1)\\\">bad</a><img src=x onerror=alert(1)>";
        DocumentCheck check = validator.check(json(doc("{'type':'rich-text','content':{'body':{'en':'" + body + "'}}}")), Mode.DRAFT);
        String stored = check.document().at("/sections/0/content/body/en").asText();
        assertThat(stored).contains("Hi").doesNotContain("script").doesNotContain("onclick").doesNotContain("onerror").doesNotContain("javascript:");
    }

    @Test
    void enumsNumbersAndListSizesAreEnforced() throws Exception {
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'align':'diagonal'}}")), Mode.DRAFT), "invalid_option", "align")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'overlay':500}}")), Mode.DRAFT), "out_of_range", "overlay")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'overlay':'40'}}")), Mode.DRAFT), "out_of_range", "overlay")).isTrue();
        String threeCtas = "{'label':{'en':'a'},'url':'/a'},{'label':{'en':'b'},'url':'/b'},{'label':{'en':'c'},'url':'/c'},{'label':{'en':'d'},'url':'/d'}";
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'ctas':[" + threeCtas + "]}}")), Mode.DRAFT), "too_many_items", "ctas")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','style':{'background':'url(x)'},'content':{}}")), Mode.DRAFT), "invalid_option", "style.background")).isTrue();
    }

    @Test
    void sectionIdsAreAssignedAndMustBeUniqueUuids() throws Exception {
        DocumentCheck check = validator.check(json(doc("{'type':'spacer','content':{}},{'type':'spacer','content':{}}")), Mode.DRAFT);
        assertThat(check.errors()).isEmpty();
        assertThat(check.document().at("/sections/0/id").asText()).isNotEqualTo(check.document().at("/sections/1/id").asText()).hasSize(36);

        String id = "123e4567-e89b-42d3-a456-426614174000";
        DocumentCheck dup = validator.check(json(doc("{'id':'" + id + "','type':'spacer'},{'id':'" + id + "','type':'spacer'}")), Mode.DRAFT);
        assertThat(hasError(dup, "duplicate_id", "id")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'id':'not-a-uuid','type':'spacer'}")), Mode.DRAFT), "invalid_id", "id")).isTrue();
    }

    @Test
    void limitsTheNumberOfSections() throws Exception {
        StringBuilder sections = new StringBuilder();
        for (int i = 0; i < DocumentValidator.MAX_SECTIONS + 1; i++) {
            sections.append(i == 0 ? "" : ",").append("{'type':'spacer'}");
        }
        assertThat(hasError(validator.check(json(doc(sections.toString())), Mode.DRAFT), "too_many_sections", "sections")).isTrue();
    }

    @Test
    void reportsPerLanguageCompleteness() throws Exception {
        String hero = "{'type':'hero','content':{'heading':{'en':'Hello','fr':'Bonjour'},'subheading':{'en':'Sub'}}}";
        DocumentCheck check = validator.check(json(doc(hero)), Mode.PUBLISH);
        assertThat(check.errors()).isEmpty();
        assertThat(check.completeness().get("en")).isEqualTo(new DocumentCheck.Completeness(2, 2));
        assertThat(check.completeness().get("fr")).isEqualTo(new DocumentCheck.Completeness(1, 2));
        assertThat(check.completeness().get("zh")).isEqualTo(new DocumentCheck.Completeness(0, 2));
    }

    @Test
    void emptyStringsAreTreatedAsEmptyNotStored() throws Exception {
        DocumentCheck check = validator.check(json(doc("{'type':'hero','content':{'heading':{'en':'  ','fr':''},'desktopImage':''}}")), Mode.DRAFT);
        assertThat(check.errors()).isEmpty();
        assertThat(check.document().at("/sections/0/content").has("heading")).isFalse();
        assertThat(check.document().at("/sections/0/content").has("desktopImage")).isFalse();
    }

    @Test
    void normalisationIsIdempotent() throws Exception {
        String hero = "{'type':'hero','content':{'heading':{'en':'Hello'},'overlay':30}}";
        DocumentCheck first = validator.check(json(doc(hero)), Mode.DRAFT);
        DocumentCheck second = validator.check(first.document(), Mode.DRAFT);
        assertThat(second.document()).isEqualTo(first.document());
    }

    @Test
    void rejectsWrongSchemaVersionAndNonObjectDocuments() throws Exception {
        assertThat(hasError(validator.check(json("{'schemaVersion':2,'sections':[]}"), Mode.DRAFT), "unsupported_schema", "schemaVersion")).isTrue();
        assertThat(validator.check(json("[]"), Mode.DRAFT).hasErrors()).isTrue();
        assertThat(validator.check(null, Mode.DRAFT).hasErrors()).isTrue();
    }

    // --- colors, shared style and richer sections -----------------------------------------------------------

    @Test
    void colorsMustBeHexAndAreNormalisedToLowerCase() throws Exception {
        DocumentCheck ok = validator.check(json(doc("{'type':'stats','style':{'background':'custom','backgroundColor':'#0A5C3B','textColor':'#FFFFFF'},"
                + "'content':{'valueColor':'#FFD700','items':[{'value':'25+','label':{'en':'Years'},'color':'#ABCDEF'}]}}")), Mode.DRAFT);
        assertThat(ok.errors()).isEmpty();
        assertThat(ok.document().at("/sections/0/style/backgroundColor").asText()).isEqualTo("#0a5c3b");
        assertThat(ok.document().at("/sections/0/content/items/0/color").asText()).isEqualTo("#abcdef");

        for (String bad : new String[]{"red", "#fff", "#12345", "#1234567", "rgb(0,0,0)", "url(//evil.example)", "#00a651;background:red", "expression(alert(1))", "javascript:x", "0a5c3b"}) {
            DocumentCheck check = validator.check(json(doc("{'type':'spacer','style':{'backgroundColor':'" + bad.replace("'", "") + "'},'content':{}}")), Mode.DRAFT);
            assertThat(hasError(check, "invalid_color", "backgroundColor")).as(bad).isTrue();
        }
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'overlayColor':123}}")), Mode.DRAFT), "invalid_type", "overlayColor")).isTrue();
    }

    @Test
    void buttonsCarryTheirOwnColorsAndSafeLinks() throws Exception {
        String hero = "{'type':'hero','content':{'heading':{'en':'x'},'ctas':[{'label':{'en':'Buy'},'url':'/products','variant':'secondary','color':'#00A651','textColor':'#ffffff','newTab':true}]}}";
        DocumentCheck check = validator.check(json(doc(hero)), Mode.PUBLISH);
        assertThat(check.errors()).isEmpty();
        assertThat(check.document().at("/sections/0/content/ctas/0/color").asText()).isEqualTo("#00a651");
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'ctas':[{'label':{'en':'x'},'url':'/a','variant':'neon'}]}}")), Mode.DRAFT), "invalid_option", "variant")).isTrue();
    }

    @Test
    void testimonialsCanBeWrittenInTheSectionWithPhotoRoleAndStars() throws Exception {
        String section = "{'type':'testimonials','content':{'source':'custom','heading':{'en':'Voices'},'intro':{'en':'Why riders trust us'},'columns':'2',"
                + "'items':[{'quote':{'en':'Great bike','fr':'Super moto'},'name':'Awa Diallo','role':{'en':'Taxi driver, Abidjan'},'photo':'/uploads/awa.jpg','rating':4}]}}";
        DocumentCheck check = validator.check(json(doc(section)), Mode.PUBLISH);
        assertThat(check.errors()).isEmpty();
        assertThat(check.document().at("/sections/0/content/items/0/rating").asInt()).isEqualTo(4);
        // quote and name are required for each testimonial; a photo must be a safe media address; stars stay 0-5.
        DocumentCheck bad = validator.check(json(doc("{'type':'testimonials','content':{'items':[{'photo':'javascript:x','rating':9}]}}")), Mode.PUBLISH);
        assertThat(hasError(bad, "required", "quote")).isTrue();
        assertThat(hasError(bad, "required", "name")).isTrue();
        assertThat(hasError(bad, "invalid_media", "photo")).isTrue();
        assertThat(hasError(bad, "out_of_range", "rating")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'testimonials','content':{'source':'somewhere'}}")), Mode.DRAFT), "invalid_option", "source")).isTrue();
    }

    @Test
    void pictureAndOverlayControlsAreClosedChoicesAndRanges() throws Exception {
        String good = "{'type':'hero','content':{'heading':{'en':'x'},'imageSize':'contain','imagePosition':'top-left','overlay':65,'overlayColor':'#102030','overlayStyle':'bottom','height':'custom','minHeight':700}}";
        assertThat(validator.check(json(doc(good)), Mode.PUBLISH).errors()).isEmpty();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'imagePosition':'41% 12px'}}")), Mode.DRAFT), "invalid_option", "imagePosition")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'hero','content':{'minHeight':99999}}")), Mode.DRAFT), "out_of_range", "minHeight")).isTrue();
        assertThat(hasError(validator.check(json(doc("{'type':'cta-banner','content':{'overlay':120}}")), Mode.DRAFT), "out_of_range", "overlay")).isTrue();
        String style = "{'type':'rich-text','style':{'background':'custom','backgroundColor':'#222222','backgroundImage':'/uploads/bg.jpg','backgroundSize':'contain','backgroundPosition':'bottom-right','backgroundFixed':true,'backgroundOverlay':30,'buttonShape':'square','showOn':'mobile'},'content':{}}";
        assertThat(validator.check(json(doc(style)), Mode.DRAFT).errors()).isEmpty();
        assertThat(hasError(validator.check(json(doc("{'type':'rich-text','style':{'backgroundImage':'javascript:alert(1)'},'content':{}}")), Mode.DRAFT), "invalid_media", "backgroundImage")).isTrue();
    }

    @Test
    void newSectionsStartWithTheirOwnStyleDefaults() {
        SectionRegistry registry = new SectionRegistry();
        assertThat(registry.newSection("cta-banner", "a").at("/style/background").asText()).isEqualTo("brand");
        assertThat(registry.newSection("rich-text", "b").at("/style/background").asText()).isEqualTo("white");
        // A hero draws its own background, so the background/spacing style does not apply to it.
        assertThat(registry.newSection("hero", "c").at("/style").has("background")).isFalse();
        assertThat(registry.newSection("hero", "d").at("/content/overlay").asInt()).isEqualTo(40);
        assertThat(registry.newSection("testimonials", "e").at("/content/source").asText()).isEqualTo("custom");
    }

    @Test
    void everySectionTypeOnlyNamesStyleFieldsThatExist() {
        SectionRegistry registry = new SectionRegistry();
        java.util.Set<String> known = new java.util.HashSet<>();
        registry.styleFields().forEach(f -> known.add(f.getName()));
        registry.all().forEach(def -> {
            if (def.styleFieldNames() != null) {
                assertThat(known).as(def.type()).containsAll(def.styleFieldNames());
            }
            // Conditional fields point at a sibling that exists.
            java.util.Set<String> siblings = new java.util.HashSet<>();
            def.fields().forEach(f -> siblings.add(f.getName()));
            def.fields().forEach(f -> {
                if (f.getShowWhenField() != null) {
                    assertThat(siblings).as(def.type() + "." + f.getName()).contains(f.getShowWhenField());
                }
            });
        });
        registry.styleFields().forEach(f -> {
            if (f.getShowWhenField() != null) {
                assertThat(known).as("style." + f.getName()).contains(f.getShowWhenField());
            }
        });
    }

    @Test
    void drafts_savedWithTheOldSingleButtonFieldsAreUpgradedInsteadOfRejected() throws Exception {
        String old = "{'type':'cta-banner','content':{'heading':{'en':'Talk'},'buttonLabel':{'en':'Contact us'},'buttonUrl':'/contact'}}";
        DocumentCheck check = validator.check(json(doc(old)), Mode.PUBLISH);
        assertThat(check.errors()).isEmpty();
        assertThat(check.document().at("/sections/0/content/ctas/0/url").asText()).isEqualTo("/contact");
        assertThat(check.document().at("/sections/0/content/ctas/0/label/en").asText()).isEqualTo("Contact us");
        assertThat(check.document().at("/sections/0/content").has("buttonUrl")).isFalse();

        String text = "{'type':'text-image','content':{'heading':{'en':'x'},'ctaLabel':{'en':'More'},'ctaUrl':'https://example.com'}}";
        assertThat(validator.check(json(doc(text)), Mode.DRAFT).document().at("/sections/0/content/ctas/0/url").asText()).isEqualTo("https://example.com");

        // Existing buttons win; the leftovers are dropped; an unsafe old link is still refused.
        String both = "{'type':'cta-banner','content':{'ctas':[{'label':{'en':'New'},'url':'/new'}],'buttonUrl':'/old'}}";
        DocumentCheck kept = validator.check(json(doc(both)), Mode.DRAFT);
        assertThat(kept.document().at("/sections/0/content/ctas")).hasSize(1);
        assertThat(kept.document().at("/sections/0/content/ctas/0/url").asText()).isEqualTo("/new");
        String unsafe = "{'type':'cta-banner','content':{'buttonLabel':{'en':'x'},'buttonUrl':'javascript:alert(1)'}}";
        assertThat(hasError(validator.check(json(doc(unsafe)), Mode.DRAFT), "invalid_url", "ctas[0].url")).isTrue();
        // Genuinely unknown fields are still rejected.
        assertThat(hasError(validator.check(json(doc("{'type':'cta-banner','content':{'onclick':'x'}}")), Mode.DRAFT), "unknown_field", "onclick")).isTrue();
    }
}
