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
 * {@link AssistanceRailTranslationSeeder} serves all THIRTEEN locales, with placeholders intact.
 *
 * <h2>Why this test is worth its weight</h2>
 * Every failure mode in this seeder is SILENT. The rail component treats a key echoed back unresolved
 * as "localisation did not happen" and prints the server's English, and discards any resolved string
 * still carrying a {@code {{...}}} in favour of the same English. So a locale never seeded, a key
 * misspelled with an underscore, and a {@code {{placeholder}}} lost in translation all look identical
 * from the outside: a panel that is merely "not translated yet". No error, no empty row, nothing in a
 * log. That is exactly how {@code or} and {@code as} came to be missing while the seeder looked
 * finished, and how a previous session came to believe a locale was served when it was not.
 *
 * <h2>It drives {@code run()}, not the private maps</h2>
 * Reflecting into {@code odia()} would prove the map exists while a missing
 * {@code seedLocale("or", odia())} line shipped anyway — which is a map nobody ever reads. So the test
 * runs the real {@code run()} against mock repositories and asserts on what was actually handed to
 * {@code translationRepo.save}. The locale list is taken from {@code LanguageTranslationService}'s
 * {@code LANGUAGE_NAMES}, restated here because that field is private; a fourteenth product locale
 * therefore needs this list updated, which is the point — the alternative is a hardcoded count of 13
 * that stays green while a new locale goes unserved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceRailTranslationSeeder: 13-locale coverage")
class AssistanceRailTranslationCoverageTest {

    /**
     * The locales this product serves, per {@code LanguageTranslationService.LANGUAGE_NAMES}.
     * {@code en} is included: it is seeded as each key's default value rather than by a
     * {@code seedLocale} call, and the seeder writes a real {@code en} row for it.
     */
    private static final List<String> PRODUCT_LOCALES =
            List.of("en", "hi", "mr", "bn", "ur", "te", "ta", "ml", "kn", "gu", "pa", "or", "as");

    /** Matches the interpolation slots the rail's i18n values carry, e.g. {@code {{section}}}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    @Mock private TranslationKeyRepository keyRepo;
    @Mock private TranslationRepository translationRepo;

    /** Every row the seeder wrote, as locale -> (key code -> value). */
    private final Map<String, Map<String, String>> seeded = new LinkedHashMap<>();

