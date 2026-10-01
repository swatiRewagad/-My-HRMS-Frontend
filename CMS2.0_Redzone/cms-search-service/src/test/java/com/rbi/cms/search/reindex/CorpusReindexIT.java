package com.rbi.cms.search.reindex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.rbi.cms.search.config.SearchProperties;
import com.rbi.cms.search.index.ComplaintIndexManager;
import com.rbi.cms.search.index.LanguageDetector;
import com.rbi.cms.search.support.ElasticsearchAvailable;
import com.rbi.cms.search.support.MultilingualCorpus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Proves the seeded corpus actually reaches Elasticsearch through the PRODUCTION indexing path.
 *
 * <p><b>Why this test exists separately from {@code SeededCorpusSearchIT}.</b> That test indexes the
 * corpus text itself, so it proves the mapping retrieves Indic text — but it would pass just as well if
 * the seeder had never written a row, because it never touches the database. This one starts from
 * {@code COMPLAINTS} in MySQL and runs the real {@link ReindexJob}: its real keyset SQL, its real
 * {@link ComplaintRowMapper}, its real bulk writes and its real alias swap. If the seeder's rows cannot
 * make that journey, this fails and nothing else would have.
 *
 * <p><b>Wired by hand rather than with {@code @SpringBootTest}.</b> Direct construction keeps this test
 * to the database-to-index path and off the Kafka consumer and the rest of the context, neither of which
 * it asserts anything about. {@link ReindexJob} is still exercised verbatim — only its collaborators are
 * supplied here instead of by the container.
 *
 * <p>This was originally forced rather than chosen: the context could not start at all, because two
 * {@code ElasticsearchClient} beans were published with no {@code @Primary} and the consumers did not
 * qualify. That is fixed in {@code ElasticsearchConfig} and held by
 * {@code ElasticsearchConfigWiringTest}, so the hand-wiring is now a scoping decision, not a workaround.
 *
 * <p>Skips when either MySQL or Elasticsearch is absent. It writes to a scratch alias, never to
 * {@code cms-complaints}, so running it cannot disturb the live index six sessions share.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Seeded corpus reaches Elasticsearch via ReindexJob")
class CorpusReindexIT {

    /** Deliberately NOT cms-complaints: this test must not repoint the alias other sessions read. */
    private static final String SCRATCH_ALIAS = "it-corpus-reindex";

    private static HikariDataSource dataSource;
    private static RestClient restClient;
    private static ElasticsearchClient client;
    private static ComplaintIndexManager indexManager;
    private static String builtIndex;
    private static ReindexJob.ReindexStatus status;
    private static boolean available;

    @BeforeAll
    static void setUp() throws IOException {
        if (!ElasticsearchAvailable.isReachable()) {
            return;
        }

        dataSource = mysqlOrNull();
        if (dataSource == null) {
            return;
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long seeded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM COMPLAINTS WHERE complaint_number LIKE ?",
                Long.class, MultilingualCorpus.NUMBER_PREFIX + "%");
        if (seeded == 0) {
            // The corpus has not been seeded on this machine. Skipping is correct: this test asserts
            // that seeded rows reach the index, not that the seeder has been run.
            return;
        }

        restClient = ElasticsearchAvailable.restClient();
        client = ElasticsearchAvailable.client(restClient);

        SearchProperties properties = new SearchProperties();
        properties.setAlias(SCRATCH_ALIAS);
        properties.getReindex().setBatchSize(500);
        properties.getReindex().setPauseBetweenBatchesMs(0);

        LanguageDetector detector = new LanguageDetector();
        indexManager = new ComplaintIndexManager(client, properties);

        // The production class, constructed exactly as Spring would construct it.
        ReindexJob job = new ReindexJob(jdbc, client, indexManager,
                new ComplaintRowMapper(detector), properties);

        status = job.run();
        builtIndex = status.index();

        if (builtIndex != null) {
            client.indices().refresh(r -> r.index(builtIndex));
        }
        available = true;
    }

    @AfterAll
    static void tearDown() throws IOException {
        try {
            if (builtIndex != null) {
                try {
                    indexManager.deleteIndex(builtIndex);
                } catch (Exception ignored) {
                    // Already gone.
                }
            }
        } finally {
            if (restClient != null) {
                restClient.close();
            }
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    private static HikariDataSource mysqlOrNull() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3306/cms_db?useSSL=false"
                    + "&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    + "&characterEncoding=UTF-8&useUnicode=true");
            config.setUsername("cms_user");
            config.setPassword("cms_pass");
            config.setMaximumPoolSize(2);
            config.setConnectionTimeout(3000);
            config.setInitializationFailTimeout(3000);
            return new HikariDataSource(config);
        } catch (Exception e) {
            return null;
        }
    }

    static List<MultilingualCorpus.LanguagePair> allPairs() {
        return MultilingualCorpus.pairs();
    }

