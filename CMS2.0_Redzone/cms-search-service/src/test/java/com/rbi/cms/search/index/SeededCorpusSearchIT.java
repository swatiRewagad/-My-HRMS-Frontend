package com.rbi.cms.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.rbi.cms.search.support.ElasticsearchAvailable;
import com.rbi.cms.search.support.MultilingualCorpus;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Per-language retrieval against the corpus {@code MultilingualCorpusSeeder} writes.
 *
 * <p><b>How this differs from {@link MultilingualSearchIT}.</b> That test asks what the MAPPING does
 * and uses whatever fixture makes the point. This one asks whether the SEEDED CORPUS is actually
 * retrievable in all 13 languages, and so it indexes the seeder's own text, restated in
 * {@link MultilingualCorpus}. That restatement is a copy rather than a shared constant because the two
 * modules have no dependency between them — see that class for why, and for the drift risk it carries.
 *
 * <p><b>Why it builds its own index instead of reading {@code cms-complaints}.</b> A test that queried
 * the live alias would pass or fail depending on whether someone had run a reindex since the last boot,
 * on a database six sessions share. That is not a regression test, it is a status report. Indexing the
 * corpus into a scratch index from the shipped settings and mapping JSON makes the result depend only
 * on the mapping and the text, which are the two things under test. That the DB rows then reach the
 * live index through {@code ReindexJob} is verified separately, by execution, and recorded in the
 * session report.
 *
 * <p>Every expectation here is a MEASUREMENT taken against a live Elasticsearch 8.15.3 node with
 * {@code analysis-icu}, including the misses. The misses matter most: they are the honest record of
 * which languages get no stemming, and if a future ES version starts stemming them these tests fail
 * and tell someone to add a subfield.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Seeded multilingual corpus retrieval")
class SeededCorpusSearchIT {

    private static final String INDEX = "it-seeded-corpus";

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

        try (InputStream settings = new ClassPathResource("elasticsearch/cms-complaints-settings.json").getInputStream();
             InputStream mappings = new ClassPathResource("elasticsearch/cms-complaints-mappings.json").getInputStream()) {
            client.indices().create(CreateIndexRequest.of(b -> b
                    .index(INDEX)
                    .settings(s -> s.withJson(settings))
                    .mappings(m -> m.withJson(mappings))));
        }

        for (MultilingualCorpus.LanguagePair pair : MultilingualCorpus.pairs()) {
            index(docId(pair.language(), true), pair.language(), pair.rootSubject(), pair.rootBody());
            index(docId(pair.language(), false), pair.language(), pair.inflSubject(), pair.inflBody());
        }
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

    /** {@code HI-ROOT}, {@code HI-INFL}: the document ids, matching the seeder's number suffixes. */
    private static String docId(String language, boolean root) {
        return language.toUpperCase() + (root ? "-ROOT" : "-INFL");
    }

    private static void index(String id, String language, String subject, String description)
            throws IOException {
        ComplaintDocument doc = new ComplaintDocument();
        doc.setComplaintId(id);
        doc.setComplaintNumber(MultilingualCorpus.NUMBER_PREFIX + id);
        doc.setDetectedLanguage(language);
        doc.setSubject(subject);
        doc.setDescription(description);
        doc.setStatus("closed");
        doc.setCategoryId("1");
        doc.setEntityCode("MLCORPUS");
        doc.setCreatedAt("2026-01-15T10:00:00");
        client.index(b -> b.index(INDEX).id(id).document(doc));
    }

