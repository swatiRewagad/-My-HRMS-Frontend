package com.hrms.cms.config;

import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English labels for the closure clauses in {@code CLOSURE_CLAUSE_MASTER}.
 *
 * <p>WHY THIS EXISTS: {@link ClosureClauseMasterSeeder} writes a {@code label_key} of the form
 * {@code clause.15_1_a} onto every clause row, and the AA parent-complaint search renders
 * {@code clause.labelKey ? (clause.labelKey | translate) : clause.label} — so because {@code labelKey}
 * is always non-null, the English {@code label} fallback is NEVER reached. No seeder provided these
 * keys, so every closure-clause dropdown option rendered as the raw key, e.g.
 * "15(1)(a) — clause.15_1_a", to staff choosing the clause a citizen's complaint is closed under.
 *
 * <p>SCOPE — deliberately English only. Each value is copied VERBATIM from
 * {@code ClosureClauseMasterSeeder}, which is the existing source of truth for this wording, so this
 * class introduces no new text. The nine other locales are NOT seeded here on purpose: these strings
 * are operative descriptions of Scheme clauses, and a translated clause label is a legal statement
 * about what that clause means. Guessing them would be worse than showing English. They are flagged
 * for legal//translation sign-off; until then the {@code TranslationService} fallback resolves a
 * missing locale to the English default, which is correct behaviour rather than a raw key.
 *
 * <p>Insert-if-absent like every other seeder, so re-running is a no-op and a later text correction
 * needs a code-scoped UPDATE migration rather than an edit here.
 */
@Component
@Order(19)
public class ClauseLabelTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "clause-master";

    private final TranslationKeyRepository keyRepo;

    public ClauseLabelTranslationSeeder(TranslationKeyRepository keyRepo) {
        this.keyRepo = keyRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
    }

    /** Copied verbatim from ClosureClauseMasterSeeder — do not reword independently of that class. */
    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        // ── Awards: the only appealable clauses ──
        m.put("clause.15_1_a", "Award - full relief granted");
        m.put("clause.15_1_b", "Award - partial relief with compensation");
        // ── Resolution ──
        m.put("clause.16_1", "Resolved to the satisfaction of the complainant");
        // ── Non-maintainable ──
        m.put("clause.16_2_a", "Not maintainable - time barred");
        m.put("clause.16_2_b", "Not maintainable - frivolous or vexatious");
        m.put("clause.16_2_c", "Not maintainable - sub-judice");
        m.put("clause.16_2_d", "Not maintainable - outside jurisdiction");
        m.put("clause.16_2_e", "Not maintainable - already settled by RBI");
        m.put("clause.16_2_f", "Not maintainable - covered by another dispute mechanism");
        m.put("clause.16_2_g", "Not maintainable - anonymous complaint");
        m.put("clause.16_2_h", "Not maintainable - insufficient information");
        // ── Closure ──
        m.put("clause.16_3", "Closed - complainant not responding");
        m.put("clause.16_4", "Closed - matter settled between the parties");
        return m;
    }

    private void seed(String code, String englishValue) {
        if (keyRepo.existsByCode(code)) {
            return;
        }
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(MODULE);
        key.setDefaultValue(englishValue);
        key.setDescription("Closure clause label (CLOSURE_CLAUSE_MASTER.label_key)");
        keyRepo.save(key);
    }
}
