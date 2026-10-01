-- ============================================================
-- V59 — Retire the ten orphaned FAQ rows, add the two First-Resolution closure clauses
-- Oracle counterpart of MySQL V61. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- The rationale is recorded in full in the MySQL counterpart (database/V61). In brief:
--
-- PART 1 — FAQ rows whose QUESTION_KEY is a literal 'faq.q<N>.question' have no TRANSLATION_KEYS row,
--   so the portal's translate pipe renders the key itself and a complainant sees "faq.q1.question"
--   where a question should be. Rows 11-28 use descriptive keys that resolve and already cover the same
--   topics, so the duplicates are retired rather than having 20 new keys of invented citizen-facing
--   content authored in a migration. IS_ACTIVE = 0 is enough because FaqRepository reads only
--   findByIsActiveTrue..., and it preserves the evidence instead of deleting it.
--   This also fixes the category-filter buttons: rows 1-10 were the only rows carrying the UPPERCASE
--   categories, and faq.component.ts looks up 'faq.cat_' || category case-sensitively against keys that
--   exist only in lowercase.
--
-- PART 2 — CLOSURE_CLAUSE_MASTER held no 10(1)(*) row, so AutoClosureService emitted the placeholder
--   literal "Clause FRC" — not a clause of any Scheme — into citizen-facing text. Per the ruling: no
--   prior approach to the Regulated Entity closes under 10(1)(e); filing before the RE reply window
--   elapsed closes under 10(1)(g). The service resolves the code through this master and omits the
--   citation when absent, so these rows are load-bearing.
--
-- Clause labels are seeded ENGLISH ONLY, via TRANSLATION_KEYS.DEFAULT_VALUE and with NO TRANSLATIONS
-- rows in any locale. A translated clause label is a legal statement about what the clause means.
--
-- Re-running is safe: every statement is guarded by an existence check or scoped by key pattern.
-- ============================================================

DECLARE
    PROCEDURE seed_clause(p_code VARCHAR2, p_label VARCHAR2, p_label_key VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM CLOSURE_CLAUSE_MASTER
         WHERE SCHEME_VERSION = 'RBIOS_2021' AND CLAUSE_CODE = p_code;
        IF v_count = 0 THEN
            -- Neither clause is appealable: both are maintainability failures the complainant can cure
            -- by re-filing once the pre-condition is met.
            INSERT INTO CLOSURE_CLAUSE_MASTER
                (SCHEME_VERSION, CLAUSE_CODE, LABEL, LABEL_KEY, CATEGORY,
                 APPEALABLE_BY_COMPLAINANT, APPEALABLE_BY_ENTITY, RESTRICTED_TO_ROLES,
                 ACTIVE, CREATED_AT, UPDATED_AT)
            VALUES ('RBIOS_2021', p_code, p_label, p_label_key, 'NON_MAINTAINABLE',
                    0, 0, NULL, 1, SYSTIMESTAMP, SYSTIMESTAMP);
        END IF;
    END;

    PROCEDURE seed_key(p_code VARCHAR2, p_default VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM TRANSLATION_KEYS WHERE CODE = p_code;
        IF v_count = 0 THEN
            INSERT INTO TRANSLATION_KEYS (CODE, DEFAULT_VALUE, DESCRIPTION, MODULE, CREATED_AT, UPDATED_AT)
            VALUES (p_code, p_default,
                    'Closure clause label (CLOSURE_CLAUSE_MASTER.label_key)', 'clause-master',
                    SYSTIMESTAMP, SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    -- ── PART 1: retire the ten unresolvable FAQ rows ────────────────────────────────────────────
    -- Scoped on the key pattern, not on id, so it is correct even if ids differ between environments.
    UPDATE FAQ f
       SET IS_ACTIVE = 0,
           UPDATED_AT = SYSTIMESTAMP
     WHERE IS_ACTIVE = 1
       AND REGEXP_LIKE(f.QUESTION_KEY, '^faq\.q[0-9]+\.question$')
       AND NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS tk WHERE tk.CODE = f.QUESTION_KEY);

    -- ── PART 2: the two First-Resolution clauses ────────────────────────────────────────────────
    seed_clause('10(1)(e)',
        'Not maintainable - complainant has not first approached the Regulated Entity',
        'clause.10_1_e');
    seed_clause('10(1)(g)',
        'Not maintainable - filed before the Regulated Entity reply window elapsed',
        'clause.10_1_g');

    -- ── PART 2b: their labels, ENGLISH ONLY — do NOT add TRANSLATIONS rows ──────────────────────
    seed_key('clause.10_1_e',
        'Not maintainable - complainant has not first approached the Regulated Entity');
    seed_key('clause.10_1_g',
        'Not maintainable - filed before the Regulated Entity reply window elapsed');
END;
/

COMMIT;
