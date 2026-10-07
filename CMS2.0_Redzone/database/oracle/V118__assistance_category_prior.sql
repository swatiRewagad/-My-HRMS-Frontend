-- V118: Assistance — the token-to-category prior (auto-category suggestion from complaint text)
-- Oracle version (mirrors database/V120__assistance_category_prior.sql; the two directories' V-numbers
-- are not in sync — this is the twin of MySQL V120, not of any V118 there)
--
-- THE FULL RATIONALE LIVES IN THE MYSQL TWIN and is not duplicated here, because two copies of a
-- 200-line argument diverge. What follows is the Oracle-specific part plus the measured summary a reader
-- of THIS file needs in order not to mistake a defensibly-quiet feature for a broken one.
--
-- ═══ WHY THIS ROLLUP MATTERS MORE THAN THE OTHER THREE ═══
-- COMPLAINTS.CATEGORY_ID is populated on 273 of 4403 rows (6.2%). That one gap is why V113's
-- next-action prior, V114's clause affinity and V115's entity-pattern alert all carry a CATEGORY_KEY
-- that holds the wildcard sentinel 0 on essentially every row they can write. None of those three needs
-- a code change to sharpen — they need CATEGORY_ID populated. SUBJECT and DESCRIPTION are populated on
-- 4343 of 4403 (98.6%), so the input side is rich and the label side is thin.
--
-- ═══ WHAT A ROW MEANS ═══
-- "Of the TOKEN_TOTAL labelled complaints whose SUBJECT or DESCRIPTION contains TOKEN, OCCURRENCES were
-- categorised as CATEGORY_KEY. That category holds CATEGORY_TOTAL of the LABELLED_TOTAL labelled
-- complaints overall." A COUNT of word-to-label co-occurrence. No model artifact, nothing trained.
--
-- ═══ THE KEY IS TWO COLUMNS, AND THAT IS A MEASURED DIVERGENCE FROM THE SIBLINGS ═══
-- (TOKEN, CATEGORY_KEY). The siblings carry five and six key columns; 273 labelled rows over 10
-- categories cannot support more. Measured against the MySQL dev database (the only engine with
-- populated data available — these are properties of the DATASET, not of the engine, and they have NOT
-- been re-measured against an Oracle instance, which is stated rather than implied):
--   * SCHEME_VERSION — rejected, though every sibling keeps it. 4092 of 4403 rows carry none, so it
--     would be a constant. And a WORD is not scheme-scoped: 'ATM' means the same under RBIOS_2021 and
--     RBIOS_2026, whereas clause 15(1)(a) does not. Pooling schemes is a correctness bug for a clause
--     and free for a token.
--   * DEPARTMENT — rejected on arithmetic. Splitting 273 rows across CEPC / RBIO / CRPC puts every
--     token below the 3-document floor in the smaller two, so the rollup would hold CEPC rows only
--     while LOOKING department-aware.
--   * GROUND_OF_COMPLAINT_ID — rejected. Populated on 0 of 4403 rows and named by nothing here.
--
-- ═══ SENTINELS, NOT NULLS — AND THE NOTE THAT THIS TABLE NEVER USES ONE ═══
-- Oracle does not enforce a composite UNIQUE constraint across rows where any keyed column is NULL, so
-- a NULL dimension would make the key non-idempotent on BOTH engines and every refresh would append
-- instead of updating; NULL is also not comparable with '=', so a nullable key column is unreachable by
-- any seek. Both key columns here are NOT NULL and NEITHER is ever a wildcard: CATEGORY_KEY always
-- holds a real COMPLAINT_CATEGORIES.ID because the rollup mines only labelled rows, and TOKEN is never
-- blank. ON ORACLE THAT SECOND POINT IS LOAD-BEARING IN ITS OWN RIGHT: an empty string IS NULL on
-- Oracle, so a tokenizer that emitted '' would violate NOT NULL rather than writing a useless row —
-- AssistanceTextTokenizer rejects empty and blank tokens before they reach SQL. The numeric sentinel 0
-- is reserved and never written, said out loud so a reader comparing this with V114 does not look for
-- wildcard rows that cannot exist.
--
-- ═══ NORMALISED ON WRITE, IN LOWER CASE, BY ONE SHARED CLASS ═══
-- AssistanceTextTokenizer normalises on BOTH the write and the read path — one class, two callers. A
-- second implementation on the read side is how a rollup comes to be keyed on words no read can match.
-- LOWER, uniquely in this feature set: the siblings upper-case because they carry controlled
-- vocabularies that appear mixed-case in this database, and a token is not a code. The case choice
-- matters only in that both sides agree, which one class guarantees.
--
-- THIS IS NOT MERELY A STYLE CHOICE ON ORACLE. MySQL's utf8mb4_unicode_ci collation folds comparisons
-- for free and Oracle's default collation does not, so a token stored verbatim and compared verbatim
-- would match in dev and MISS in production. Normalising in Java also means the read never needs
-- LOWER(TOKEN), which would defeat the index on both engines, and the read uses `TOKEN IN (...)` —
-- never a leading-wildcard LIKE, which is forbidden outright.
--
-- ═══ WHAT THE FLOORS LEAVE, MEASURED ═══
-- (the service holds the authoritative constants: MIN_TOKEN_LENGTH=3, MIN_TOKEN_SAMPLE=3,
--  MAX_DOC_FRACTION=0.50, MIN_LABELLED_CORPUS=50, MIN_MATCHED_TOKENS=2, MIN_CONFIDENCE=0.35,
--  MIN_LIFT=1.0. Reproduce with: python -I CMS2.0_Redzone/scripts/measure-category-prior.py)
--   labelled corpus 273 over 10 categories -> 249 yield a token -> 284 distinct tokens -> 85 clear the
--   sample and document-fraction floors -> 141 (TOKEN, CATEGORY_KEY) rows.
--   141 ROWS is the honest size of this table against today's register.
--   Self-check on the labelled corpus (not held out, so an upper bound): 212 top-1 correct, 10 wrong,
--   51 silent, 95.5% precision where it spoke.
--   COVERAGE OF THE 4130 UNLABELLED: 628 (15.2%) would receive a suggestion; 3469 are silent for having
--   fewer than 2 known tokens; 33 fall below the confidence or lift floor. 599 of the 628 point at
--   category 1.
--
-- ═══ FOUR THINGS TO READ BEFORE CALLING THIS BROKEN ═══
-- A. THE LABELS ARE PARTLY WRONG AT SOURCE. config/DemoDataSeeder.java:72 pairs its `categoryIds`
--    array positionally against a `subjects` array ordered for a DIFFERENT taxonomy, so NINE of its TEN
--    subjects carry the wrong category — 'ATM card blocked' is labelled Loan/Advances, 'Loan EMI
--    overcharged' is labelled Mobile Banking/UPI, 'Net banking fraud' is labelled ATM/Debit Card. 60 of
--    the 273 labelled rows come from it. The consequence is visible: the token 'atm' resolves to
--    category 5 (Loan / Advances) on 11 of its 21 documents. THIS MUST NOT BE PATCHED IN THE COUNTING —
--    correcting it here would mean hard-coding a judgement about which labels are right, which is the
--    one thing a count may not do. The fix is one line in DemoDataSeeder.
-- B. THE VOCABULARY IS MOSTLY FIXTURE TEXT. 164 of 273 labelled complaints carry a fixture subject
--    (RETLC%, E2E%, QA%, FTWIN), 60 come from the seeder, ~49 are realistic prose. 'retlc', 'ftwin' and
--    'cepc' are among the thickest tokens. Not stopworded, deliberately: that would be tuning product
--    code to one database's test seed.
-- C. THE MAJORITY CLASS IS 74%. MIN_LIFT exists for exactly that and does not fix it.
-- D. NO NAME-DERIVED PII BLOCKLIST. Measured and REJECTED, not omitted: blocking every token appearing
--    in a COMPLAINANT_NAME removes 'withdrawal', 'card', 'atm', 'status', 'window', 'session' and
--    'duplicate', because COMPLAINANT_NAME is itself polluted with fixture phrases ('Session Cee' on
--    245 rows, 'Withdrawal Notification Citizen' on 64). Coverage fell 15.2% -> 8.1%. See
--    AssistanceTextTokenizer for the PII position shipped instead and the residual risk accepted.
--
-- ═══ ORACLE-SPECIFIC NOTES ═══
-- Applied BY HAND — there is no Flyway — so idempotency lives in this file. Oracle has no
-- CREATE TABLE IF NOT EXISTS, so the DDL is wrapped in a PL/SQL block guarded on USER_TABLES, which is
-- the pattern oracle V113 established. No separate CREATE INDEX is needed: Oracle backs a UNIQUE
-- constraint with an index automatically, so UK_ACP_TOKEN_CATEGORY is both the constraint and the
-- lookup index.
--
-- ASSISTANCE_JOB_LOCK is NOT re-created here — oracle V113 owns it, and two migrations owning one DDL
-- is how a column width comes to differ between two environments. This file seeds only an additional
-- named lease row, under its own name, because four jobs now share that table and a shared lease ROW
-- would make each block the others for no reason: whichever fired first would hold it and the rest
-- would log "another pod holds the lease", which is both false and the hardest kind of bug to see — a
-- job that is merely never running.
--
-- NOT APPLIED as shipped. The read path returns a 200 carrying an empty suggestion list when the table
-- is absent or empty, so an unapplied migration degrades this feature to invisible rather than breaking
-- the filing form or the officer's category picker.

