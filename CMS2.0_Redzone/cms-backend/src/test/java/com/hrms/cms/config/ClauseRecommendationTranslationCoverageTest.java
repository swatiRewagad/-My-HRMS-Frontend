package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ClauseRecommendationTranslationSeeder} serves all THIRTEEN locales, with placeholders intact.
 *
 * <h2>Why a seeder needs a test at all</h2>
 * Every failure mode here is SILENT, exactly as in {@code AssistanceRailTranslationCoverageTest}: an
 * unresolved key is echoed back and the component prints the server's English, so a locale never
 * seeded, a key misspelled with an underscore, and a {@code {{placeholder}}} lost in translation all
 * present identically as "not translated yet" — no error, no empty row, nothing logged. That is how the
 * rail shipped missing {@code or} and {@code as} while its seeder read as finished.
 *
 * <h2>What is specifically at risk in THIS seeder</h2>
 * Its strings are not decoration. {@code used_in} carries the DENOMINATOR — "cited in 12 of 40
 * comparable closures" — and {@code advisory} carries the sentence that stops an ordering being read as
 * an instruction. If {@code advisory} silently reverts to English for an Odia officer, the ranking is
 * still reordering their legal clause picker with no caveat they can read. So the placeholder check
 * here is not pedantry about braces; {@code {{count}}} and {@code {{total}}} losing their pairing is
 * how a 12-of-40 becomes a bare 12.
 *
 * <p>It drives the real {@code run()} against mock repositories rather than reflecting into the private
 * maps, because a map with no matching {@code seedLocale} line is a map nobody reads.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClauseRecommendationTranslationSeeder: 13-locale coverage")
class ClauseRecommendationTranslationCoverageTest {

    /** The locales this product serves, per {@code LanguageTranslationService.LANGUAGE_NAMES}. */
    private static final List<String> PRODUCT_LOCALES =
            List.of("en", "hi", "mr", "bn", "ur", "te", "ta", "ml", "kn", "gu", "pa", "or", "as");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    @Mock private TranslationKeyRepository keyRepo;
    @Mock private TranslationRepository translationRepo;

    /** Every row the seeder wrote, as locale -> (key code -> value). */
    private final Map<String, Map<String, String>> seeded = new LinkedHashMap<>();

