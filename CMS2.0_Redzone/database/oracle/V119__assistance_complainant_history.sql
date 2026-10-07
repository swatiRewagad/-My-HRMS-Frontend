-- V119: Assistance — the complainant filing-history projection
-- Oracle version (mirrors database/V121__assistance_complainant_history.sql; the two directories'
-- V-numbers are not in sync — this is the twin of MySQL V121, not of any V119 there)
--
-- Serves TWO faces of ONE feature, because both are the same keyed lookup over a complainant's filing
-- history: duplicate / repeat-filing detection at intake, and the repeat-complainant ("vexatious")
-- signal that supplies the evidence for a statutory 16(2)(b) determination. Counts with denominators;
-- never a verdict.
--
-- THE FULL RATIONALE LIVES IN THE MYSQL TWIN and is not duplicated here, because two copies of a
-- 200-line argument diverge. What follows is the Oracle-specific part plus the measured summary a reader
-- of THIS file needs in order not to mistake a defensibly-quiet feature for a broken one.
--
-- ═══ WHY A PROJECTION (ONE ROW PER COMPLAINT) AND NOT AN AGGREGATE ROLLUP ═══
-- V113/V114/V115 each store a GROUP BY's output: one row per cohort. This one cannot. The duplicate
-- signal must NAME the earlier complaints so the officer can open them, so the read's output is a list
-- of complaint numbers rather than a count — and no cohort row can hold a list. The read is therefore
-- two index seeks (EMAIL_KEY, then PHONE_KEY, each with a FILED_AT range) unioned and counted in Java
-- over at most MAX_HISTORY_ROWS rows. No GROUP BY, no COUNT(*), no scan, no window function.
--   The one genuine aggregate this feature needs — the contact fan-out guard below, a COUNT(DISTINCT)
-- over the whole register — runs in the SCHEDULED JOB and never on request, which is where §6.2's rule
-- bites and where it is honoured.
--
-- ═══ THE REGISTER MOVED DURING THIS WORK — READ THIS FIRST ═══
-- The figures below were taken against 4403 complaints. The register reached 6598 while the work was in
-- progress (other sessions and the QA harness write to cms_db concurrently), so every absolute number is
-- a snapshot. Re-taken at the end against 6598 rows with SQL mirroring the shipped normalisation:
--   duplicate signal: email only 280 (4.2%) | email OR phone UNGUARDED 3897 (59.1%) | guarded 312 (4.7%)
--   repeat distribution: 1 filing 6057 | 2 .. 132 | 3-4 .. 13 | 5-9 .. 19 | 10+ .. 2
--     -> 34 complainants exceed 3 filings, 21 exceed 5
--   non-maintainable rows 665, of which only 6 belong to a complainant with more than one filing
-- CONCLUSIONS UNCHANGED: the guard still cuts false positives 12.5x, phone still adds +32 complaints of
-- real recall over email alone, and the non-maintainable half is still ~zero for repeat complainants.
-- The unguarded rate moved 87% -> 59% only because the new rows dilute the placeholder's share.
--
-- ═══ THE MEASUREMENT THAT DECIDED THE DESIGN ═══
-- Measured 2026-10-07 against the MySQL dev database, 4403 complaints (the only engine with populated
-- data available — these are properties of the DATASET, not of the engine, but they have NOT been
-- re-measured against an Oracle instance and that is stated rather than implied).
--
-- The columns are genuinely populated, unlike most assistance candidates: COMPLAINANT_EMAIL 4352
-- (98.8%), COMPLAINANT_PHONE 4354 (98.9%), ENTITY_CODE 4113 (93.4%), CREATED_AT complete. 153 distinct
-- emails hold more than one complaint and the largest holds 10.
--
-- THE TRAP: "match on email OR phone" is the right rule and is CATASTROPHIC here unguarded. The phone
-- column is poisoned by placeholders — 4 values cover 3699 of 4354 rows, the worst being 9876543210 on
-- 3527 complaints across 3427 DISTINCT EMAILS. A value shared by 3427 people is not an identity.
--   Complaints receiving a duplicate signal (same entity, 30-day window):
--       email only ........................  227  (5.2%)
--       email OR phone, UNGUARDED ......... 3849  (87.4%)   <- the feature is destroyed
--       email OR phone, fan-out guard .....  259  (5.9%)   <- ships
-- So PHONE_KEY carries the '*' sentinel whenever the job measures that phone as shared by more than
-- MAX_CONTACT_FANOUT (3) distinct emails, and the rule is applied symmetrically to EMAIL_KEY. It is a
-- CARDINALITY test and not a blocklist: a hardcoded '9876543210' would be right today and silently
-- wrong the first time a different placeholder was seeded.
--
-- WHAT THE FEATURE SAYS TODAY: 259 complaints name at least one earlier same-entity complaint within 30
-- days (131 name exactly one, the largest names 18); 308 have any earlier complaint lifetime; and 21
-- complainants exceed a vexatious threshold of 3 filings, 20 exceed 5.
--
-- THE HONEST WEAKNESS: NON_MAINTAINABLE will be 'N' on almost every row. The register holds 47
-- non-maintainable rows by any of the three available markers, and only 6 belong to a repeat
-- complainant. The lifetime FILING count is real; the non-maintainable count is almost always zero.
-- Nobody should read "0 of 8 closed as non-maintainable" as evidence AGAINST a 16(2)(b) determination —
-- it is evidence that the determination is not being recorded. The column is carried anyway because it
-- costs nothing until it populates, at which point the signal sharpens with no migration.
--
-- ═══ PRIVACY, ENFORCED BY THE SCHEMA RATHER THAN BY THE SERIALISER ═══
-- This is the most PII-sensitive read in the assistance set — it surfaces one person's OTHER complaints
-- — and two PII leaks have already had to be fixed in this repo. So:
--   * NO column for a complainant NAME, ADDRESS, ACCOUNT or CARD number. Not masked: ABSENT.
--   * NO column for SUBJECT or DESCRIPTION. The brief permits a snippet; this feature declines even
--     that, because a narrative snippet from someone's other complaint is the most identifying thing
--     that could be on the screen and the signal does not need it to be useful.
--   * EMAIL_KEY and PHONE_KEY are join keys and are NEVER SERIALISED — the read seeks BY them and
--     DuplicateFilingResponse has no field that could carry either. PHONE_KEY is a 10-digit tail, so
--     any country code or separator the complainant typed is dropped and never stored.
-- The wire response carries COMPLAINT_NUMBER, FILED_AT, ENTITY_KEY, STATUS and DEPARTMENT and nothing
-- else; each is either already on the officer's screen or needed to open a case they may open.
--
-- ═══ SCOPE ═══
-- DEPARTMENT is a key column so an officer cannot learn of complaints outside their own department
-- through this read. The service filters candidates against the departments the CALLER's resolved roles
-- are responsible for, reusing the role-prefix map AssistanceQueueService already measured and ships,
-- and FAILS CLOSED — roles mapping to no department yield an empty signal, never an unscoped one. A '*'
-- DEPARTMENT row is out of scope for every caller: an unprovable scope on a PII-bearing read excludes.
--   MEASURED: zero complainant emails in this register span more than one department, so the filter
-- removes nothing on today's data and a test asserting only that "results appear" would pass with the
-- filter deleted. The negative tests build cross-department fixtures explicitly and assert absence.
--   NOT scoped by OFFICE, and that is a gap rather than a decision: RequestIdentity carries no office
-- code, so an office boundary could only come from the request — which is the
-- EmailSyndicationApiController defect. Department is the finest boundary the token supports.
--
-- ═══ ORACLE-SPECIFIC NOTES ═══
-- Applied BY HAND — there is no Flyway — so idempotency lives in this file. Oracle has no
-- CREATE TABLE IF NOT EXISTS, so the DDL is wrapped in a PL/SQL block guarded on USER_TABLES, which is
-- the pattern V113 established. Unlike the MySQL twin, the two READ indexes must be created as separate
-- statements (Oracle does not accept inline non-unique index declarations inside CREATE TABLE), so each
-- gets its own USER_INDEXES guard — the Oracle equivalent of the errno-1061 problem, and the reason the
-- guard is on the INDEX NAME rather than on the table.
--
-- Oracle backs the UNIQUE constraint UK_ACH_COMPLAINT with an index automatically, so no separate
-- statement is needed for that one.
--
-- SENTINELS, NOT NULLS, and this is NOT merely a MySQL accommodation. NULL is not comparable with '=',
-- so a nullable key column makes its rows unreachable by any seek — a complaint with no email would be
-- invisible to a PHONE_KEY read that should have found it. And on Oracle an EMPTY STRING IS NULL, so ''
-- is not even available as an alternative sentinel here: '*' is the only value that works on both
-- engines. Oracle also does not enforce a UNIQUE constraint across rows where a keyed column is NULL.
--
-- NON_MAINTAINABLE is VARCHAR2(1) holding 'Y'/'N' rather than NUMBER(1), and the reason is the defect
-- this cleanup exists for: dev runs ddl-auto: update and prod runs ddl-auto: validate, so a type the two
-- dialects infer differently works in dev and fails production boot. A String field against VARCHAR(1) /
-- VARCHAR2(1) has one unambiguous mapping on both engines and needs no dialect to agree about TINYINT vs
-- NUMBER(1) vs BIT.
--
-- TEXT KEYS ARE NORMALISED ON WRITE (lower-cased for EMAIL_KEY, upper-cased for DEPARTMENT and
-- ENTITY_KEY, in Java by the refresh). The cross-engine reason is specific: MySQL's
-- utf8mb4_unicode_ci collation folds comparisons for free and Oracle's default collation does not, so a
-- value stored verbatim and compared verbatim would match in dev and MISS in production. Normalising on
-- write also means the read never needs LOWER(column) or UPPER(column), which would defeat the indexes
-- on both engines (§6.2).
--
-- The lease table ASSISTANCE_JOB_LOCK is NOT re-created here — oracle V113 owns it, and two migrations
-- owning one DDL is how a column width comes to differ between two environments. This file seeds only an
-- additional lease row under its own name. A FOURTH row, not a share of an existing one: two jobs on one
-- lease row means whichever fires first holds it and the other logs "another pod holds the lease" and
-- skips its cycle, which is both false and the hardest kind of bug to see — a job that is merely never
-- running.
--
-- NOT APPLIED as shipped, and the kill switch cms.assistance.duplicate-detection.enabled defaults to
-- FALSE in code. With the table absent every read throws, the service logs at WARN and returns the
-- empty-signal shape at HTTP 200, so an unapplied migration degrades this feature to invisible rather
-- than breaking a complaint screen.