    @BeforeEach
    void seedIntoMemory() {
        Map<String, TranslationKey> keysByCode = new LinkedHashMap<>();
        AtomicLong ids = new AtomicLong(1);

        // A clean database: no key exists yet, so run() takes the insert path for every one.
        when(keyRepo.existsByCode(anyString())).thenReturn(false);
        when(keyRepo.save(any(TranslationKey.class))).thenAnswer(inv -> {
            TranslationKey key = inv.getArgument(0);
            key.setId(ids.getAndIncrement());
            keysByCode.put(key.getCode(), key);
            return key;
        });
        when(keyRepo.findByCode(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(keysByCode.get(inv.getArgument(0, String.class))));
        // Insert-if-absent: nothing is present, so every putTranslation writes.
        when(translationRepo.findByKeyIdAndLocale(anyLong(), anyString())).thenReturn(List.of());
        when(translationRepo.save(any(Translation.class))).thenAnswer(inv -> {
            Translation t = inv.getArgument(0);
            seeded.computeIfAbsent(t.getLocale(), l -> new LinkedHashMap<>())
                    .put(t.getTranslationKey().getCode(), t.getValue());
            return t;
        });

        new AssistanceRailTranslationSeeder(keyRepo, translationRepo).run();
    }

    /** The {@code en} rows, which are the contract every other locale is measured against. */
    private Map<String, String> englishRows() {
        return seeded.getOrDefault("en", Map.of());
    }

    @Nested
    @DisplayName("locale coverage")
    class LocaleCoverage {

        @Test
        @DisplayName("every product locale is seeded — not eleven of thirteen")
        void everyProductLocaleIsSeeded() {
            assertThat(seeded.keySet()).containsAll(PRODUCT_LOCALES);
        }

        /**
         * The specific regression. {@code or} and {@code as} were absent while the seeder read as
         * complete, so officers in those locales were served English with no indication of it.
         */
        @Test
        @DisplayName("Odia and Assamese are present — the two that were missing")
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
         * A locale cannot be served by copying its neighbour's script.
         *
         * <p>Assamese and Bengali share the Bengali-Assamese block, so {@code seedLocale("as",
         * bengali())} would have satisfied every assertion above while shipping text that reads as
         * foreign to an Assamese officer — Bengali {@code র} where Assamese writes {@code ৰ}, the
         * {@code -উন} imperative where Assamese takes {@code -ক}. Only an inequality test catches it.
         */
        @Test
        @DisplayName("Assamese is not a copy of Bengali")
        void assameseIsNotBengali() {
            assertThat(seeded.get("as"))
                    .as("as must be genuine Assamese, not bn reseeded under a second locale code")
                    .isNotEqualTo(seeded.get("bn"));
        }

        /** Odia has its own block (U+0B00–U+0B7F), so a wrong script here is a visible mistake. */
        @Test
        @DisplayName("Odia is written in Oriya script and Assamese in Bengali-Assamese script")
        void theNewLocalesUseTheirOwnScripts() {
            assertThat(seeded.get("or").get("assistance.panel_title"))
                    .as("Odia panel title must be Oriya script, U+0B00-U+0B7F")
                    .matches(".*[\\u0B00-\\u0B7F].*");
            assertThat(seeded.get("as").get("assistance.panel_title"))
                    .as("Assamese panel title must be Bengali-Assamese script, U+0980-U+09FF")
                    .matches(".*[\\u0980-\\u09FF].*");
        }
    }

    @Nested
    @DisplayName("placeholders survive translation")
    class Placeholders {

        /**
         * The strictest assertion in the file, and the cheapest insurance.
         *
         * <p>{@code translate} substitutes only the {@code {{name}}} slots it is handed and leaves the
         * rest standing, and the component then throws away any string still holding one. So a
         * {@code {{sample}}} translated to {@code {{नमूना}}}, or a brace lost to a copy-paste, reverts
         * that one locale to English and looks like a locale nobody got round to. Compared per key
         * against the English set, so adding a key with a new placeholder extends the check for free.
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
                        continue; // Absence is the other test's finding, not this one's.
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
         * Guards the placeholder check itself against being vacuously true.
         *
         * <p>If the English map ever stopped carrying {@code {{...}}} slots — a refactor to resolved
         * server prose, say — the test above would compare empty sets to empty sets and pass while
         * proving nothing. These are the names the DTO and {@code AssistanceRailService}'s
         * {@code PARAM_*} constants declare.
         */
        @Test
        @DisplayName("the English map really does carry the six documented placeholders")
        void englishCarriesTheDocumentedPlaceholders() {
            Set<String> all = new LinkedHashSet<>();
            englishRows().values().forEach(v -> all.addAll(placeholdersIn(v)));
            assertThat(all).containsExactlyInAnyOrder(
                    "section", "age", "count", "clause", "days", "sample");
        }

        /**
         * The hyphens are load-bearing: the component's hardcoded {@code KIND_LABEL_KEYS} asks for
         * {@code assistance.signal.last-section}, so an underscored key here misses every lookup and
         * the whole panel reverts to English while looking untranslated. Asserted on the keys the
         * seeder actually wrote, because that is where a normalising edit would land.
         */
        @Test
        @DisplayName("signal keys keep the hyphenated kind, never an underscored one")
        void signalKeysStayHyphenated() {
            assertThat(englishRows().keySet())
                    .contains("assistance.signal.unsaved-draft",
                            "assistance.signal.last-section",
                            "assistance.signal.last-viewed",
                            "assistance.signal.complainant-history",
                            "assistance.signal.entity-clause-precedent",
                            "assistance.signal.category-closure-time");

            assertThat(seeded.values().stream()
                    .flatMap(rows -> rows.keySet().stream())
                    .filter(code -> code.startsWith("assistance.signal.") && code.contains("_"))
                    .toList())
                    .as("an underscored signal key misses the client's lookup silently")
                    .isEmpty();
        }
    }

    /**
     * The two states that must never read alike, per the seeder's own header: "nothing to report" is
     * the rail having run and found nothing, and "failed" is the request not completing, so nothing is
     * known either way. Wording them identically in a locale reintroduces the defect the panel exists
     * to remove, for the officers who read that locale — including the two locales added here.
     */
    @Test
    @DisplayName("'nothing to report' and 'failed' are distinct in every locale")
    void theTwoQuietStatesNeverReadAlike() {
        List<String> collisions = new ArrayList<>();
        for (String locale : PRODUCT_LOCALES) {
            Map<String, String> rows = seeded.getOrDefault(locale, Map.of());
            String nothing = rows.get("assistance.nothing_to_report");
            String failed = rows.get("assistance.error_failed");
            if (nothing != null && nothing.equals(failed)) {
                collisions.add(locale);
            }
        }
        assertThat(collisions).isEmpty();
    }

    private static Set<String> placeholdersIn(String value) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