    private List<String> numbersFor(String queryText, String... fields) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                .index(builtIndex)
                .size(50)
                // Restricted to the corpus so a real complaint that happens to share a word cannot
                // make an assertion pass. The database has ~2800 rows from six other sessions.
                .query(Query.of(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm.query(queryText).fields(List.of(fields))))
                        .filter(f -> f.term(t -> t.field("entityCode").value("MLCORPUS")))))),
                Map.class);

        return response.hits().hits().stream()
                .map(h -> String.valueOf(h.source().get("complaintNumber")))
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("the reindex succeeded and indexed the whole complaint table")
    void reindexSucceeded() {
        assumeThat(available).as("MySQL + Elasticsearch + a seeded corpus").isTrue();

        assertThat(status.success())
                .as("reindex reported: %s", status.message())
                .isTrue();
        assertThat(status.failed()).as("no bulk item may fail").isZero();
        assertThat(status.indexed())
                .as("the corpus is 26 of however many complaints exist")
                .isGreaterThanOrEqualTo(26);
    }

    @Test
    @DisplayName("all 26 seeded rows arrived in the index with their text intact")
    void allCorpusRowsArrivedWithText() throws IOException {
        assumeThat(available).isTrue();

        SearchResponse<Map> response = client.search(s -> s
                .index(builtIndex)
                .size(50)
                .query(q -> q.term(t -> t.field("entityCode").value("MLCORPUS"))), Map.class);

        assertThat(response.hits().total().value())
                .as("13 languages x (root + inflected) read out of MySQL and bulk-indexed")
                .isEqualTo(26);

        // The defect class fixed in ComplaintIndexingListener this session was a partial map passed to
        // a full IndexRequest, which erased subject and description while leaving the document present.
        // Counting documents would not have caught it; reading the fields back does.
        for (var hit : response.hits().hits()) {
            Map<?, ?> source = hit.source();
            assertThat((String) source.get("subject"))
                    .as("subject of %s survived the trip from MySQL", source.get("complaintNumber"))
                    .isNotBlank();
            assertThat((String) source.get("description"))
                    .as("description of %s survived the trip from MySQL", source.get("complaintNumber"))
                    .isNotBlank();
            assertThat((String) source.get("subject"))
                    .as("no charset damage in %s", source.get("complaintNumber"))
                    .doesNotContain("?");
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allPairs")
    @DisplayName("each language is retrievable from the index built out of the database")
    void eachLanguageIsRetrievableAfterReindex(MultilingualCorpus.LanguagePair pair) throws IOException {
        assumeThat(available).isTrue();

        // Base fields only: 9 of the 13 languages have nothing else, so this is the common floor that
        // every language must clear.
        assertThat(numbersFor(pair.rootTerm(), "subject", "description"))
                .as("%s: '%s' retrieves the seeded root document end to end", pair.language(),
                        pair.rootTerm())
                .contains(MultilingualCorpus.NUMBER_PREFIX + pair.language().toUpperCase() + "-ROOT");
    }

    @Test
    @DisplayName("the stemming asymmetry survives the real indexing path, not just a hand-built fixture")
    void stemmingAsymmetryHoldsOnTheReindexedCorpus() throws IOException {
        assumeThat(available).isTrue();

        String hiRoot = MultilingualCorpus.NUMBER_PREFIX + "HI-ROOT";
        String hiInfl = MultilingualCorpus.NUMBER_PREFIX + "HI-INFL";

        // The measurement the whole brief turns on, now against text that came out of MySQL.
        assertThat(numbersFor("शिकायत", "subject", "description"))
                .as("base cms_indic field does not stem, so the inflected document is unreachable")
                .contains(hiRoot)
                .doesNotContain(hiInfl);

        assertThat(numbersFor("शिकायत", "subject.hi", "description.hi"))
                .as("the hindi subfield stems शिकायतों to शिकायत and reaches both")
                .contains(hiRoot, hiInfl);
    }

    @Test
    @DisplayName("detectedLanguage was computed at index time for all 13 languages")
    void detectedLanguageWasAssignedByTheRowMapper() throws IOException {
        assumeThat(available).isTrue();

        // ComplaintRowMapper calls LanguageDetector during the reindex, so this asserts a production
        // behaviour rather than a value the seeder could have written. Note the deliberate lossiness:
        // Marathi resolves to hi and Assamese to bn, because neither is separable by script.
        for (MultilingualCorpus.LanguagePair pair : MultilingualCorpus.pairs()) {
            String expected = switch (pair.language()) {
                case "mr" -> "hi";
                case "as" -> "bn";
                default -> pair.language();
            };

            SearchResponse<Map> response = client.search(s -> s
                    .index(builtIndex)
                    .size(5)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("entityCode").value("MLCORPUS")))
                            .filter(f -> f.term(t -> t.field("complaintNumber")
                                    .value(MultilingualCorpus.NUMBER_PREFIX
                                            + pair.language().toUpperCase() + "-ROOT"))))),
                    Map.class);

            assertThat(response.hits().hits())
                    .as("%s root document is present", pair.language())
                    .hasSize(1);
            assertThat((String) response.hits().hits().get(0).source().get("detectedLanguage"))
                    .as("%s detected at index time", pair.language())
                    .isEqualTo(expected);
        }
    }
}