-- ── The projection ───────────────────────────────────────────────────────────────────────────────
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES
     WHERE TABLE_NAME = 'ASSISTANCE_COMPLAINANT_HISTORY';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ASSISTANCE_COMPLAINANT_HISTORY (
                ID               NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,

                -- The complaint this row projects, and the IDEMPOTENCY key. One row per complaint, so
                -- the refresh finds and overwrites rather than accumulating. VARCHAR2(50) matches
                -- COMPLAINTS.COMPLAINT_NUMBER exactly so a value cannot be truncated on the way in.
                -- The NUMBER and not the id, because the number is what the read returns to the officer
                -- and what every other assistance endpoint keys on.
                COMPLAINT_NUMBER VARCHAR2(50)  NOT NULL,

                -- The complainant''s email, LOWERCASED AND TRIMMED ON WRITE, or ''*'' when absent or
                -- suppressed by the fan-out guard. VARCHAR2(200) matches COMPLAINANT_EMAIL.
                -- A JOIN KEY that is NEVER SERIALISED: the read seeks BY it and the response DTO has no
                -- field that could carry it. See the privacy section in the header.
                EMAIL_KEY        VARCHAR2(200) DEFAULT ''*'' NOT NULL,

                -- The LAST 10 DIGITS of the phone with every non-digit stripped, or ''*'' when absent or
                -- suppressed. VARCHAR2(20) matches COMPLAINANT_PHONE, wider than the 10 digits ever
                -- stored, so the column can never be the reason a future normalisation has to migrate.
                -- The TAIL rather than the number as filed, so ''+91 98765-43210'' and ''09876543210''
                -- are one identity; measured, 0 of 4354 non-blank phones hold fewer than 10 digits.
                -- THE FAN-OUT GUARD WRITES THE SENTINEL HERE — see the header''s 87%-to-5.9% figure.
                -- A JOIN KEY AND NEVER SERIALISED, like EMAIL_KEY.
                PHONE_KEY        VARCHAR2(20)  DEFAULT ''*'' NOT NULL,

                -- The entity complained against, canonicalised, or ''*'' when the complaint names none.
                -- VARCHAR2(50) matches ENTITY_CODE and AssistanceEntityAliasNormaliser.MAX_LENGTH.
                -- Resolved through that normaliser, which ALREADY EXISTS and is REUSED, not rewritten:
                -- ENTITY_CODE is documented dirty (''PNB'' and ''Punjab National Bank'' are one bank and
                -- UPPER() merges neither) and the normaliser is an explicit alias table, deliberately
                -- NOT fuzzy. Both sides go through it — written here, and applied to the subject
                -- complaint''s own code on the read before seeking — because if only one side did, the
                -- table would hold PNB''s history under one key and be asked for it under another, which
                -- is a WRONG count rather than a missing one.
                ENTITY_KEY       VARCHAR2(50)  DEFAULT ''*'' NOT NULL,

                -- THE SCOPE COLUMN. CEPC / RBIO / CRPC upper-cased, or ''*'' when the complaint carries
                -- none (23 of 4403). The read filters every candidate against the departments the
                -- caller''s resolved ROLES are responsible for and fails closed when they map to none. A
                -- ''*'' row is out of scope for EVERY caller: a complaint whose department is unknown
                -- cannot be proven to be in anyone''s scope, and the safe reading of an unprovable scope
                -- on a PII-bearing read is exclusion.
                DEPARTMENT       VARCHAR2(20)  DEFAULT ''*'' NOT NULL,

                -- When the complaint was filed: COALESCE(FILED_AT, CREATED_AT). CREATED_AT is complete
                -- on all 4403 rows while FILED_AT is not, so the coalesce is what makes the window
                -- reliable. NOT NULL and the range/sort column of both read indexes — a NULL would make
                -- a complaint invisible to the window while still counting toward the lifetime total,
                -- producing a numerator and denominator that disagree.
                FILED_AT         TIMESTAMP(6)  NOT NULL,

                -- The complaint''s status, verbatim, or ''*''. VARCHAR2(30) matches the source.
                -- NOT case-folded, unlike EMAIL_KEY and DEPARTMENT, because it is DISPLAYED rather than
                -- compared — this feature never seeks on it. The register mixes cases (''pending''
                -- beside ''NOT_OPENED'') and folding would show an officer a status spelled differently
                -- from the one on the complaint grid beside it.
                STATUS           VARCHAR2(30)  DEFAULT ''*'' NOT NULL,

                -- ''Y'' / ''N''. Whether this complaint was closed as NON-MAINTAINABLE — the 16(2)(b)
                -- evidence. VARCHAR2(1) and not NUMBER(1): see the header on ddl-auto validate.
                -- Set from THREE markers OR''d together because no single column answers it:
                -- MAINTAINABILITY_DETERMINATION = ''NON_MAINTAINABLE'', CLOSURE_CAUSE =
                -- ''NON_MAINTAINABLE'', or a CLOSURE_CLAUSE under 16(2). Measured: 47 rows in the whole
                -- register, 6 of them on a repeat complainant.
                NON_MAINTAINABLE VARCHAR2(1)   DEFAULT ''N'' NOT NULL,

                -- When the refresh that wrote this row ran. Read by nothing in the request path — it
                -- exists so a projection that quietly stopped refreshing is diagnosable, which is the
                -- one failure a scheduled job has that an endpoint does not: stale rows look exactly
                -- like correct rows. It is also the stale sweep''s only predicate.
                REFRESHED_AT     TIMESTAMP(6)  NOT NULL,

                CONSTRAINT PK_ASSISTANCE_COMPLAINANT_HIST PRIMARY KEY (ID),

                -- One row per complaint. The refresh''s insert-or-update decision seeks this, so a
                -- re-run overwrites rather than appending, and a complaint whose email or entity was
                -- corrected is re-keyed in place. Oracle backs this with an index automatically.
                CONSTRAINT UK_ACH_COMPLAINT UNIQUE (COMPLAINT_NUMBER)
            )';
    END IF;