-- ── The rollup ───────────────────────────────────────────────────────────────────────────────────
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'ASSISTANCE_CATEGORY_PRIOR';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ASSISTANCE_CATEGORY_PRIOR (
                ID              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,

                -- One normalised word from a labelled complaint''s SUBJECT or DESCRIPTION.
                --
                -- 64 characters, matching AssistanceTextTokenizer.MAX_TOKEN_LENGTH exactly, so a token
                -- the tokenizer accepts can never be silently truncated on the way in — a truncated
                -- token would key a row no read could ever seek. Oracle RAISES on an over-long value
                -- rather than truncating, so on this engine the mismatch would surface as a failed
                -- refresh rather than as a silently unreachable row; the tokenizer enforces the same
                -- bound in Java so neither happens. 64 is chosen from the longest plausible word in a
                -- complaint (''reconciliation'', ''acknowledgement'' are 14-15) with room for an
                -- agglutinated Indic-script term — anything longer is not a word.
                --
                -- Written LOWER-cased and digit-free; the only text column in the assistance set that
                -- is not upper-cased. See the header.
                TOKEN           VARCHAR2(64)  NOT NULL,

                -- A REAL COMPLAINT_CATEGORIES.ID. Never the sentinel, because this rollup mines only
                -- labelled rows. NOT NULL, and the numeric sentinel 0 is reserved and never written.
                --
                -- COMPLAINT_CATEGORIES, not CATEGORY_MASTER. Both tables exist and both look
                -- plausible; MEASURED, CATEGORY_MASTER.CATEGORY_NAME reads ''S1 authority e2e probe''
                -- for ids 1-6 and has no row at all for 7-10, while COMPLAINT_CATEGORIES names the ten
                -- real categories the register uses, and its ID is what COMPLAINTS.CATEGORY_ID
                -- resolves against. A reader who joined the other master would conclude the data was
                -- garbage.
                --
                -- Deliberately NOT a foreign key, matching every other assistance rollup. A rollup is
                -- derived data: an FK would make retiring a category fail against a derived table
                -- rather than against the complaints that reference it, and the read already drops any
                -- category the master no longer holds.
                CATEGORY_KEY    NUMBER(19)    NOT NULL,

                -- The NUMERATOR: labelled complaints containing TOKEN categorised as CATEGORY_KEY.
                OCCURRENCES     NUMBER(19)    NOT NULL,

                -- The DENOMINATOR: labelled complaints containing TOKEN at all, across every category.
                --
                -- Denormalised onto every row of a token rather than derived by summing them, so the
                -- read needs no second aggregate and a partially-refreshed token cannot show a
                -- numerator from this pass against a denominator built from the last. Both NOT NULL:
                -- the schema does not permit storing a recommendation without the number that makes it
                -- weighable. "matched 41 of 52 complaints containing ''withdrawal''" is a statement an
                -- officer can act on; "category: ATM" is not.
                TOKEN_TOTAL     NUMBER(19)    NOT NULL,

                -- The category''s own size, and the corpus size. The BASE RATE, carried so the read can
                -- compute lift without a second query or a second aggregate.
                --
                -- These two are what stop the feature naming the majority class. Category 1 holds 202
                -- of 273 labels (74%), so a category whose mean P(category|token) is 0.5 is doing WORSE
                -- than guessing and must not be suggested. The read''s MIN_LIFT floor is
                -- confidence / (CATEGORY_TOTAL / LABELLED_TOTAL), computable from ONE row — which is
                -- why these are denormalised here rather than read from COMPLAINT_CATEGORIES on
                -- request. §6.2 forbids the request-time aggregate the alternative would need.
                --
                -- Constant across every row of one pass, which looks like redundancy and is not: it
                -- makes each row a self-contained statement, and a row read DURING a refresh carries
                -- the base rate from the same pass as its own counts.
                CATEGORY_TOTAL  NUMBER(19)    NOT NULL,
                LABELLED_TOTAL  NUMBER(19)    NOT NULL,

                -- When the refresh that wrote this row ran. Read by nothing in the request path — it
                -- exists so a rollup that quietly stopped refreshing is diagnosable, which is the one
                -- failure a scheduled job has that an endpoint does not: stale counts look exactly
                -- like correct counts. It is also the stale sweep''s only predicate.
                REFRESHED_AT    TIMESTAMP(6)  NOT NULL,

                CONSTRAINT PK_ASSISTANCE_CATEGORY_PRIOR PRIMARY KEY (ID),

                -- The key IS the lookup and the idempotency. Oracle backs a UNIQUE constraint with an
                -- index automatically, so this is also the only index the read needs.
                --
                -- The read is `TOKEN IN (?, ?, ...)` over at most MAX_QUERY_TOKENS values — one index
                -- range per token, each returning that token''s whole category distribution
                -- contiguously because CATEGORY_KEY is the trailing key part, so the read''s
                -- ORDER BY TOKEN, CATEGORY_KEY is served by the index itself with no sort. The refresh
                -- upserts against this constraint rather than truncating: a TRUNCATE plus reload would
                -- leave the suggestion silent for the duration of every refresh, and a feature whose
                -- failure mode is silence cannot be observed failing.
                CONSTRAINT UK_ACP_TOKEN_CATEGORY UNIQUE (TOKEN, CATEGORY_KEY)
            )';
    END IF;
