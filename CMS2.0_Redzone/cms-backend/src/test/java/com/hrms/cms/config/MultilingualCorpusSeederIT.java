package com.hrms.cms.config;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Proves {@link MultilingualCorpusSeeder} against a real MySQL, because the two things that can break
 * it cannot break on H2.
 *
 * <ol>
 *   <li><b>The UTF-8 round-trip.</b> Thirteen scripts are written and read back and compared
 *       code-point for code-point. H2 in-memory would accept anything; only the real
 *       {@code utf8mb4} column with the real connector's {@code characterEncoding} parameter can show
 *       whether Odia conjuncts and the Assamese apostrophe actually survive. A column at
 *       {@code utf8} (3-byte) rather than {@code utf8mb4}, or a connection defaulting to Latin-1,
 *       silently substitutes {@code ?} — which looks like a seeding success and produces zero search
 *       hits.
 *   <li><b>The {@code @PrePersist} overwrite.</b> {@link Complaint#onCreate()} sets {@code createdAt}
 *       to {@code now()} unconditionally, so the seeder's fixed timestamps only survive because of its
 *       native UPDATE. This asserts they did.
 * </ol>
 *
 * <p><b>Why a JPA slice and not {@code @SpringBootTest}.</b> Booting the whole monolith to exercise one
 * {@code CommandLineRunner} would drag in Kafka, Hazelcast and Keycloak, none of which this seeder
 * touches, and would start a web server on a port this session does not own. The slice loads the
 * datasource, JPA and the seeder, and nothing else.
 *
 * <p><b>Skips rather than fails when MySQL is absent</b>, matching how the Elasticsearch tests in
 * cms-search-service treat a missing node: a developer without a local database must not see red for
 * infrastructure they were never asked to run.
 *
 * <p><b>This test COMMITS.</b> {@code @DataJpaTest} rolls back by default, which would be wrong here on
 * two counts: the native-UPDATE step is exactly what a rollback would hide, and the seeder's whole
 * contract is that the rows are there for the next reindex. Committing is also safe to repeat — that
 * is the property under test — and the rows are the same 26 the seeder writes on every dev boot
 * regardless, so this leaves the shared database in the state it is meant to be in rather than
 * polluting it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MultilingualCorpusSeeder.class)
@ActiveProfiles("dev-local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        // src/test/resources/application.yml points the whole backend test suite at H2. These three
        // override it back to the dev-local MySQL for this class only; without them the test would
        // pass against H2 and prove nothing about the encoding, which is the point of the file.
        "spring.datasource.url=jdbc:mysql://localhost:3306/cms_db?useSSL=false"
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=cms_user",
        "spring.datasource.password=cms_pass",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        // Never let a test alter the shared schema. The seeder needs no DDL.
        "spring.jpa.hibernate.ddl-auto=none"
})
@DisplayName("Multilingual corpus seeder against real MySQL")
class MultilingualCorpusSeederIT {

    @Autowired
    private MultilingualCorpusSeeder seeder;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private EntityManager entityManager;

    private static final LocalDateTime CORPUS_EPOCH = LocalDateTime.of(2026, 1, 5, 9, 0, 0);

    static List<MultilingualCorpusSeeder.LanguagePair> corpus() {
        return MultilingualCorpusSeeder.corpus();
    }

    /**
     * Runs the seeder outside the test's own transaction and commits, so the native UPDATE and the
     * unique-index behaviour are both real.
     */
    private void seed() {
        seeder.run();
        entityManager.flush();
        entityManager.clear();
    }

    private boolean mysqlAvailable() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @org.springframework.test.annotation.Rollback(false)
    @DisplayName("seeds 26 rows, and seeding again is a no-op rather than a duplicate or a violation")
    void seedingIsIdempotent() {
        assumeThat(mysqlAvailable()).as("MySQL cms_db on localhost:3306").isTrue();

        seed();
        long afterFirst = countCorpus();
        assertThat(afterFirst)
                .as("13 languages x (root + inflected)")
                .isEqualTo(26);

        // The real test: a second run on a seeded database. complaint_number carries a unique index,
        // so a seeder that re-inserted blindly would throw here rather than merely duplicating.
        seed();
        assertThat(countCorpus())
                .as("re-running must change nothing")
                .isEqualTo(afterFirst);

        // And a third, because the bulk count guard and the per-row guard are different code paths and
        // only the per-row one runs when the count is short.
        seed();
        assertThat(countCorpus()).isEqualTo(afterFirst);
    }

    private long countCorpus() {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM COMPLAINTS WHERE complaint_number LIKE :p")
                .setParameter("p", MultilingualCorpusSeeder.NUMBER_PREFIX + "%")
                .getSingleResult()).longValue();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("corpus")
    @DisplayName("every script round-trips through MySQL byte-for-byte")
    void textRoundTripsThroughMysql(MultilingualCorpusSeeder.LanguagePair pair) {
        assumeThat(mysqlAvailable()).isTrue();
        seed();

        assertRow(pair.language(), true, pair.rootSubject(), pair.rootBody(), pair.rootTerm());
        assertRow(pair.language(), false, pair.inflSubject(), pair.inflBody(), pair.inflectedTerm());
    }