END;
/

-- ── The two read indexes ─────────────────────────────────────────────────────────────────────────
-- One seek each, leading equality on the key with FILED_AT as the range and sort column, so the
-- duplicate window is served by the index and the DESC ordering needs no sort.
--
-- TWO SEPARATE INDEXES and NOT one composite on (EMAIL_KEY, PHONE_KEY, ...): the read is "email OR
-- phone", and an OR across two columns cannot be index-sought as one predicate on either engine — which
-- is precisely the measured reason ComplaintRepository.countOtherComplaintsByComplainantEmail declined
-- to match phone at all (1 row examined with email alone against 2509 with the OR). Two seeks and a
-- union in Java is how that recall is recovered without the scan.
--
-- ENTITY_KEY is deliberately in NEITHER index. The duplicate predicate does include it, but a
-- complainant's whole history is at most 19 rows after the fan-out guard, so the entity filter runs in
-- Java over rows the seek already returned. Indexing it would add a third B-tree maintained on every
-- refresh to avoid filtering a handful of rows, and it would sit between the key and FILED_AT, breaking
-- the range scan that serves the window.
--
-- Guarded on USER_INDEXES by NAME — the Oracle equivalent of MySQL's errno-1061 "duplicate key name",
-- and the reason these cannot simply ride on the table guard above: a re-application of this file with
-- the table already present must skip the indexes individually.
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_ACH_EMAIL';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_ACH_EMAIL ON ASSISTANCE_COMPLAINANT_HISTORY (EMAIL_KEY, FILED_AT)';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_ACH_PHONE';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_ACH_PHONE ON ASSISTANCE_COMPLAINANT_HISTORY (PHONE_KEY, FILED_AT)';
    END IF;