END;
/

-- ── This refresh's own lease row ─────────────────────────────────────────────────────────────────
-- ASSISTANCE_JOB_LOCK is created by oracle V113 and is NOT redefined here. Only the row is added, under
-- a SEPARATE lease name from the three jobs already using the table — see the header.
--
-- Dated in the past so the first pod to start can take it immediately. Guarded on the table EXISTING as
-- well as on the row's absence, so applying this file before V113 inserts nothing and raises nothing —
-- and the refresh service treats a missing lease row as "I did not get the lock" and does nothing,
-- which is the correct behaviour for a half-applied schema.
--
-- EXECUTE IMMEDIATE rather than a plain INSERT, because a static INSERT against a table that may not
-- exist fails at COMPILE time inside a PL/SQL block — the guard on USER_TABLES would never be reached.
DECLARE
    v_table NUMBER;
    v_row   NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table FROM USER_TABLES WHERE TABLE_NAME = 'ASSISTANCE_JOB_LOCK';
    IF v_table > 0 THEN
        EXECUTE IMMEDIATE
            'SELECT COUNT(*) FROM ASSISTANCE_JOB_LOCK '
            || 'WHERE LOCK_NAME = ''assistance-category-prior-refresh'''
            INTO v_row;
        IF v_row = 0 THEN
            EXECUTE IMMEDIATE
                'INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT) '
                || 'VALUES (''assistance-category-prior-refresh'', '
                || 'TIMESTAMP ''1970-01-01 00:00:00'', NULL, NULL)';
        END IF;
    END IF;
END;
/

COMMIT;

-- ── No new index on COMPLAINTS, and that is a measured result ────────────────────────────────────
-- The refresh reads the labelled complaints keyset-paged on the PRIMARY KEY
-- (WHERE ID > :after AND CATEGORY_ID IS NOT NULL ORDER BY ID), which needs no index that does not
-- already exist. An index on (CATEGORY_ID) was considered and rejected: the job reads EVERY labelled
-- row by design, so there is nothing for it to seek, the qualifying slice is 273 of 4403 rows, and the
-- index would be a B-tree maintained on every complaint write for the benefit of one job that runs
-- every six hours.
--
-- The REQUEST path touches COMPLAINTS at most once, by COMPLAINT_NUMBER (an existing unique index) and
-- only when the caller passed a number rather than raw text. It then reads ASSISTANCE_CATEGORY_PRIOR by
-- UK_ACP_TOKEN_CATEGORY. It issues no aggregate and no scan, which is the point of precomputing.
