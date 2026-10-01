package com.hrms.cms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the one property of {@link MultilingualCorpusSeeder} that must never regress: it cannot run
 * outside {@code dev-local}.
 *
 * <p><b>Why this is a separate, context-free test.</b> The seeder writes 26 invented complaints about
 * invented citizens into the complaint register. In {@code prod} that is not a bug to be fixed later —
 * it is synthetic records inside a statutory register, mixed in with real citizens' grievances. The
 * guard is one annotation, and one annotation is exactly the kind of thing a later refactor deletes
 * while moving a class or converting it to a conditional bean. A unit test that reads the annotation
 * back costs nothing and fails loudly if it goes.
 *
 * <p>No Spring context at all, deliberately: reflection over the class literal is immune to the
 * profile the surrounding suite happens to run under, whereas a context test would only prove the
 * absence of a bean under one specific configuration.
 *
 * <p>Mutation-checked: removing {@code @Profile("dev-local")} from the seeder, or widening it to
 * {@code @Profile("!prod")} as {@link DemoDataSeeder} uses, fails the first assertion below.
 */
@DisplayName("Multilingual corpus seeder production-safety gate")
class MultilingualCorpusSeederProfileTest {

    @Test
    @DisplayName("is gated on the dev-local profile exactly, not on a negation and not on a flag")
    void seederIsRestrictedToDevLocal() {
        Profile profile = MultilingualCorpusSeeder.class.getAnnotation(Profile.class);

        assertThat(profile)
                .as("without @Profile this seeder would write synthetic complaints in every environment")
                .isNotNull();

        // "dev-local" and nothing else. A negation like "!prod" would still be active under
        // "openshift", and any profile this seeder does not explicitly name is one where invented
        // complaints must not appear.
        assertThat(profile.value())
                .as("must name dev-local positively; a negated profile leaks into openshift")
                .containsExactly("dev-local");
    }

    @Test
    @DisplayName("runs at the @Order reserved for it, so it cannot collide with another seeder")
    void seederRunsAtItsReservedOrder() {
        Order order = MultilingualCorpusSeeder.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();

        // 45 is reserved for this work. 42/43/44 are RbioMeetingTransferTransitionSeeder,
        // RbioMeetingTranslationSeeder and RbioForwardTranslationSeeder respectively; taking one of
        // those numbers would make two CommandLineRunners' relative order undefined.
        assertThat(order.value())
                .as("@Order(45) is this seeder's reservation on a tree six sessions share")
                .isEqualTo(45);
    }

    @Test
    @DisplayName("the corpus itself is 13 languages, each with a distinct root and inflected form")
    void corpusIsWellFormed() {
        var pairs = MultilingualCorpusSeeder.corpus();

        assertThat(pairs).hasSize(13);

        // Every pair must actually differ in its key term, or the stemming asymmetry it exists to
        // demonstrate is untestable: querying the root would trivially match both documents.
        for (var pair : pairs) {
            assertThat(pair.rootTerm())
                    .as("%s root and inflected terms must differ", pair.language())
                    .isNotEqualTo(pair.inflectedTerm());

            assertThat(pair.rootBody())
                    .as("%s root body must contain the root term", pair.language())
                    .contains(pair.rootTerm());

            assertThat(pair.inflBody())
                    .as("%s inflected body must contain the inflected term", pair.language())
                    .contains(pair.inflectedTerm());

            // The root term must NOT appear in the inflected document as a bare substring of the
            // description, or the base-field "does not stem" assertion would pass for the wrong
            // reason. Checked here rather than in the search test because it is a property of the
            // text, and a text edit is what would break it.
            assertThat(pair.inflBody())
                    .as("%s inflected body must not also contain the bare root term", pair.language())
                    .doesNotContain(" " + pair.rootTerm() + " ");
        }

        assertThat(pairs.stream().map(MultilingualCorpusSeeder.LanguagePair::language).toList())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("en", "hi", "mr", "bn", "ur", "te", "ta", "ml", "kn",
                        "gu", "pa", "or", "as");
    }

    @Test
    @DisplayName("complaint numbers are namespaced and unique across the 26 documents")
    void complaintNumbersAreUniqueAndNamespaced() {
        var numbers = MultilingualCorpusSeeder.corpus().stream()
                .flatMap(p -> java.util.stream.Stream.of(
                        MultilingualCorpusSeeder.complaintNumber(p.language(), true),
                        MultilingualCorpusSeeder.complaintNumber(p.language(), false)))
                .toList();

        assertThat(numbers).hasSize(26).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(n ->
                assertThat(n).startsWith(MultilingualCorpusSeeder.NUMBER_PREFIX));
    }
}