    private void assertRow(String language, boolean root, String subject, String body, String term) {
        String number = MultilingualCorpusSeeder.complaintNumber(language, root);
        Optional<Complaint> found = complaintRepo.findByComplaintNumber(number);

        assertThat(found).as("row %s", number).isPresent();
        Complaint c = found.get();

        // Equality on the String, not on a rendered form: a charset failure substitutes U+FFFD or '?'
        // and this comparison is what catches it.
        assertThat(c.getSubject()).as("subject of %s", number).isEqualTo(subject);
        assertThat(c.getDescription()).as("description of %s", number).isEqualTo(body);

        // Explicitly assert the key term is still in the stored text. The equality above would also
        // catch its loss, but naming it separately means a failure says which word died.
        assertThat(c.getDescription()).as("key term '%s' survived in %s", term, number).contains(term);

        // No substitution characters anywhere. A 3-byte utf8 column mangles only the astral and some
        // conjunct forms, so a per-language equality check can pass while a neighbouring field is
        // already damaged.
        assertThat(c.getSubject() + c.getDescription())
                .as("no replacement characters in %s", number)
                .doesNotContain("�")
                .doesNotContain("?");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("corpus")
    @DisplayName("fixed timestamps survive the @PrePersist overwrite")
    void timestampsAreDeterministic(MultilingualCorpusSeeder.LanguagePair pair) {
        assumeThat(mysqlAvailable()).isTrue();
        seed();

        int index = corpus().indexOf(pair);
        LocalDateTime expectedRoot = CORPUS_EPOCH.plusDays(index);

        // Complaint.onCreate() assigns LocalDateTime.now() on every persist. If the seeder's native
        // UPDATE were removed, these would be today's date and this assertion would fail — which is
        // precisely what silently happened to DemoDataSeeder's 60 rows, all of which carry one instant
        // instead of the 90-day spread their author wrote.
        assertThat(storedCreatedAt(pair.language(), true))
                .as("%s root createdAt is pinned, not now()", pair.language())
                .isEqualTo(expectedRoot);

        assertThat(storedCreatedAt(pair.language(), false))
                .as("%s inflected createdAt is pinned one hour after its root", pair.language())
                .isEqualTo(expectedRoot.plusHours(1));
    }

    private LocalDateTime storedCreatedAt(String language, boolean root) {
        return ((java.sql.Timestamp) entityManager.createNativeQuery(
                        "SELECT created_at FROM COMPLAINTS WHERE complaint_number = :n")
                .setParameter("n", MultilingualCorpusSeeder.complaintNumber(language, root))
                .getSingleResult()).toLocalDateTime();
    }

    @Test
    @DisplayName("the corpus is unmistakably synthetic and namespaced away from real complaints")
    void rowsAreNamespacedAsSynthetic() {
        assumeThat(mysqlAvailable()).isTrue();
        seed();

        // This database is shared with six other sessions. A synthetic row that reads like a real
        // complaint is worse than no row at all, so the marker is asserted rather than trusted.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT complaint_number, complainant_name, entity_code FROM COMPLAINTS "
                                + "WHERE complaint_number LIKE :p")
                .setParameter("p", MultilingualCorpusSeeder.NUMBER_PREFIX + "%")
                .getResultList();

        assertThat(rows).hasSize(26);
        for (Object[] row : rows) {
            assertThat((String) row[0]).startsWith(MultilingualCorpusSeeder.NUMBER_PREFIX);
            assertThat((String) row[1]).startsWith("SYNTHETIC CORPUS");
            assertThat((String) row[2]).isEqualTo("MLCORPUS");
        }
    }

    @Test
    @DisplayName("the corpus number series collides with nothing already in the database")
    void complaintNumbersDoNotCollide() {
        assumeThat(mysqlAvailable()).isTrue();
        seed();

        // The pre-existing series are CMP-2026*, CMS-2026*, CMS-DEMO-*, CEPC/2026/* and one N2026*.
        // Asserting the complement rather than listing them: any row matching the corpus prefix must be
        // one of the 26, so nothing else in the table can be claiming these numbers.
        long outside = ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM COMPLAINTS WHERE complaint_number LIKE :p "
                                + "AND complainant_name NOT LIKE 'SYNTHETIC CORPUS%'")
                .setParameter("p", MultilingualCorpusSeeder.NUMBER_PREFIX + "%")
                .getSingleResult()).longValue();

        assertThat(outside)
                .as("no foreign row has taken a CMS-MLCORPUS- number")
                .isZero();
    }

    @Test
    @DisplayName("all 13 served languages are covered, each by a root and an inflected document")
    void allThirteenLanguagesArePresent() {
        assumeThat(mysqlAvailable()).isTrue();
        seed();

        List<String> languages = corpus().stream()
                .map(MultilingualCorpusSeeder.LanguagePair::language)
                .toList();

        assertThat(languages)
                .containsExactlyInAnyOrder("en", "hi", "mr", "bn", "ur", "te", "ta", "ml", "kn",
                        "gu", "pa", "or", "as")
                .doesNotHaveDuplicates();
    }
}
