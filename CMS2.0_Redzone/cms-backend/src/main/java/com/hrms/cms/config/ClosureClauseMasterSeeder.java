package com.hrms.cms.config;

import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds CLOSURE_CLAUSE_MASTER with the RBIOS 2021 closure clauses and their appealability.
 *
 * These clauses were previously a hardcoded List<Map> inside WorkflowController.getClosureClauses,
 * which meant a Scheme amendment needed a code release, and the appeal path never read them anyway.
 *
 * APPEALABILITY (per RBI ruling):
 *   - A COMPLAINANT may appeal a closure under 15(1)(a) or 15(1)(b).
 *   - A regulated ENTITY may appeal only under 15(1)(b).
 *   - Every other clause is a REPRESENTATION for both parties.
 *
 * Note this deliberately CONTRADICTS the old hardcoded list, which additionally marked 16(2)(c)-(f)
 * as "appellable". That flag was dead — no appeal code ever read it — and the ruling above supersedes
 * it. Those rows are seeded appealable=false for both parties.
 *
 * Only RBIOS_2021 is seeded. RBIOS 2026 clause numbers are NOT invented here: they are pending RBI
 * confirmation, and a guessed clause number in a citizen-facing closure would be a legal defect. The
 * schema is scheme-versioned so 2026 rows can be added as data once confirmed.
 *
 * Insert-if-absent, so correcting a value here does NOT fix rows already in the database; the
 * accompanying migration carries corrective UPDATEs for that.
 */
@Component
@Order(8)
public class ClosureClauseMasterSeeder implements CommandLineRunner {

    private static final String SCHEME = "RBIOS_2021";

    /** Roles allowed to close under an award or a discretionary non-maintainability clause. */
    private static final String OMBUDSMAN_ONLY = "OMBUDSMAN,RBIO_ADMIN,ADMIN";

    private final ClosureClauseMasterRepository repo;

    public ClosureClauseMasterSeeder(ClosureClauseMasterRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // ── Awards: the only appealable clauses ────────────────────────────────
        seed("15(1)(a)", "Award - full relief granted", "clause.15_1_a", "AWARD",
                true, false, OMBUDSMAN_ONLY);
        seed("15(1)(b)", "Award - partial relief with compensation", "clause.15_1_b", "AWARD",
                true, true, OMBUDSMAN_ONLY);

        // ── Resolution ─────────────────────────────────────────────────────────
        seed("16(1)", "Resolved to the satisfaction of the complainant", "clause.16_1", "RESOLUTION",
                false, false, null);

        // ── Non-maintainable ───────────────────────────────────────────────────
        seed("16(2)(a)", "Not maintainable - time barred", "clause.16_2_a", "NON_MAINTAINABLE",
                false, false, null);
        seed("16(2)(b)", "Not maintainable - frivolous or vexatious", "clause.16_2_b", "NON_MAINTAINABLE",
                false, false, null);
        // 16(2)(c)-(f) were marked "appellable" by the old hardcoded list. Superseded by the ruling.
        seed("16(2)(c)", "Not maintainable - sub-judice", "clause.16_2_c", "NON_MAINTAINABLE",
                false, false, OMBUDSMAN_ONLY);
        seed("16(2)(d)", "Not maintainable - outside jurisdiction", "clause.16_2_d", "NON_MAINTAINABLE",
                false, false, OMBUDSMAN_ONLY);
        seed("16(2)(e)", "Not maintainable - already settled by RBI", "clause.16_2_e", "NON_MAINTAINABLE",
                false, false, OMBUDSMAN_ONLY);
        seed("16(2)(f)", "Not maintainable - covered by another dispute mechanism", "clause.16_2_f",
                "NON_MAINTAINABLE", false, false, OMBUDSMAN_ONLY);
        seed("16(2)(g)", "Not maintainable - anonymous complaint", "clause.16_2_g", "NON_MAINTAINABLE",
                false, false, null);
        seed("16(2)(h)", "Not maintainable - insufficient information", "clause.16_2_h", "NON_MAINTAINABLE",
                false, false, null);

        // ── Closure ────────────────────────────────────────────────────────────
        seed("16(3)", "Closed - complainant not responding", "clause.16_3", "CLOSURE",
                false, false, null);
        seed("16(4)", "Closed - matter settled between the parties", "clause.16_4", "CLOSURE",
                false, false, null);

        // ── First Resolution (FRC) pre-conditions under clause 10(1) ───────────
        // Added so AutoClosureService can cite a clause from this master instead of the placeholder
        // literal "Clause FRC", which was being emitted into citizen-facing text. Per the ruling: a
        // complainant who has not approached the RE at all closes under 10(1)(e); one who approached the
        // RE but filed before the reply window elapsed closes under 10(1)(g). Both are maintainability
        // failures the complainant can cure by re-filing, so neither is appealable.
        //
        // NOTE FOR THE REPORT: 10(1)(g) is already used elsewhere in this codebase for the
        // employer-employee bar (EligibilityQuestionMasterSeeder:173 and :220). Under RB-IOS 2021 the
        // two cannot both be 10(1)(g). This is flagged as a clause-citation conflict requiring legal
        // confirmation rather than silently renumbered.
        seed("10(1)(e)", "Not maintainable - complainant has not first approached the Regulated Entity",
                "clause.10_1_e", "NON_MAINTAINABLE", false, false, null);
        seed("10(1)(g)", "Not maintainable - filed before the Regulated Entity reply window elapsed",
                "clause.10_1_g", "NON_MAINTAINABLE", false, false, null);
    }

    private void seed(String clauseCode, String label, String labelKey, String category,
                      boolean appealableByComplainant, boolean appealableByEntity,
                      String restrictedToRoles) {
        if (repo.existsBySchemeVersionAndClauseCode(SCHEME, clauseCode)) {
            return;
        }
        repo.save(ClosureClauseMaster.builder()
                .schemeVersion(SCHEME)
                .clauseCode(clauseCode)
                .label(label)
                .labelKey(labelKey)
                .category(category)
                .appealableByComplainant(appealableByComplainant)
                .appealableByEntity(appealableByEntity)
                .restrictedToRoles(restrictedToRoles)
                .active(true)
                .build());
    }
}
