package com.rbi.cms.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.rbi.cms.search.support.ElasticsearchAvailable;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Proves what the mapping does to real Indic text, against a real Elasticsearch.
 *
 * <p>Skips cleanly when no node is reachable — see
 * {@link com.rbi.cms.search.support.ElasticsearchAvailable}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Multilingual complaint search")
class MultilingualSearchIT {

    private static final String INDEX = "it-cms-complaints";

    private static RestClient restClient;
    private static ElasticsearchClient client;
    private static boolean available;

    @BeforeAll
    static void setUp() throws IOException {
        available = ElasticsearchAvailable.isReachable();
        if (!available) {
            return;
        }

        restClient = ElasticsearchAvailable.restClient();
        client = ElasticsearchAvailable.client(restClient);

        try {
            client.indices().delete(d -> d.index(INDEX));
        } catch (Exception ignored) {
            // First run on a clean node.
        }

        // Same JSON the service ships, so a mapping change that breaks Indic retrieval breaks here.
        try (InputStream settings = new ClassPathResource("elasticsearch/cms-complaints-settings.json").getInputStream();
             InputStream mappings = new ClassPathResource("elasticsearch/cms-complaints-mappings.json").getInputStream()) {
            client.indices().create(CreateIndexRequest.of(b -> b
                    .index(INDEX)
                    .settings(s -> s.withJson(settings))
                    .mappings(m -> m.withJson(mappings))));
        }

        indexFixtures();
        client.indices().refresh(r -> r.index(INDEX));
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (!available) {
            return;
        }
        try {
            client.indices().delete(d -> d.index(INDEX));
        } finally {
            restClient.close();
        }
    }

    private static void indexFixtures() throws IOException {
        put("HI-1", doc("HI-1", "hi", "एटीएम से नकदी नहीं मिली",
                "मेरी कई शिकायतों का समाधान नहीं हुआ है", null));

        // Mixed script. An English closure clause quoted inside a Hindi narrative is the normal
        // shape of a real closure record here, not an edge case.
        put("MIX-1", doc("MIX-1", "hi", "शिकायत बंद करने का आदेश",
                "शिकायत को Clause 15(1)(a) of the Scheme के अंतर्गत बंद किया गया क्योंकि "
                        + "complainant ने कोई further evidence नहीं दिया", "15(1)(a)"));

        put("EN-1", doc("EN-1", "en", "ATM cash not dispensed",
                "My complaints were never resolved by the bank", null));
        put("BN-1", doc("BN-1", "bn", "ব্যাঙ্কের অভিযোগ", "আমার অভিযোগের কোনো সমাধান হয়নি", null));
        put("UR-1", doc("UR-1", "ur", "بینک کی شکایت", "میری شکایت کا کوئی حل نہیں ہوا", null));
        put("MR-1", doc("MR-1", "mr", "बँक तक्रार", "माझ्या तक्रारीचे निराकरण झाले नाही", null));
        put("TA-1", doc("TA-1", "ta", "வங்கி கட்டணம் தவறாக வசூலிக்கப்பட்டது",
                "எனது கணக்கில் கட்டணம் இரண்டு முறை எடுக்கப்பட்டது", null));
        put("TE-1", doc("TE-1", "te", "బ్యాంకు ఫిర్యాదు", "నా ఫిర్యాదు పరిష్కారం కాలేదు", null));
        put("KN-1", doc("KN-1", "kn", "ಬ್ಯಾಂಕ್ ದೂರು", "ನನ್ನ ದೂರು ಪರಿಹಾರವಾಗಿಲ್ಲ", null));
        put("ML-1", doc("ML-1", "ml", "ബാങ്ക് പരാതി", "എന്റെ പരാതി പരിഹരിച്ചില്ല", null));
        put("GU-1", doc("GU-1", "gu", "બેંક ફરિયાદ", "મારી ફરિયાદનો ઉકેલ આવ્યો નથી", null));
        put("PA-1", doc("PA-1", "pa", "ਬੈਂਕ ਸ਼ਿਕਾਇਤ", "ਮੇਰੀ ਸ਼ਿਕਾਇਤ ਦਾ ਹੱਲ ਨਹੀਂ ਹੋਇਆ", null));
        put("OR-1", doc("OR-1", "or", "ବ୍ୟାଙ୍କ ଅଭିଯୋଗ", "ମୋର ଅଭିଯୋଗର ସମାଧାନ ହୋଇନାହିଁ", null));
        put("AS-1", doc("AS-1", "as", "বেংকৰ অভিযোগ", "মোৰ অভিযোগৰ সমাধান হোৱা নাই", null));

        // Timeline remarks in a nested doc: the richest text source in the system.
        ComplaintDocument withTimeline = doc("TL-1", "hi", "ऋण खाते की शिकायत",
                "ब्याज दर विवाद", null);
        ComplaintDocument.TimelineEntry entry = new ComplaintDocument.TimelineEntry();
        entry.setAction("CLOSED");
        entry.setFromStatus("under_review");
        entry.setToStatus("closed");
        entry.setPerformedByRole("RBIO_OMBUDSMAN");
        entry.setPerformedAt("2026-09-20T11:00:00");
        entry.setRemarks("बैंक ने ग्राहक को ब्याज वापस कर दिया; complaint closed under Clause 16(1)");
        withTimeline.getTimeline().add(entry);
        put("TL-1", withTimeline);
    }

