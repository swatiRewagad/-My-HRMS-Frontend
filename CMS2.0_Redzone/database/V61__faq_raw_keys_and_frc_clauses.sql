-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V61 — Retire the ten orphaned FAQ rows, and add the two First-Resolution closure clauses.
--
-- PART 1 — THE FAQ RAW-KEY DEFECT (citizen-facing).
--
-- FAQ rows id 1-10 store the literal strings 'faq.q1.question' … 'faq.q10.question' (and the matching
-- .answer keys) in QUESTION_KEY / ANSWER_KEY. No TRANSLATION_KEYS row exists for any of the 20 codes —
-- verified: SELECT COUNT(*) FROM TRANSLATION_KEYS WHERE CODE REGEXP '^faq\.q[0-9]+\.' returns 0. The
-- portal's translate pipe renders `this.translations()[key] || key`, so a complainant literally sees
-- the text "faq.q1.question" where a question should be.
--
-- WHY DEACTIVATE RATHER THAN SEED 20 NEW KEYS. Rows 11-28 use descriptive keys that DO resolve and
-- already cover the same ground, topic for topic:
--     GENERAL     (1-3)  -> faq.q_what_is_scheme, faq.q_fee, faq.q_complain_to_re_first
--     FILING      (4-5)  -> faq.q_documents, faq.q_languages, faq.q_accessibility
--     TRACKING    (6-7)  -> faq.q_track, faq.q_track_anonymous
--     APPEAL      (8-9)  -> faq.q_no_reply, faq.q_not_taken_up, faq.q_not_eligible
--     WITHDRAWAL  (10)   -> faq.q_withdraw, faq.q_withdraw_consent
-- Inventing replacement question and answer TEXT for rows 1-10 would mean authoring citizen-facing
-- content about statutory rights, which is not a migration's job. Retiring duplicates that render
-- broken is the conservative fix. FaqRepository reads only findByIsActiveTrue..., so IS_ACTIVE = 0
-- removes them from every response without deleting evidence of what was there.
--
-- THIS ALSO FIXES A SECOND, UNDOCUMENTED DEFECT. faq.component.ts builds its category-filter buttons
-- as translate('faq.cat_' + category), and the lookup is case-sensitive. Only the lowercase keys
-- faq.cat_eligibility / _filing / _privacy / _tracking exist. Rows 1-10 are the ONLY rows carrying the
-- UPPERCASE categories GENERAL / FILING / TRACKING / APPEAL / WITHDRAWAL, so they were also the only
-- reason the category buttons rendered raw keys. Retiring these rows removes those five categories
-- from the distinct-category list entirely.
--
-- PART 2 — FIRST-RESOLUTION CLOSURE CLAUSES.
--
-- AutoClosureService emitted the literal "Clause FRC" — not a clause of any Scheme — into
-- citizen-facing closure text, because CLOSURE_CLAUSE_MASTER held no 10(1)(*) row at all. Per the
-- ruling: a complainant who never approached the Regulated Entity closes under 10(1)(e); one who
-- approached the RE but filed before the reply window elapsed closes under 10(1)(g). The service now
-- resolves the code through this master and omits the citation if it is absent, so these rows are
-- what make the feature work rather than decoration.
--
-- Re-running is safe: the UPDATE is scoped BY KEY PATTERN and is idempotent; every INSERT is guarded
-- with WHERE NOT EXISTS. Never scope a corrective UPDATE by display text — labels are localised.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

-- ── PART 1: retire the ten unresolvable FAQ rows ────────────────────────────────────────────────
-- Scoped on the key pattern, not on id, so it is correct even if ids differ between environments.
UPDATE FAQ
   SET IS_ACTIVE = 0,
       UPDATED_AT = NOW()
 WHERE IS_ACTIVE = 1
   AND QUESTION_KEY REGEXP '^faq\\.q[0-9]+\\.question$'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS tk WHERE tk.CODE = FAQ.QUESTION_KEY);

-- ── PART 2: the two First-Resolution clauses ────────────────────────────────────────────────────
-- Neither is appealable: both are maintainability failures the complainant can cure by re-filing once
-- the pre-condition is met, so there is nothing to appeal against.
INSERT INTO CLOSURE_CLAUSE_MASTER
    (SCHEME_VERSION, CLAUSE_CODE, LABEL, LABEL_KEY, CATEGORY,
     APPEALABLE_BY_COMPLAINANT, APPEALABLE_BY_ENTITY, RESTRICTED_TO_ROLES, ACTIVE, CREATED_AT, UPDATED_AT)
SELECT 'RBIOS_2021', '10(1)(e)',
       'Not maintainable - complainant has not first approached the Regulated Entity',
       'clause.10_1_e', 'NON_MAINTAINABLE', 0, 0, NULL, 1, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM CLOSURE_CLAUSE_MASTER
                    WHERE SCHEME_VERSION = 'RBIOS_2021' AND CLAUSE_CODE = '10(1)(e)');

INSERT INTO CLOSURE_CLAUSE_MASTER
    (SCHEME_VERSION, CLAUSE_CODE, LABEL, LABEL_KEY, CATEGORY,
     APPEALABLE_BY_COMPLAINANT, APPEALABLE_BY_ENTITY, RESTRICTED_TO_ROLES, ACTIVE, CREATED_AT, UPDATED_AT)
SELECT 'RBIOS_2021', '10(1)(g)',
       'Not maintainable - filed before the Regulated Entity reply window elapsed',
       'clause.10_1_g', 'NON_MAINTAINABLE', 0, 0, NULL, 1, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM CLOSURE_CLAUSE_MASTER
                    WHERE SCHEME_VERSION = 'RBIOS_2021' AND CLAUSE_CODE = '10(1)(g)');

-- ── PART 2b: their labels, ENGLISH ONLY ─────────────────────────────────────────────────────────
-- DO NOT add TRANSLATIONS rows for these keys, in any locale. A clause label is an operative statement
-- of what the clause means, so a machine translation of one is a legal assertion. All 13 pre-existing
-- clause.* keys carry English via DEFAULT_VALUE and have ZERO rows in TRANSLATIONS; the
-- TranslationService fallback serves the English default into every locale bundle, which is the
-- intended behaviour. A guard test asserts clause.* keys have no non-English translations.
INSERT INTO TRANSLATION_KEYS (CODE, DEFAULT_VALUE, DESCRIPTION, MODULE, CREATED_AT, UPDATED_AT)
SELECT 'clause.10_1_e',
       'Not maintainable - complainant has not first approached the Regulated Entity',
       'Closure clause label (CLOSURE_CLAUSE_MASTER.label_key)', 'clause-master', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'clause.10_1_e');

INSERT INTO TRANSLATION_KEYS (CODE, DEFAULT_VALUE, DESCRIPTION, MODULE, CREATED_AT, UPDATED_AT)
SELECT 'clause.10_1_g',
       'Not maintainable - filed before the Regulated Entity reply window elapsed',
       'Closure clause label (CLOSURE_CLAUSE_MASTER.label_key)', 'clause-master', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'clause.10_1_g');
