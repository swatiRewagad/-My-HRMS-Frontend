package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import com.hrms.cms.service.AssistanceQueueService;
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
 * {@link AssistanceQueueTranslationSeeder} serves all THIRTEEN locales, with placeholders intact.
 *
 * <h2>Why this test is worth its weight</h2>
 * Every failure mode in that seeder is SILENT, exactly as in the rail's. The client treats a key echoed
 * back unresolved as "localisation did not happen" and prints the server's English, and discards any
 * resolved string still carrying a {@code {{...}}} in favour of the same English. So a locale never
 * seeded, a key misspelled with an underscore, and a placeholder lost in translation all look identical
 * from outside: a panel that is merely "not translated yet". No error, no empty row, nothing in a log.
 * The compiler cannot help either, because no Java code reads these strings.
 *
 * <h2>It drives {@code run()}, not the private maps</h2>
 * Reflecting into {@code odia()} would prove the map exists while a missing
 * {@code seedLocale("or", odia())} line shipped anyway — a map nobody reads. So this runs the real
 * {@code run()} against mock repositories and asserts on what was handed to
 * {@code translationRepo.save}, which is the only evidence a locale was actually served.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceQueueTranslationSeeder: 13-locale coverage")
class AssistanceQueueTranslationCoverageTest {

    /**
     * The locales this product serves, per {@code LanguageTranslationService.LANGUAGE_NAMES}.
     * {@code en} is included: it is seeded as each key's default value rather than by a
     * {@code seedLocale} call, and the seeder writes a real {@code en} row for it.
     */
    private static final List<String> PRODUCT_LOCALES =
            List.of("en", "hi", "mr", "bn", "ur", "te", "ta", "ml", "kn", "gu", "pa", "or", "as");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    /** The signal key the client looks up, built from the service's own kind constant. */
    private static final String SIGNAL_KEY =
            "assistance.queue.signal." + AssistanceQueueService.KIND_DEADLINE_TRIAGE;

    @Mock private TranslationKeyRepository keyRepo;
    @Mock private TranslationRepository translationRepo;

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
        when(translationRepo.findByKeyIdAndLocale(anyLong(), anyString())).thenReturn(List.of());
        when(translationRepo.save(any(Translation.class))).thenAnswer(inv -> {
            Translation t = inv.getArgument(0);
            seeded.computeIfAbsent(t.getLocale(), l -> new LinkedHashMap<>())
                    .put(t.getTranslationKey().getCode(), t.getValue());
            return t;
        });