    private static ComplaintDocument doc(String id, String language, String subject,
                                         String description, String closureClause) {
        ComplaintDocument d = new ComplaintDocument();
        d.setComplaintId(id);
        d.setComplaintNumber("CMP-" + id);
        d.setDetectedLanguage(language);
        d.setSubject(subject);
        d.setDescription(description);
        d.setClosureClause(closureClause);
        d.setStatus("closed");
        d.setCategoryId("7");
        d.setCreatedAt("2026-09-01T10:00:00");
        return d;
    }

    private static void put(String id, ComplaintDocument document) throws IOException {
        client.index(b -> b.index(INDEX).id(id).document(document));
    }

    private List<String> idsFor(String queryText, String... fields) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .size(20)
                .query(Query.of(q -> q.multiMatch(mm -> mm.query(queryText).fields(List.of(fields))))),
                Map.class);

        return response.hits().hits().stream()
                .map(h -> String.valueOf(h.source().get("complaintId")))
                .toList();
    }

    @Test
    @DisplayName("the hindi subfield stems शिकायतों to शिकायत where the unstemmed base field cannot")
    void hindiSubfieldStemsWhereBaseFieldCannot() throws IOException {
        assumeThat(available).as("Elasticsearch at %s", ElasticsearchAvailable.describe()).isTrue();

        // HI-1's description contains only the inflected plural शिकायतों. The base field is
        // icu_tokenizer + indic_normalization + icu_folding, which normalises but does not stem, so
        // the singular query term cannot reach it.
        assertThat(idsFor("शिकायत", "description"))
                .as("base cms_indic field does not stem, so the inflected doc is unreachable")
                .doesNotContain("HI-1");

        // The hindi subfield applies hindi_stemmer, which reduces शिकायतों -> शिकायत.
        assertThat(idsFor("शिकायत", "description.hi"))
                .as("hindi subfield stems and therefore matches")
                .contains("HI-1");

        // Which is why production queries both.
        assertThat(idsFor("शिकायत", "description", "description.hi")).contains("HI-1");
    }

    @Test
    @DisplayName("a mixed-script document is reachable by both its English clause and its Hindi prose")
    void mixedScriptDocumentIsReachableFromBothScripts() throws IOException {
        assumeThat(available).isTrue();

        assertThat(idsFor("Clause 15(1)(a)", "description", "description.en"))
                .as("English clause quoted inside Hindi prose")
                .contains("MIX-1");

        assertThat(idsFor("बंद", "description", "description.hi"))
                .as("Hindi prose in the same document")
                .contains("MIX-1");

        assertThat(idsFor("complainant evidence", "description", "description.en"))
                .as("English words embedded mid-sentence in a Devanagari narrative")
                .contains("MIX-1");
    }

    @Test
    @DisplayName("closureClause is a keyword, so it term-filters and does not analyse away the parentheses")
    void closureClauseIsTermFilterable() throws IOException {
        assumeThat(available).isTrue();

        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .query(q -> q.term(t -> t.field("closureClause").value("15(1)(a)"))), Map.class);

        assertThat(response.hits().total().value())
                .as("defect 1: term filters need a keyword mapping, which dynamic mapping did not give")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("createdAt sorts as a date, and status term-filters")
    void sortAndFilterWorkOnKeywordAndDateMappings() throws IOException {
        assumeThat(available).isTrue();

        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .size(3)
                .query(q -> q.term(t -> t.field("status").value("closed")))
                .sort(so -> so.field(f -> f.field("createdAt")
                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))), Map.class);

        assertThat(response.hits().hits()).isNotEmpty();
    }

    @Test
    @DisplayName("nested timeline remarks are searchable in both scripts without flattening the document")
    void nestedTimelineRemarksAreSearchable() throws IOException {
        assumeThat(available).isTrue();

        SearchResponse<Map> hindi = client.search(s -> s
                .index(INDEX)
                .query(q -> q.nested(n -> n
                        .path("timeline")
                        .query(nq -> nq.multiMatch(mm -> mm
                                .query("ब्याज")
                                .fields(List.of("timeline.remarks", "timeline.remarks.hi"))))
                        .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode.Max))),
                Map.class);

        assertThat(hindi.hits().hits())
                .as("Hindi remark text")
                .extracting(h -> String.valueOf(h.source().get("complaintId")))
                .contains("TL-1");

        SearchResponse<Map> english = client.search(s -> s
                .index(INDEX)
                .query(q -> q.nested(n -> n
                        .path("timeline")
                        .query(nq -> nq.multiMatch(mm -> mm
                                .query("closed under Clause")
                                .fields(List.of("timeline.remarks", "timeline.remarks.en"))))
                        .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode.Max))),
                Map.class);

        assertThat(english.hits().hits())
                .as("English clause inside the same remark")
                .extracting(h -> String.valueOf(h.source().get("complaintId")))
                .contains("TL-1");
    }

    @ParameterizedTest(name = "[{index}] {0}: querying \"{1}\" finds {2}")
    @CsvSource(delimiter = '|', value = {
            "en | complaint      | EN-1",
            "hi | नकदी           | HI-1",
            "bn | অভিযোগ         | BN-1",
            "ur | شکایت          | UR-1",
            "mr | तक्रार          | MR-1",
            "ta | கட்டணம்         | TA-1",
            "te | ఫిర్యాదు         | TE-1",
            "kn | ದೂರು           | KN-1",
            "ml | പരാതി          | ML-1",
            "gu | ફરિયાદ          | GU-1",
            "pa | ਸ਼ਿਕਾਇਤ         | PA-1",
            "or | ଅଭିଯୋଗ         | OR-1",
            "as | অভিযোগ         | AS-1",
    })
    @DisplayName("every served language retrieves its own fixture")
    void eachLanguageRetrievesItsFixture(String language, String queryText, String expectedId)
            throws IOException {
        assumeThat(available).isTrue();

        List<String> ids = idsFor(queryText.trim(),
                "subject", "description",
                "subject.en", "description.en",
                "subject.hi", "description.hi",
                "subject.bn", "description.bn",
                "subject.ur", "description.ur");

        assertThat(ids).as("language %s", language).contains(expectedId.trim());
    }

    /**
     * Records the limit of the ICU-only languages rather than hiding it.
     *
     * <p>Tamil, Telugu, Kannada and Malayalam are agglutinative and Elasticsearch 8.15.3 ships no
     * analyzer for any of them. The base field normalises and tokenises correctly but does not stem,
     * so an inflected query form does not reach a document holding only the root. If a future ES
     * version or plugin adds Dravidian stemming this test will start failing, which is the signal to
     * add the subfield and delete this test.
     */
    @Test
    @DisplayName("Tamil inflected forms do NOT match the root: ICU normalises, it does not stem")
    void tamilAgglutinationIsNotHandled() throws IOException {
        assumeThat(available).isTrue();

        assertThat(idsFor("கட்டணம்", "subject", "description"))
                .as("near-surface form matches")
                .contains("TA-1");

        assertThat(idsFor("கட்டணங்கள்", "subject", "description"))
                .as("ICU tokenises and normalises but performs no stemming, so the plural misses the root")
                .doesNotContain("TA-1");
    }
}