END;
/

-- ── This refresh's own lease row ─────────────────────────────────────────────────────────────────
-- ASSISTANCE_JOB_LOCK is created by oracle V113 and is NOT redefined here. Only the row is added, under
-- a SEPARATE lease name from the three that already exist — see the header for why sharing one row would
-- silently stop one of the jobs.
--
-- Dated in the past so the first pod to start can take it immediately. Guarded on the table EXISTING as
-- well as on the row's absence, so applying this file before V113 inserts nothing and raises nothing —
-- and the refresh service treats a missing lease row as "I did not get the lock" and does nothing, which
-- is the correct behaviour for a half-applied schema.
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
            || 'WHERE LOCK_NAME = ''assistance-complainant-history-refresh'''
            INTO v_row;
        IF v_row = 0 THEN
            EXECUTE IMMEDIATE
                'INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT) '
                || 'VALUES (''assistance-complainant-history-refresh'', '
                || 'TIMESTAMP ''1970-01-01 00:00:00'', NULL, NULL)';
        END IF;
    END IF;
END;
/

COMMIT;

-- ── No new index on COMPLAINTS, and that is a measured result ────────────────────────────────────
-- The refresh reads complaints keyset-paged on the primary key (WHERE ID > :after ORDER BY ID), which
-- needs no index that does not already exist. It reads EVERY row by design — unlike the clause rollup it
-- cannot filter, because a complainant's FIRST complaint is as much a part of their filing history as
-- their tenth — so there is nothing for an index to seek.
--
-- A covering index on (COMPLAINANT_EMAIL, COMPLAINANT_PHONE, ENTITY_CODE, CREATED_AT) was considered and
-- rejected: another B-tree maintained on every single complaint write, for the benefit of one job that
-- runs every six hours over a table it scans in full anyway.
--
-- The REQUEST path touches COMPLAINTS exactly once, for the complaint the officer already has open, and
-- then reads ASSISTANCE_COMPLAINANT_HISTORY by IDX_ACH_EMAIL and IDX_ACH_PHONE. It issues no aggregate.