    private List<String> idsFor(String queryText, String... fields) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .size(40)
                .query(Query.of(q -> q.multiMatch(mm -> mm.query(queryText).fields(List.of(fields))))),
                Map.class);

        return response.hits().hits().stream()
                .map(h -> String.valueOf(h.source().get("complaintId")))
                .sorted()
                .toList();
    }

    private static final String[] BASE_FIELDS = {"subject", "description"};

    static List<MultilingualCorpus.LanguagePair> allPairs() {
        return MultilingualCorpus.pairs();
    }

    // ───────────────────────── 1. every language is retrievable at all ─────────────────────────

    @Test
    @DisplayName("all 13 languages are present and all 26 documents are indexed")
    void corpusCoversThirteenLanguages() throws IOException {
        assumeThat(available).as("Elasticsearch at %s", ElasticsearchAvailable.describe()).isTrue();

        assertThat(MultilingualCorpus.pairs()).hasSize(13);

        SearchResponse<Map> all = client.search(s -> s.index(INDEX).size(0)
                .query(q -> q.matchAll(m -> m)), Map.class);
        assertThat(all.hits().total().value())
                .as("13 languages x (root + inflected)")
                .isEqualTo(26);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allPairs")
    @DisplayName("each language's root term retrieves exactly its own root document")
    void rootTermRetrievesItsOwnDocument(MultilingualCorpus.LanguagePair pair) throws IOException {
        assumeThat(available).isTrue();

        List<String> hits = idsFor(pair.rootTerm(), BASE_FIELDS);

        assertThat(hits)
                .as("%s root term '%s' on the cms_indic base fields", pair.language(), pair.rootTerm())
                .contains(docId(pair.language(), true));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allPairs")
    @DisplayName("each language's inflected term retrieves its inflected document on the base field")
    void inflectedTermRetrievesItsOwnDocument(MultilingualCorpus.LanguagePair pair) throws IOException {
        assumeThat(available).isTrue();

        assertThat(idsFor(pair.inflectedTerm(), BASE_FIELDS))
                .as("%s inflected term '%s' matches its own surface form", pair.language(),
                        pair.inflectedTerm())
                .contains(docId(pair.language(), false));
    }

    // ───────────────────────── 2. the stemming asymmetry ─────────────────────────

    @ParameterizedTest(name = "[{index}] {0} does NOT stem on the base cms_indic field")
    @MethodSource("allPairs")
    @DisplayName("no language stems on the base field: cms_indic normalises and folds, it never stems")
    void baseFieldNeverStemsInAnyLanguage(MultilingualCorpus.LanguagePair pair) throws IOException {
        assumeThat(available).isTrue();

        // This is the single fact that makes the per-language subfields necessary at all, and it holds
        // uniformly across all 13 — including English, whose base field is cms_indic like everyone
        // else's and therefore does not reduce "complaints" to "complaint" either.
        assertThat(idsFor(pair.rootTerm(), BASE_FIELDS))
                .as("%s: base field must not reach the inflected doc from the root term", pair.language())
                .doesNotContain(docId(pair.language(), false));
    }

    @Test
    @DisplayName("hi: the hindi subfield stems शिकायतों to शिकायत where the base field cannot")
    void hindiSubfieldStems() throws IOException {
        assumeThat(available).isTrue();

        // Measured: description:"शिकायतों" -> 0 of the two Hindi docs from the root term,
        // description.hi:"शिकायत" -> both. hindi_stemmer reduces शिकायतों to शिकायत.
        assertThat(idsFor("शिकायत", BASE_FIELDS))
                .as("base field reaches only the root document")
                .containsExactly("HI-ROOT");

        assertThat(idsFor("शिकायत", "subject.hi", "description.hi"))
                .as("the hindi subfield reaches BOTH: this is the stemming gain")
                .contains("HI-ROOT", "HI-INFL");
    }

    @Test
    @DisplayName("bn: the bengali subfield stems অভিযোগগুলি to অভিযোগ")
    void bengaliSubfieldStems() throws IOException {
        assumeThat(available).isTrue();

        assertThat(idsFor("অভিযোগ", BASE_FIELDS))
                .as("base field cannot reach the Bengali plural from the singular")
                .doesNotContain("BN-INFL");

        assertThat(idsFor("অভিযোগ", "subject.bn", "description.bn"))
                .as("bengali_stemmer reduces the গুলি plural")
                .contains("BN-ROOT", "BN-INFL");
    }

    @Test
    @DisplayName("en: the english subfield stems complaints to complaint")
    void englishSubfieldStems() throws IOException {
        assumeThat(available).isTrue();

        assertThat(idsFor("complaint", BASE_FIELDS))
                .as("cms_indic does not stem English any more than it stems Hindi")
                .containsExactly("EN-ROOT");

        assertThat(idsFor("complaint", "subject.en", "description.en"))
                .as("english analyzer stems, so one query term reaches both documents")
                .contains("EN-ROOT", "EN-INFL");
    }

    /**
     * Urdu is the honest middle case: it has a custom analyzer that helps with SPELLING, not grammar.
     */
    @Test
    @DisplayName("ur: cms_urdu folds Arabic-form spelling onto the Urdu form but does not stem")
    void urduNormalisesScriptButDoesNotStem() throws IOException {
        assumeThat(available).isTrue();

        // Arabic kaf/yeh (شكايت) vs Urdu keheh/yeh (شکایت): different code points, same word. The
        // arabic_normalization filter folds them together, which is a real retrieval gain for text
        // typed on an Arabic keyboard.
        assertThat(idsFor("شكايت", "subject.ur", "description.ur"))
                .as("Arabic-form spelling reaches the Urdu-form document")
                .contains("UR-ROOT");

        // But شکایات (plural) is a broken-plural form, and no stemmer exists for it.
        assertThat(idsFor("شکایت", "subject.ur", "description.ur"))
                .as("the ur subfield normalises only; the broken plural stays out of reach")
                .doesNotContain("UR-INFL");
    }

    // ───────────────────────── 3. the nine without a stemmer ─────────────────────────

    /**
     * The limitation, asserted rather than documented.
     *
     * <p>{@code ta te mr gu kn ml pa or as} have no stemmer in Elasticsearch 8.15.3. For seven of them
     * there is no subfield to try at all. For {@code mr} and {@code as} there IS one —
     * {@code LanguageDetector} routes Marathi to {@code .hi} and Assamese to {@code .bn} because the
     * scripts are shared — and the measured result is that borrowing the subfield does NOT buy
     * stemming: the Hindi stemmer does not reduce the Marathi तक्रारींवर and the Bengali stemmer does
     * not reduce the Assamese অভিযোগসমূহ. They belong in this group despite appearances.
     */
    @ParameterizedTest(name = "[{index}] {0}: inflected form does not reach the root")
    @MethodSource("languagesWithoutStemming")
    @DisplayName("the nine languages without a stemmer: an inflected query cannot reach the root doc")
    void noStemmerLanguagesCannotMatchAcrossInflection(MultilingualCorpus.LanguagePair pair)
            throws IOException {
        assumeThat(available).isTrue();

        String[] fields = fieldsFor(pair.language());

        // Surface form works. This is what these languages DO get: tokenisation, normalisation, folding.
        assertThat(idsFor(pair.rootTerm(), fields))
                .as("%s: the root surface form retrieves the root document", pair.language())
                .contains(docId(pair.language(), true));

        // And this is what they do not get.
        assertThat(idsFor(pair.inflectedTerm(), fields))
                .as("%s: '%s' does not reach the root document — no stemmer exists for this language",
                        pair.language(), pair.inflectedTerm())
                .doesNotContain(docId(pair.language(), true));

        assertThat(idsFor(pair.rootTerm(), fields))
                .as("%s: and the reverse direction misses too", pair.language())
                .doesNotContain(docId(pair.language(), false));
    }

    static List<MultilingualCorpus.LanguagePair> languagesWithoutStemming() {
        List<String> codes = List.of("ta", "te", "mr", "gu", "kn", "ml", "pa", "or", "as");
        List<MultilingualCorpus.LanguagePair> out = new ArrayList<>();
        for (MultilingualCorpus.LanguagePair pair : MultilingualCorpus.pairs()) {
            if (codes.contains(pair.language())) {
                out.add(pair);
            }
        }
        return out;
    }

    /**
     * Base fields, plus the borrowed subfield for the two languages that have one.
     *
     * <p>Including the borrowed subfield is what makes the mr/as assertions meaningful: without it the
     * test would only be proving that a field which does not exist cannot stem.
     */
    private static String[] fieldsFor(String language) {
        return switch (language) {
            case "mr" -> new String[]{"subject", "description", "subject.hi", "description.hi"};
            case "as" -> new String[]{"subject", "description", "subject.bn", "description.bn"};
            default -> BASE_FIELDS;
        };
    }

    // ───────────────────────── 4. cross-language behaviour ─────────────────────────

    /**
     * Shared scripts mean shared words, and the index cannot and should not pretend otherwise.
     */
    @Test
    @DisplayName("bn and as share the surface form অভিযোগ, so one query legitimately returns both")
    void sharedScriptRetrievesBothLanguages() throws IOException {
        assumeThat(available).isTrue();

        assertThat(idsFor("অভিযোগ", BASE_FIELDS))
                .as("identical spelling in two languages is one token; filter by detectedLanguage to split")
                .contains("BN-ROOT", "AS-ROOT");
    }

    @Test
    @DisplayName("detectedLanguage term-filters the corpus down to one language")
    void detectedLanguageIsFilterable() throws IOException {
        assumeThat(available).isTrue();

        // The companion to the test above: the remedy for cross-language bleed is a term filter, which
        // requires detectedLanguage to be a keyword. It is, and the seeder sets it via the row mapper's
        // LanguageDetector at index time.
        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .size(0)
                .query(q -> q.term(t -> t.field("detectedLanguage").value("as"))), Map.class);

        assertThat(response.hits().total().value())
                .as("exactly the two Assamese documents")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a query in one script never returns documents in an unrelated script")
    void scriptsDoNotBleedAcrossUnrelatedLanguages() throws IOException {
        assumeThat(available).isTrue();

        // Tamil and Telugu are neighbours with separate Unicode blocks; icu_folding must not collapse
        // them into each other. A regression here would be subtle and very damaging to precision.
        assertThat(idsFor("புகார்", BASE_FIELDS))
                .as("a Tamil query returns only Tamil documents")
                .allSatisfy(id -> assertThat(id).startsWith("TA-"));

        assertThat(idsFor("ದೂರು", BASE_FIELDS))
                .as("a Kannada query returns only Kannada documents")
                .allSatisfy(id -> assertThat(id).startsWith("KN-"));
    }

    @Test
    @DisplayName("every seeded document carries non-empty subject and description in the index")
    void seededTextIsActuallyPresentInTheIndex() throws IOException {
        assumeThat(available).isTrue();

        // Guards the defect class fixed in ComplaintIndexingListener this session: a partial map passed
        // to a full IndexRequest replaced the document with a stub and erased the only two fields search
        // matches on. A corpus whose text had been blanked would still COUNT as 26 documents, so
        // counting is not enough — the fields have to be read back.
        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .size(40)
                .query(q -> q.matchAll(m -> m)), Map.class);

        assertThat(response.hits().hits()).hasSize(26);
        for (var hit : response.hits().hits()) {
            Map<?, ?> source = hit.source();
            assertThat((String) source.get("subject"))
                    .as("subject of %s", hit.id()).isNotBlank();
            assertThat((String) source.get("description"))
                    .as("description of %s", hit.id()).isNotBlank();
        }
    }
}