    @BeforeEach
    void seedIntoMemory() {
        Map<String, TranslationKey> keysByCode = new LinkedHashMap<>();
        AtomicLong ids = new AtomicLong(1);

        when(keyRepo.existsByCode(anyString())).thenReturn(false);
        when(keyRepo.save(any(TranslationKey.class))).thenAnswer(inv -> {
            TranslationKey key = inv.getArgument(0);
            key.setId(ids.getAndIncrement());
            keysByCode.put(key.getCode(), key);
            return key;
        });
        when(keyRepo.findByCode(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(keysByCode.get(inv.getArgument(0, String.class))));
        when(translationRepo.findByKeyIdAndLocale(anyLong(), anyString())).thenReturn(List.of());
        when(translationRepo.save(any(Translation.class))).thenAnswer(inv -> {
            Translation t = inv.getArgument(0);
            seeded.computeIfAbsent(t.getLocale(), l -> new LinkedHashMap<>())
                    .put(t.getTranslationKey().getCode(), t.getValue());
            return t;
        });

        new ClauseRecommendationTranslationSeeder(keyRepo, translationRepo).run();
    }

    private Map<String, String> englishRows() {
        return seeded.getOrDefault("en", Map.of());
    }

    private static Set<String> placeholdersIn(String value) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Nested
    @DisplayName("locale coverage")
    class LocaleCoverage {

        @Test
        @DisplayName("every product locale is seeded — not eleven of thirteen")
        void everyProductLocaleIsSeeded() {
            assertThat(seeded.keySet()).containsAll(PRODUCT_LOCALES);
        }

        /** The two the rail's seeder originally shipped without. */
        @Test
        @DisplayName("Odia and Assamese are present")
        void odiaAndAssameseArePresent() {
            assertThat(seeded).containsKeys("or", "as");
        }

        @Test
        @DisplayName("no locale outside the product's thirteen was seeded")
        void noStrayLocaleWasSeeded() {
            assertThat(PRODUCT_LOCALES).containsAll(seeded.keySet());
        }

        @Test
        @DisplayName("every locale carries EVERY key the English map defines")
        void everyLocaleCarriesEveryKey() {
            Set<String> englishKeys = englishRows().keySet();
            assertThat(englishKeys).isNotEmpty();

            Map<String, Set<String>> missing = new LinkedHashMap<>();
            for (String locale : PRODUCT_LOCALES) {
                Set<String> absent = new LinkedHashSet<>(englishKeys);
                absent.removeAll(seeded.getOrDefault(locale, Map.of()).keySet());
                if (!absent.isEmpty()) {
                    missing.put(locale, absent);
                }
            }
            assertThat(missing)
                    .as("locale -> keys it is missing; each absent key silently renders English")
                    .isEmpty();
        }

        /**
         * Assamese and Bengali share the Bengali-Assamese block, so {@code seedLocale("as", bengali())}
         * would satisfy every assertion above while shipping text that reads as foreign — Bengali
         * {@code র} where Assamese writes {@code ৰ}, the {@code -উন} imperative where Assamese takes
         * {@code -ক}. Only an inequality test catches it.
         */
        @Test
        @DisplayName("Assamese is not a copy of Bengali")
        void assameseIsNotBengali() {
            assertThat(seeded.get("as"))
                    .as("as must be genuine Assamese, not bn reseeded under a second locale code")
                    .isNotEqualTo(seeded.get("bn"));
        }

        @Test
        @DisplayName("no two locales were seeded with identical maps")
        void everyLocaleIsDistinct() {
            List<String> duplicates = new ArrayList<>();
            for (int i = 0; i < PRODUCT_LOCALES.size(); i++) {
                for (int j = i + 1; j < PRODUCT_LOCALES.size(); j++) {
                    String a = PRODUCT_LOCALES.get(i);
                    String b = PRODUCT_LOCALES.get(j);
                    if (seeded.containsKey(a) && seeded.get(a).equals(seeded.get(b))) {
                        duplicates.add(a + " == " + b);
                    }
                }
            }
            assertThat(duplicates)
                    .as("an identical map means one locale was seeded with another's text")
                    .isEmpty();
        }

        @Test
        @DisplayName("Odia uses Oriya script and Assamese the Bengali-Assamese block")
        void theScriptsAreCorrect() {
            assertThat(seeded.get("or").get("clause-recommendation.most_cited"))
                    .as("Odia must be Oriya script, U+0B00-U+0B7F")
                    .matches(".*[\\u0B00-\\u0B7F].*");
            assertThat(seeded.get("as").get("clause-recommendation.most_cited"))
                    .as("Assamese must be Bengali-Assamese script, U+0980-U+09FF")
                    .matches(".*[\\u0980-\\u09FF].*");
        }

        /**
         * Punjabi is the locale a previous session wrongly recorded as served while it was not seeded at
         * all, so it is asserted by script rather than by mere presence.
         */
        @Test
        @DisplayName("Punjabi is real Gurmukhi, not English left in place")
        void punjabiIsGurmukhi() {
            assertThat(seeded.get("pa").get("clause-recommendation.most_cited"))
                    .as("Gurmukhi is U+0A00-U+0A7F; English here is the defect this asserts against")
                    .matches(".*[\\u0A00-\\u0A7F].*");
        }
    }

    @Nested
    @DisplayName("the keys themselves")
    class Keys {

        /**
         * The hyphen in {@code clause-recommendation} is load-bearing. The frontend asks for
         * {@code clause-recommendation.used_in} literally; a normalising edit to
         * {@code clause_recommendation.used_in} would miss every lookup and revert the annotations to
         * English while looking merely untranslated.
         */
        @Test
        @DisplayName("the namespace keeps its hyphen and the leaf keeps its underscore")
        void namespaceStaysHyphenated() {
            assertThat(englishRows().keySet()).contains(
                    "clause-recommendation.heading",
                    "clause-recommendation.used_in",
                    "clause-recommendation.used_in_aria",
                    "clause-recommendation.most_cited",
                    "clause-recommendation.advisory",
                    "clause-recommendation.no_history");

            assertThat(englishRows().keySet())
                    .as("clause_recommendation.* with an underscored namespace resolves to nothing")
                    .noneMatch(code -> code.startsWith("clause_recommendation"));
        }

        /**
         * The caveat must exist in every locale, because the ordering it qualifies applies in every
         * locale. An officer reading an Odia screen is being nudged by the same ranking as one reading
         * English, so the sentence saying "this is history, not advice" cannot be English-only.
         */
        @Test
        @DisplayName("the advisory caveat is served in every locale, never English-only")
        void theAdvisoryIsTranslatedEverywhere() {
            List<String> untranslated = new ArrayList<>();
            String english = englishRows().get("clause-recommendation.advisory");
            assertThat(english).isNotBlank();

            for (String locale : PRODUCT_LOCALES) {
                if ("en".equals(locale)) {
                    continue;
                }
                String value = seeded.getOrDefault(locale, Map.of())
                        .get("clause-recommendation.advisory");
                if (value == null || value.equals(english)) {
                    untranslated.add(locale);
                }
            }
            assertThat(untranslated)
                    .as("a locale left on the English caveat is a nudge with no readable warning")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("placeholders survive translation")
    class Placeholders {

        /**
         * The strictest assertion in the file. {@code translate} substitutes only the {@code {{name}}}
         * slots it is handed and leaves the rest standing, and the component discards any string still
         * holding one. So {@code {{count}}} translated to {@code {{गणना}}}, or a brace lost to a
         * copy-paste, reverts that locale to English and looks like a locale nobody got round to.
         */
        @Test
        @DisplayName("each key's placeholder set is byte-identical to English in every locale")
        void placeholderSetsMatchEnglishExactly() {
            List<String> mismatches = new ArrayList<>();

            englishRows().forEach((code, englishValue) -> {
                Set<String> expected = placeholdersIn(englishValue);
                for (String locale : PRODUCT_LOCALES) {
                    String value = seeded.getOrDefault(locale, Map.of()).get(code);
                    if (value == null) {
                        continue; // Absence is the other nest's finding.
                    }
                    Set<String> actual = placeholdersIn(value);
                    if (!actual.equals(expected)) {
                        mismatches.add(locale + "/" + code + " expected " + expected + " but had " + actual);
                    }
                }
            });

            assertThat(mismatches)
                    .as("a placeholder lost or renamed reverts that locale to English, silently")
                    .isEmpty();
        }

        /**
         * Guards the check above against being vacuously true: were the English map ever to stop
         * carrying slots, it would compare empty sets and pass while proving nothing.
         */
        @Test
        @DisplayName("the English map really does carry count and total")
        void englishCarriesTheDocumentedPlaceholders() {
            Set<String> all = new LinkedHashSet<>();
            englishRows().values().forEach(v -> all.addAll(placeholdersIn(v)));
            assertThat(all)
                    .as("count and total are the annotation's numerator and DENOMINATOR")
                    .containsExactlyInAnyOrder("count", "total");
        }

        /**
         * The denominator is the whole point. "cited in 12" is a number an officer cannot weigh; "cited
         * in 12 of 40" is. So the keys that print a count must print both slots, in every locale — the
         * specific regression being a translator who kept {@code {{count}}} and dropped {@code {{total}}}
         * as redundant.
         */
        @Test
        @DisplayName("every count-bearing key carries BOTH count and total in every locale")
        void countKeysAlwaysCarryTheirDenominator() {
            List<String> offenders = new ArrayList<>();
            for (String code : List.of("clause-recommendation.used_in",
                    "clause-recommendation.used_in_aria")) {
                for (String locale : PRODUCT_LOCALES) {
                    String value = seeded.getOrDefault(locale, Map.of()).get(code);
                    if (value == null) {
                        continue;
                    }
                    if (!placeholdersIn(value).containsAll(Set.of("count", "total"))) {
                        offenders.add(locale + "/" + code);
                    }
                }
            }
            assertThat(offenders)
                    .as("a count without its denominator is a number nobody can weigh")
                    .isEmpty();
        }
    }
}