        new AssistanceQueueTranslationSeeder(keyRepo, translationRepo).run();
    }

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
         * bengali())} would satisfy every assertion above while shipping text that reads as foreign to
         * an Assamese officer — Bengali {@code র} where Assamese writes {@code ৰ}. Only an inequality
         * test catches it.
         */
        @Test
        @DisplayName("Assamese is not a copy of Bengali")
        void assameseIsNotBengali() {
            assertThat(seeded.get("as"))
                    .as("as must be genuine Assamese, not bn reseeded under a second locale code")
                    .isNotEqualTo(seeded.get("bn"));
        }

        @Test
        @DisplayName("each locale is written in its own script")
        void localesUseTheirOwnScripts() {
            assertScript("hi", "\\u0900-\\u097F");   // Devanagari
            assertScript("bn", "\\u0980-\\u09FF");   // Bengali-Assamese
            assertScript("as", "\\u0980-\\u09FF");
            assertScript("te", "\\u0C00-\\u0C7F");   // Telugu
            assertScript("ta", "\\u0B80-\\u0BFF");   // Tamil
            assertScript("gu", "\\u0A80-\\u0AFF");   // Gujarati
            assertScript("ur", "\\u0600-\\u06FF");   // Arabic (Urdu)
            assertScript("kn", "\\u0C80-\\u0CFF");   // Kannada
            assertScript("ml", "\\u0D00-\\u0D7F");   // Malayalam
            assertScript("pa", "\\u0A00-\\u0A7F");   // Gurmukhi
            assertScript("or", "\\u0B00-\\u0B7F");   // Oriya
        }

        private void assertScript(String locale, String range) {
            assertThat(seeded.get(locale).get("assistance.queue.group"))
                    .as("%s must be written in its own script (%s)", locale, range)
                    .matches(".*[" + range + "].*");
        }
    }

    @Nested
    @DisplayName("placeholders survive translation")
    class Placeholders {

        /**
         * The strictest assertion in the file, and the cheapest insurance.
         *
         * <p>{@code translate} substitutes only the slots it is handed and leaves the rest standing,
         * and the client then throws away any string still holding one. So a {@code {{total}}}
         * translated to {@code {{कुल}}}, or a brace lost to a copy-paste, reverts that one locale to
         * English and looks like a locale nobody got round to.
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
         * Guards the check above against being vacuously true. If the English map ever stopped
         * carrying {@code {{...}}} slots, that test would compare empty sets and pass while proving
         * nothing. These four are the {@code PARAM_*} constants on {@code AssistanceQueueService}.
         */
        @Test
        @DisplayName("the English map really does carry the four documented placeholders")
        void englishCarriesTheDocumentedPlaceholders() {
            Set<String> all = new LinkedHashSet<>();
            englishRows().values().forEach(v -> all.addAll(placeholdersIn(v)));
            assertThat(all).containsExactlyInAnyOrder("count", "total", "hours", "overdue");
        }

        /**
         * BOTH numbers in every locale. Brief 21's position is that a bare figure with no denominator
         * will be distrusted, correctly — "3 cases breach" invites "out of how many?", and an officer
         * who cannot answer it cannot triage. A locale carrying only {@code count} would read as a
         * complete sentence while withholding the thing that makes it actionable.
         */
        @Test
        @DisplayName("the signal sentence carries numerator AND denominator in all 13 locales")
        void everyLocaleCarriesBothNumbers() {
            List<String> deficient = new ArrayList<>();
            for (String locale : PRODUCT_LOCALES) {
                Set<String> slots = placeholdersIn(
                        seeded.getOrDefault(locale, Map.of()).getOrDefault(SIGNAL_KEY, ""));
                if (!slots.containsAll(Set.of("count", "total", "hours"))) {
                    deficient.add(locale + " had " + slots);
                }
            }
            assertThat(deficient)
                    .as("a locale missing total reports a figure nobody can act on")
                    .isEmpty();
        }

        /**
         * The hyphens are load-bearing. The client asks for
         * {@code assistance.queue.signal.queue-deadline-triage}, so an underscored key here misses the
         * lookup and the sentence reverts to English while looking untranslated. Built from the service
         * constant so renaming the kind without renaming the key fails here rather than in production.
         */
        @Test
        @DisplayName("the signal key keeps the hyphenated kind, never an underscored one")
        void signalKeyStaysHyphenated() {
            assertThat(englishRows().keySet()).contains(SIGNAL_KEY);
            assertThat(SIGNAL_KEY).isEqualTo("assistance.queue.signal.queue-deadline-triage");

            assertThat(seeded.values().stream()
                    .flatMap(rows -> rows.keySet().stream())
                    .filter(code -> code.startsWith("assistance.queue.signal.") && code.contains("_"))
                    .toList())
                    .as("an underscored signal key misses the client's lookup silently")
                    .isEmpty();
        }
    }

    /**
     * The queue namespace must not collide with the rail's.
     *
     * <p>Both seeders are insert-if-absent on the key CODE and both declare module {@code assistance},
     * so a key seeded by both would be written by whichever ran first and the second seeder's value
     * would be silently discarded — a translation that exists in the source and not in the database.
     * Asserted by prefix rather than by comparing the two seeders, so it holds as either grows.
     */
    @Test
    @DisplayName("every key lives under assistance.queue., clear of the rail's assistance.signal.")
    void theNamespaceIsDisjointFromTheRails() {
        assertThat(englishRows().keySet()).isNotEmpty();
        assertThat(englishRows().keySet()).allSatisfy(code ->
                assertThat(code).startsWith("assistance.queue."));
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
