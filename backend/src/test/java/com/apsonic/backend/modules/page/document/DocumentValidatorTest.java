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
        assertThat(hasError(validator.check(json(doc("{'type':'cta-banner','content':{'buttonUrl':'//evil.example'}}")), Mode.DRAFT), "invalid_url", "buttonUrl")).isTrue();
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
        String threeCtas = "{'label':{'en':'a'},'url':'/a'},{'label':{'en':'b'},'url':'/b'},{'label':{'en':'c'},'url':'/c'}";
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
}
