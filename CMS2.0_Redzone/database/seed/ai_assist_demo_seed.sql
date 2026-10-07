-- =====================================================================================
-- ai_assist_demo_seed.sql
-- Additive demo/QA seed for the Brief 21 "assistance" rollups (clause affinity,
-- next-action prior, entity pattern) and for duplicate / repeat-complainant detection.
--
-- WHY THIS EXISTS
-- The assistance features are correct in code but SILENT on the dev register, because the
-- dimensions they key on are empty or degenerate. Measured on cms_db before this script:
--   category_id             273 / 4403 rows (6.2%), 10 distinct
--   ground_of_complaint_id  0 rows
--   closure_clause          877 rows, 5 distinct, 15(1)(a) = 833 (94.6%)  <- a near-constant
--   entity_code             876 of 877 clause-bearing rows hold 'Test Bank Ltd', which
--                           matches no row in REGULATED_ENTITIES
--   maintainability_determination  24 of 877 clause-bearing rows
--   scheme_version          absent on 4092 rows
--   performed_by_role       7531 / 17433 COMPLAINT_TIMELINE rows (43%)
-- A ranking feature over a 94.6% constant is correct and useless. This script supplies a
-- NON-DEGENERATE distribution so the floors (MIN_COHORT_SAMPLE = 5, MIN_CLAUSE_SHARE = 0.05,
-- MIN_CONFIDENCE = 0.5, MIN_OPEN_CASES = 5) are exercised for the first time.
--
-- ADDITIVE ONLY. This script issues no UPDATE and no DELETE against any pre-existing row.
-- The existing counts (4403 complaints / 877 clause-bearing / 821 CEPC closures / the 5
-- affinity rows) are asserted in documentation and possibly in e2e specs, so they are left
-- exactly as they are. Everything here is an INSERT of NEW rows.
--
-- TAG: every seeded complaint has complaint_number LIKE 'AIQA-%'  (AIQA-00001 .. AIQA-02195)
--      every seeded timeline row has remarks LIKE 'AIQA seed step %'
--      every seeded complainant_email is '...@qa.invalid.test'
--
-- ------------------------------------------------------------------------------------
-- EXACT ROLLBACK -- removes everything this script inserted and nothing else:
--
--   DELETE FROM complaint_timeline
--    WHERE complaint_id IN (SELECT id FROM complaints WHERE complaint_number LIKE 'AIQA-%');
--   DELETE FROM complaints WHERE complaint_number LIKE 'AIQA-%';
--   DROP TABLE IF EXISTS aiqa_seed_expand;
--   DROP TABLE IF EXISTS aiqa_seed_plan;
--   DROP TABLE IF EXISTS aiqa_seed_nums;
--   DROP TABLE IF EXISTS aiqa_seed_steps;
--
-- The rollup tables (ASSISTANCE_CLAUSE_AFFINITY, ASSISTANCE_NEXT_ACTION,
-- ASSISTANCE_ENTITY_PATTERN) are NOT touched here; they are derived, and the schedulers
-- rebuild them from whatever COMPLAINTS/COMPLAINT_TIMELINE hold. After a rollback, let the
-- schedulers run once (or truncate the rollups) so they stop reporting seeded cohorts.
-- ------------------------------------------------------------------------------------
--
-- IDEMPOTENT. Complaint numbers are a deterministic function of the plan, and every insert
-- is guarded by NOT EXISTS, so a second run inserts nothing. The helper tables are
-- DROP/CREATEd, so re-running rebuilds the plan rather than appending to it.
--
-- RUN:
--   "/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe" -u cms_user -pcms_pass cms_db \
--       < CMS2.0_Redzone/database/seed/ai_assist_demo_seed.sql
--
-- NOT A MIGRATION. This file deliberately lives under database/seed/ and carries no
-- V-number: it is demo/QA data, it must never run on a real register, and it must never be
-- picked up by a migration replay.
--
-- MASTER DATA NOTES (read the schema, do not guess -- these were verified, not assumed):
--   * COMPLAINTS.category_id resolves against `complaint_categories` (10 ACTIVE rows,
--     ids 1-10), NOT against CATEGORY_MASTER. CATEGORY_MASTER currently holds 6 INACTIVE
--     rows all named 'S1 authority e2e probe' and is not what the register references.
--     Ids 1-10 are used here, which is also the range the 273 pre-existing rows use.
--   * ground_of_complaint_id uses GROUND_OF_COMPLAINT_MASTER ids 1-10 (all active = 1,
--     scheme RBIOS_2021).
--   * closure_clause uses all 15 CLOSURE_CLAUSE_MASTER.clause_code values for RBIOS_2021.
--     Six of them are restricted_to_roles = 'OMBUDSMAN,RBIO_ADMIN,ADMIN':
--     15(1)(a), 15(1)(b), 16(2)(c), 16(2)(d), 16(2)(e), 16(2)(f). Those are seeded as the
--     TOP-RANKED clause in some cohorts on purpose, so the recommendation's role filter has
--     something to suppress -- a role filter that never has to suppress anything is untested.
--   * entity_code uses REAL REGULATED_ENTITIES names, so RegulatedEntity.normalize() finds
--     them and AssistanceClauseAffinityRefreshService can resolve an entity_type:
--       'HDFC Bank' / 'ICICI Bank' / 'Axis Bank' / 'Kotak Mahindra Bank' -> Private Sector Bank
--       'Punjab National Bank' / 'State Bank of India' / 'Canara Bank'
--         / 'Union Bank of India' / 'Indian Bank' / 'Bank of Baroda'    -> Public Sector Bank
--       'Bajaj Finance Limited' / 'Muthoot Finance Ltd'                 -> NBFC
--       'PhonePe Private Limited' / 'Paytm Payments Bank Limited'       -> Payment System Operator
--       'Saraswat Co-operative Bank Limited'                            -> Cooperative Bank
--       'Standard Chartered Bank'                                       -> Foreign Bank
--     AND, deliberately, the ALIAS spellings 'PNB', 'HDFC', 'SBI', 'BOB', so that
--     AssistanceEntityAliasNormaliser is exercised for real: it merges them
--     ('PNB' -> 'PUNJAB NATIONAL BANK', 'HDFC' -> 'HDFC BANK'), so the entity-pattern rollup
--     and the rail read must fold the alias rows into the full-name rows' cohort -- which
--     VERIFIED they now do, including against the pre-existing 'HDFC Bank' rows.
--
--     MEASURED CORRECTION, recorded because the obvious assumption is wrong: three of those
--     four aliases are THEMSELVES registered entities. REGULATED_ENTITIES ids 143/144/145 are
--     literally named 'SBI', 'PNB' and 'BOB' (all entity_type 'Public Sector Bank'), so
--     RegulatedEntity.normalize('PNB') = 'PNB' DOES match and the clause rollup resolves an
--     entity type for them rather than falling to the wildcard. The consequence is visible in
--     the data: cell K ('PNB') lands in the same L4 cohort as a Public Sector Bank, and cell M
--     ('SBI') MERGES with cell D ('State Bank of India'), making that cohort 216 closures with
--     a much closer three-way race (25.0% / 23.6% / 20.8%) than either cell alone. That is a
--     better ranking test than the one intended, so it is kept -- but it means the alias
--     coverage in the CLAUSE rollup comes from 'HDFC' alone, and the other three aliases are
--     only genuinely alias-folded in the ENTITY-PATTERN rollup. Do not "fix" the cells back
--     without re-reading REGULATED_ENTITIES first.
-- =====================================================================================

SET SESSION sql_mode = CONCAT(@@sql_mode, ',STRICT_ALL_TABLES');

-- -------------------------------------------------------------------------------------
-- 0. Helper tables. Regular (not TEMPORARY) tables, because the expansion below has to
--    reference the plan table twice in one statement and MySQL cannot reopen a TEMPORARY
--    table within a single query (ER_CANT_REOPEN_TABLE, 1137). They are dropped at the end.
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS aiqa_seed_expand;
DROP TABLE IF EXISTS aiqa_seed_plan;
DROP TABLE IF EXISTS aiqa_seed_nums;
DROP TABLE IF EXISTS aiqa_seed_steps;

CREATE TABLE aiqa_seed_nums (n INT NOT NULL PRIMARY KEY);
INSERT INTO aiqa_seed_nums (n)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 200)
SELECT n FROM s;

CREATE TABLE aiqa_seed_plan (
  plan_id      INT          NOT NULL PRIMARY KEY,
  cell_code    VARCHAR(8)   NOT NULL,
  kind         VARCHAR(8)   NOT NULL,          -- CLOSED | OPEN | DUP
  department   VARCHAR(20)  NOT NULL,
  category_id  BIGINT       NULL,
  ground_id    BIGINT       NULL,
  entity_code  VARCHAR(50)  NOT NULL,
  maint        VARCHAR(30)  NULL,              -- resolution path for the clause rollup
  office_code  VARCHAR(10)  NULL,
  clause       VARCHAR(100) NULL,              -- NULL for OPEN rows
  final_status VARCHAR(30)  NOT NULL,
  dup_cluster  INT          NULL,              -- repeat-complainant cluster, NULL otherwise
  copies       INT          NOT NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 1. THE CLAUSE DISTRIBUTION  (kind = 'CLOSED', 1629 rows)
--
--    Fifteen cohorts (A..O). Each is a distinct L4 key for
--    AssistanceClauseAffinityRefreshService:
--        scheme + department + category + ground + entityType + resolutionPath
--    and each gets a DIFFERENT mix of 5-6 clauses with a DIFFERENT winner, so the read's
--    ranking has to discriminate rather than echo a constant. Every share listed below is
--    deliberate:
--      * winners sit at 29-32%, never near 95%;
--      * cells M and N each carry a tail clause at ~4.2% / ~4.5%, just BELOW
--        MIN_CLAUSE_SHARE = 0.05, so the share floor is exercised as a REJECTION against a
--        realistic distribution for the first time (the existing data only ever made it
--        reject a 0.6% long tail);
--      * cell O holds only 3 closures, BELOW MIN_COHORT_SAMPLE = 5, so the sample floor is
--        exercised as a whole-cohort rejection and the read must fall back down the ladder.
-- -------------------------------------------------------------------------------------
INSERT INTO aiqa_seed_plan
  (plan_id, cell_code, kind, department, category_id, ground_id, entity_code, maint,
   office_code, clause, final_status, dup_cluster, copies)
VALUES
-- A  RBIO / ATM-Debit Card / Private Sector Bank / MAINTAINABLE  n=180
--    winner 16(1) 30% (unrestricted) over restricted 15(1)(a) 23.3%
 (101,'A','CLOSED','RBIO', 1, 1,'HDFC Bank','MAINTAINABLE','013','16(1)'   ,'closed',NULL, 54),
 (102,'A','CLOSED','RBIO', 1, 1,'HDFC Bank','MAINTAINABLE','013','15(1)(a)','closed',NULL, 42),
 (103,'A','CLOSED','RBIO', 1, 1,'HDFC Bank','MAINTAINABLE','013','16(3)'   ,'closed',NULL, 30),
 (104,'A','CLOSED','RBIO', 1, 1,'HDFC Bank','MAINTAINABLE','013','16(4)'   ,'closed',NULL, 24),
 (105,'A','CLOSED','RBIO', 1, 1,'HDFC Bank','MAINTAINABLE','013','15(1)(b)','closed',NULL, 18),
 (106,'A','CLOSED','RBIO', 1, 1,'HDFC Bank','MAINTAINABLE','013','16(2)(c)','closed',NULL, 12),
-- B  RBIO / Credit Card / Private Sector Bank / MAINTAINABLE  n=165
--    THE ROLE-FILTER COHORT: the top-ranked clause 15(1)(a) (30.9%) is RESTRICTED, so a
--    role outside OMBUDSMAN/RBIO_ADMIN/ADMIN must be offered 16(1) first and never see it.
 (111,'B','CLOSED','RBIO', 2, 2,'ICICI Bank','MAINTAINABLE','013','15(1)(a)','closed',NULL, 51),
 (112,'B','CLOSED','RBIO', 2, 2,'ICICI Bank','MAINTAINABLE','013','16(1)'   ,'closed',NULL, 36),
 (113,'B','CLOSED','RBIO', 2, 2,'ICICI Bank','MAINTAINABLE','013','16(2)(d)','closed',NULL, 27),
 (114,'B','CLOSED','RBIO', 2, 2,'ICICI Bank','MAINTAINABLE','013','16(3)'   ,'closed',NULL, 21),
 (115,'B','CLOSED','RBIO', 2, 2,'ICICI Bank','MAINTAINABLE','013','16(4)'   ,'closed',NULL, 18),
 (116,'B','CLOSED','RBIO', 2, 2,'ICICI Bank','MAINTAINABLE','013','16(2)(g)','closed',NULL, 12),
-- C  RBIO / Mobile-UPI / Public Sector Bank / NON_MAINTAINABLE  n=150  winner 16(2)(a) 30%
 (121,'C','CLOSED','RBIO', 4, 4,'Punjab National Bank','NON_MAINTAINABLE','014','16(2)(a)','rejected',NULL, 45),
 (122,'C','CLOSED','RBIO', 4, 4,'Punjab National Bank','NON_MAINTAINABLE','014','16(2)(b)','rejected',NULL, 33),
 (123,'C','CLOSED','RBIO', 4, 4,'Punjab National Bank','NON_MAINTAINABLE','014','16(2)(g)','rejected',NULL, 27),
 (124,'C','CLOSED','RBIO', 4, 4,'Punjab National Bank','NON_MAINTAINABLE','014','16(2)(h)','rejected',NULL, 21),
 (125,'C','CLOSED','RBIO', 4, 4,'Punjab National Bank','NON_MAINTAINABLE','014','10(1)(e)','rejected',NULL, 15),
 (126,'C','CLOSED','RBIO', 4, 4,'Punjab National Bank','NON_MAINTAINABLE','014','16(2)(e)','rejected',NULL,  9),
-- D  RBIO / Loan / Public Sector Bank / NON_MAINTAINABLE  n=144  winner 10(1)(g) 29.2%
--    Same resolution path as C, different category/ground/entity, DIFFERENT winner -- which
--    is what proves the ladder's upper rungs are doing work the L1 cohort could not do.
 (131,'D','CLOSED','RBIO', 5, 5,'State Bank of India','NON_MAINTAINABLE','014','10(1)(g)','rejected',NULL, 42),
 (132,'D','CLOSED','RBIO', 5, 5,'State Bank of India','NON_MAINTAINABLE','014','16(2)(b)','rejected',NULL, 33),
 (133,'D','CLOSED','RBIO', 5, 5,'State Bank of India','NON_MAINTAINABLE','014','16(2)(f)','rejected',NULL, 24),
 (134,'D','CLOSED','RBIO', 5, 5,'State Bank of India','NON_MAINTAINABLE','014','16(2)(a)','rejected',NULL, 21),
 (135,'D','CLOSED','RBIO', 5, 5,'State Bank of India','NON_MAINTAINABLE','014','10(1)(e)','rejected',NULL, 15),
 (136,'D','CLOSED','RBIO', 5, 5,'State Bank of India','NON_MAINTAINABLE','014','16(2)(h)','rejected',NULL,  9),
-- E  CEPC / Deposit Accounts / NBFC / MAINTAINABLE  n=135  winner 16(3) 31.1%
 (141,'E','CLOSED','CEPC', 6, 6,'Bajaj Finance Limited','MAINTAINABLE',NULL,'16(3)'   ,'closed',NULL, 42),
 (142,'E','CLOSED','CEPC', 6, 6,'Bajaj Finance Limited','MAINTAINABLE',NULL,'16(1)'   ,'closed',NULL, 30),
 (143,'E','CLOSED','CEPC', 6, 6,'Bajaj Finance Limited','MAINTAINABLE',NULL,'16(4)'   ,'closed',NULL, 24),
 (144,'E','CLOSED','CEPC', 6, 6,'Bajaj Finance Limited','MAINTAINABLE',NULL,'15(1)(a)','closed',NULL, 18),
 (145,'E','CLOSED','CEPC', 6, 6,'Bajaj Finance Limited','MAINTAINABLE',NULL,'16(2)(a)','closed',NULL, 12),
 (146,'E','CLOSED','CEPC', 6, 6,'Bajaj Finance Limited','MAINTAINABLE',NULL,'16(2)(b)','closed',NULL,  9),
-- F  CEPC / Remittance / NBFC / MAINTAINABLE  n=126  winner 16(4) 31.0%
 (151,'F','CLOSED','CEPC', 8, 8,'Muthoot Finance Ltd','MAINTAINABLE',NULL,'16(4)'   ,'resolved',NULL, 39),
 (152,'F','CLOSED','CEPC', 8, 8,'Muthoot Finance Ltd','MAINTAINABLE',NULL,'16(1)'   ,'resolved',NULL, 27),
 (153,'F','CLOSED','CEPC', 8, 8,'Muthoot Finance Ltd','MAINTAINABLE',NULL,'16(3)'   ,'resolved',NULL, 24),
 (154,'F','CLOSED','CEPC', 8, 8,'Muthoot Finance Ltd','MAINTAINABLE',NULL,'15(1)(b)','resolved',NULL, 18),
 (155,'F','CLOSED','CEPC', 8, 8,'Muthoot Finance Ltd','MAINTAINABLE',NULL,'16(2)(g)','resolved',NULL, 12),
 (156,'F','CLOSED','CEPC', 8, 8,'Muthoot Finance Ltd','MAINTAINABLE',NULL,'16(2)(h)','resolved',NULL,  6),
-- G  CEPC / Internet Banking / Payment System Operator / resolution path ABSENT  n=120
--    maint = NULL on purpose, so L4 and L3 reduce to one key and cohortsFor's
--    de-duplication is exercised on rows that DO have category/ground/entityType.
 (161,'G','CLOSED','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,'16(1)'   ,'closed',NULL, 36),
 (162,'G','CLOSED','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,'16(2)(a)','closed',NULL, 27),
 (163,'G','CLOSED','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,'16(3)'   ,'closed',NULL, 21),
 (164,'G','CLOSED','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,'16(4)'   ,'closed',NULL, 18),
 (165,'G','CLOSED','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,'15(1)(a)','closed',NULL, 12),
 (166,'G','CLOSED','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,'16(2)(b)','closed',NULL,  6),
-- H  CRPC / Pension / Public Sector Bank / MAINTAINABLE  n=108  winner 16(1) 30.6%
--    CRPC is seeded at all because department is an L1 dimension and CRPC held 58 rows with
--    no clause-bearing cohort, so the L1 ladder rung had nothing to offer that department.
 (171,'H','CLOSED','CRPC', 7, 7,'Canara Bank','MAINTAINABLE',NULL,'16(1)'   ,'closed',NULL, 33),
 (172,'H','CLOSED','CRPC', 7, 7,'Canara Bank','MAINTAINABLE',NULL,'16(3)'   ,'closed',NULL, 24),
 (173,'H','CLOSED','CRPC', 7, 7,'Canara Bank','MAINTAINABLE',NULL,'15(1)(a)','closed',NULL, 21),
 (174,'H','CLOSED','CRPC', 7, 7,'Canara Bank','MAINTAINABLE',NULL,'16(4)'   ,'closed',NULL, 15),
 (175,'H','CLOSED','CRPC', 7, 7,'Canara Bank','MAINTAINABLE',NULL,'16(2)(c)','closed',NULL,  9),
 (176,'H','CLOSED','CRPC', 7, 7,'Canara Bank','MAINTAINABLE',NULL,'16(2)(d)','closed',NULL,  6),
-- I  RBIO / Insurance / Private Sector Bank / MAINTAINABLE  n=102
--    SECOND role-filter cohort: winner 15(1)(b) 29.4% is restricted, and 15(1)(a) sits at
--    rank 4, so a non-entitled role loses TWO entries and the ranking must close the gap
--    rather than leave a hole.
 (181,'I','CLOSED','RBIO', 9, 9,'Axis Bank','MAINTAINABLE','013','15(1)(b)','adjudicated',NULL, 30),
 (182,'I','CLOSED','RBIO', 9, 9,'Axis Bank','MAINTAINABLE','013','16(1)'   ,'adjudicated',NULL, 24),
 (183,'I','CLOSED','RBIO', 9, 9,'Axis Bank','MAINTAINABLE','013','16(3)'   ,'adjudicated',NULL, 18),
 (184,'I','CLOSED','RBIO', 9, 9,'Axis Bank','MAINTAINABLE','013','15(1)(a)','adjudicated',NULL, 15),
 (185,'I','CLOSED','RBIO', 9, 9,'Axis Bank','MAINTAINABLE','013','16(4)'   ,'adjudicated',NULL,  9),
 (186,'I','CLOSED','RBIO', 9, 9,'Axis Bank','MAINTAINABLE','013','16(2)(e)','adjudicated',NULL,  6),
-- J  RBIO / Others / Private Sector Bank / NON_MAINTAINABLE  n=96  winner 16(2)(h) 31.3%
 (191,'J','CLOSED','RBIO',10,10,'Kotak Mahindra Bank','NON_MAINTAINABLE','016','16(2)(h)','rejected',NULL, 30),
 (192,'J','CLOSED','RBIO',10,10,'Kotak Mahindra Bank','NON_MAINTAINABLE','016','16(2)(g)','rejected',NULL, 21),
 (193,'J','CLOSED','RBIO',10,10,'Kotak Mahindra Bank','NON_MAINTAINABLE','016','16(2)(a)','rejected',NULL, 18),
 (194,'J','CLOSED','RBIO',10,10,'Kotak Mahindra Bank','NON_MAINTAINABLE','016','16(2)(b)','rejected',NULL, 12),
 (195,'J','CLOSED','RBIO',10,10,'Kotak Mahindra Bank','NON_MAINTAINABLE','016','16(2)(f)','rejected',NULL,  9),
 (196,'J','CLOSED','RBIO',10,10,'Kotak Mahindra Bank','NON_MAINTAINABLE','016','10(1)(e)','rejected',NULL,  6),
-- K  ALIAS 'PNB' -- RBIO / ATM / entity unresolvable -> wildcard entityType  n=84
 (201,'K','CLOSED','RBIO', 1, 1,'PNB','MAINTAINABLE','014','16(1)'   ,'closed',NULL, 27),
 (202,'K','CLOSED','RBIO', 1, 1,'PNB','MAINTAINABLE','014','15(1)(a)','closed',NULL, 21),
 (203,'K','CLOSED','RBIO', 1, 1,'PNB','MAINTAINABLE','014','16(3)'   ,'closed',NULL, 15),
 (204,'K','CLOSED','RBIO', 1, 1,'PNB','MAINTAINABLE','014','16(4)'   ,'closed',NULL, 12),
 (205,'K','CLOSED','RBIO', 1, 1,'PNB','MAINTAINABLE','014','15(1)(b)','closed',NULL,  9),
-- L  ALIAS 'HDFC' -- CEPC / Credit Card  n=78
 (211,'L','CLOSED','CEPC', 2, 2,'HDFC','MAINTAINABLE',NULL,'16(3)'   ,'closed',NULL, 24),
 (212,'L','CLOSED','CEPC', 2, 2,'HDFC','MAINTAINABLE',NULL,'16(1)'   ,'closed',NULL, 21),
 (213,'L','CLOSED','CEPC', 2, 2,'HDFC','MAINTAINABLE',NULL,'16(4)'   ,'closed',NULL, 15),
 (214,'L','CLOSED','CEPC', 2, 2,'HDFC','MAINTAINABLE',NULL,'15(1)(a)','closed',NULL, 12),
 (215,'L','CLOSED','CEPC', 2, 2,'HDFC','MAINTAINABLE',NULL,'16(2)(a)','closed',NULL,  6),
-- M  ALIAS 'SBI' -- RBIO / Loan / NON_MAINTAINABLE  n=72
--    The 10(1)(e) tail is 3/72 = 4.17%, BELOW MIN_CLAUSE_SHARE. Expected: rejected while
--    the other five clauses in the same cohort are written.
 (221,'M','CLOSED','RBIO', 5, 5,'SBI','NON_MAINTAINABLE','013','16(2)(a)','rejected',NULL, 24),
 (222,'M','CLOSED','RBIO', 5, 5,'SBI','NON_MAINTAINABLE','013','16(2)(b)','rejected',NULL, 18),
 (223,'M','CLOSED','RBIO', 5, 5,'SBI','NON_MAINTAINABLE','013','10(1)(g)','rejected',NULL, 12),
 (224,'M','CLOSED','RBIO', 5, 5,'SBI','NON_MAINTAINABLE','013','16(2)(g)','rejected',NULL,  9),
 (225,'M','CLOSED','RBIO', 5, 5,'SBI','NON_MAINTAINABLE','013','16(2)(h)','rejected',NULL,  6),
 (226,'M','CLOSED','RBIO', 5, 5,'SBI','NON_MAINTAINABLE','013','10(1)(e)','rejected',NULL,  3),
-- N  ALIAS 'BOB' -- CEPC / Deposit Accounts  n=66
--    Two tails at 3/66 = 4.55%, below the share floor. Expected: both rejected.
 (231,'N','CLOSED','CEPC', 6, 6,'BOB','MAINTAINABLE',NULL,'16(1)'   ,'closed',NULL, 21),
 (232,'N','CLOSED','CEPC', 6, 6,'BOB','MAINTAINABLE',NULL,'16(3)'   ,'closed',NULL, 18),
 (233,'N','CLOSED','CEPC', 6, 6,'BOB','MAINTAINABLE',NULL,'16(4)'   ,'closed',NULL, 12),
 (234,'N','CLOSED','CEPC', 6, 6,'BOB','MAINTAINABLE',NULL,'15(1)(a)','closed',NULL,  9),
 (235,'N','CLOSED','CEPC', 6, 6,'BOB','MAINTAINABLE',NULL,'16(2)(b)','closed',NULL,  3),
 (236,'N','CLOSED','CEPC', 6, 6,'BOB','MAINTAINABLE',NULL,'16(2)(g)','closed',NULL,  3),
-- O  A COHORT THAT MUST STAY SILENT: 3 closures, below MIN_COHORT_SAMPLE = 5.
--    Expected: nothing written at L4/L3 or L2 for (CEPC, cat 9, ground 3), and the read
--    falls down the ladder to L1 (RBIOS_2021, CEPC).
 (241,'O','CLOSED','CEPC', 9, 3,'Muthoot Finance Ltd',NULL,NULL,'16(1)','closed',NULL,  2),
 (242,'O','CLOSED','CEPC', 9, 3,'Muthoot Finance Ltd',NULL,NULL,'16(3)','closed',NULL,  1);

-- -------------------------------------------------------------------------------------
-- 2. OPEN complaints  (kind = 'OPEN', 500 rows)
--
--    The entity-pattern rollup counts OPEN cases per
--    (entityKey, ground, quarter, department, officeCode) and needs MIN_OPEN_CASES = 5 per
--    window. Closed complaints cannot supply that, so these exist. Statuses are drawn from
--    the register's real open vocabulary -- RbioStatusVocabulary.LEGACY_CLOSED is
--    (resolved, closed, rejected, withdrawn, adjudicated, conciliated), so everything here
--    is genuinely open rather than open-looking.
-- -------------------------------------------------------------------------------------
INSERT INTO aiqa_seed_plan
  (plan_id, cell_code, kind, department, category_id, ground_id, entity_code, maint,
   office_code, clause, final_status, dup_cluster, copies)
VALUES
 (301,'P01','OPEN','RBIO', 1, 1,'HDFC Bank'                        ,NULL,'013',NULL,'assigned'       ,NULL,36),
 (302,'P02','OPEN','RBIO', 2, 2,'ICICI Bank'                       ,NULL,'013',NULL,'in_progress'    ,NULL,32),
 (303,'P03','OPEN','RBIO', 4, 4,'Punjab National Bank'             ,NULL,'014',NULL,'reviewer_review',NULL,32),
 (304,'P04','OPEN','RBIO', 5, 5,'State Bank of India'              ,NULL,'014',NULL,'incharge_review',NULL,28),
 (305,'P05','OPEN','CEPC', 6, 6,'Bajaj Finance Limited'            ,NULL,NULL ,NULL,'assigned'       ,NULL,32),
 (306,'P06','OPEN','CEPC', 8, 8,'Muthoot Finance Ltd'              ,NULL,NULL ,NULL,'in_progress'    ,NULL,28),
 (307,'P07','OPEN','CEPC', 3, 3,'PhonePe Private Limited'          ,NULL,NULL ,NULL,'pending'        ,NULL,28),
 (308,'P08','OPEN','CRPC', 7, 7,'Canara Bank'                      ,NULL,NULL ,NULL,'assigned'       ,NULL,24),
 (309,'P09','OPEN','RBIO', 9, 9,'Axis Bank'                        ,NULL,'013',NULL,'escalated'      ,NULL,24),
 (310,'P10','OPEN','RBIO',10,10,'Kotak Mahindra Bank'              ,NULL,'016',NULL,'awaiting_closure',NULL,24),
 (311,'P11','OPEN','RBIO', 1, 1,'PNB'                              ,NULL,'014',NULL,'assigned'       ,NULL,24),
 (312,'P12','OPEN','CEPC', 2, 2,'HDFC'                             ,NULL,NULL ,NULL,'in_progress'    ,NULL,24),
 (313,'P13','OPEN','RBIO', 5, 5,'SBI'                              ,NULL,'013',NULL,'reviewer_review',NULL,24),
 (314,'P14','OPEN','CEPC', 6, 6,'BOB'                              ,NULL,NULL ,NULL,'pending'        ,NULL,20),
 (315,'P15','OPEN','RBIO', 3, 3,'Union Bank of India'              ,NULL,'017',NULL,'assigned'       ,NULL,20),
 (316,'P16','OPEN','CEPC', 4, 4,'Saraswat Co-operative Bank Limited',NULL,NULL ,NULL,'in_progress'   ,NULL,20),
 (317,'P17','OPEN','CEPC', 5, 5,'Standard Chartered Bank'          ,NULL,NULL ,NULL,'assigned'       ,NULL,20),
 (318,'P18','OPEN','RBIO', 8, 8,'Indian Bank'                      ,NULL,'011',NULL,'in_progress'    ,NULL,20),
 (319,'P19','OPEN','CEPC',10,10,'Paytm Payments Bank Limited'      ,NULL,NULL ,NULL,'pending'        ,NULL,20),
 (320,'P20','OPEN','RBIO', 7, 7,'Bank of Baroda'                   ,NULL,'021',NULL,'assigned'       ,NULL,20);

-- -------------------------------------------------------------------------------------
-- 3. DUPLICATE / REPEAT-FILING fixtures  (kind = 'DUP', 66 rows)
--
--    Twelve complainants with 4 complaints EACH against the SAME entity, every one filed
--    inside a 30-day window (copy n is base_date - 6n days, so n=1..4 spans 6..24 days).
--    Plus one high-volume repeat filer with 18 complaints over ~54 days against mixed
--    entities. Before this, 154 emails had more than one complaint and the maximum was 25,
--    but none of those clusters were same-entity-inside-30-days, so duplicate detection and
--    the repeat-complainant signal had nothing true to find.
-- -------------------------------------------------------------------------------------
INSERT INTO aiqa_seed_plan
  (plan_id, cell_code, kind, department, category_id, ground_id, entity_code, maint,
   office_code, clause, final_status, dup_cluster, copies)
VALUES
 (401,'D01','DUP','RBIO', 1, 1,'HDFC Bank'            ,NULL,'013',NULL,'assigned'   , 1, 4),
 (402,'D02','DUP','RBIO', 2, 2,'ICICI Bank'           ,NULL,'013',NULL,'in_progress', 2, 4),
 (403,'D03','DUP','RBIO', 4, 4,'Punjab National Bank' ,NULL,'014',NULL,'assigned'   , 3, 4),
 (404,'D04','DUP','RBIO', 5, 5,'State Bank of India'  ,NULL,'014',NULL,'pending'    , 4, 4),
 (405,'D05','DUP','CEPC', 6, 6,'Bajaj Finance Limited',NULL,NULL ,NULL,'assigned'   , 5, 4),
 (406,'D06','DUP','CEPC', 8, 8,'Muthoot Finance Ltd'  ,NULL,NULL ,NULL,'in_progress', 6, 4),
 (407,'D07','DUP','CEPC', 3, 3,'PhonePe Private Limited',NULL,NULL,NULL,'assigned'  , 7, 4),
 (408,'D08','DUP','RBIO', 9, 9,'Axis Bank'            ,NULL,'013',NULL,'assigned'   , 8, 4),
 (409,'D09','DUP','RBIO',10,10,'Kotak Mahindra Bank'  ,NULL,'016',NULL,'pending'    , 9, 4),
 (410,'D10','DUP','RBIO', 1, 1,'PNB'                  ,NULL,'014',NULL,'assigned'   ,10, 4),
 (411,'D11','DUP','CEPC', 2, 2,'HDFC'                 ,NULL,NULL ,NULL,'in_progress',11, 4),
 (412,'D12','DUP','RBIO', 3, 3,'Union Bank of India'  ,NULL,'017',NULL,'assigned'   ,12, 4),
 (413,'D13','DUP','RBIO', 1, 1,'HDFC Bank'            ,NULL,'013',NULL,'assigned'   ,99,18);

-- -------------------------------------------------------------------------------------
-- 4. Expand the plan into one row per complaint, with a DETERMINISTIC sequence number.
--    seq = (sum of copies of all lower plan_ids) + copy_no, so complaint_number is a pure
--    function of the plan. That is what makes the NOT EXISTS guards below a real
--    idempotency guarantee rather than a hope.
-- -------------------------------------------------------------------------------------
CREATE TABLE aiqa_seed_expand (
  seq          INT          NOT NULL PRIMARY KEY,
  cell_code    VARCHAR(8)   NOT NULL,
  kind         VARCHAR(8)   NOT NULL,
  department   VARCHAR(20)  NOT NULL,
  category_id  BIGINT       NULL,
  ground_id    BIGINT       NULL,
  entity_code  VARCHAR(50)  NOT NULL,
  maint        VARCHAR(30)  NULL,
  office_code  VARCHAR(10)  NULL,
  clause       VARCHAR(100) NULL,
  final_status VARCHAR(30)  NOT NULL,
  dup_cluster  INT          NULL,
  copy_no      INT          NOT NULL,
  KEY idx_aiqa_kind (kind)
) ENGINE=InnoDB;

INSERT INTO aiqa_seed_expand
  (seq, cell_code, kind, department, category_id, ground_id, entity_code, maint,
   office_code, clause, final_status, dup_cluster, copy_no)
SELECT
  (SELECT COALESCE(SUM(q.copies), 0) FROM aiqa_seed_plan q WHERE q.plan_id < p.plan_id) + n.n,
  p.cell_code, p.kind, p.department, p.category_id, p.ground_id, p.entity_code, p.maint,
  p.office_code, p.clause, p.final_status, p.dup_cluster, n.n
FROM aiqa_seed_plan p
JOIN aiqa_seed_nums n ON n.n <= p.copies;

-- -------------------------------------------------------------------------------------
-- 5. INSERT the complaints.
--
--    created_at spreads differ by kind on purpose:
--      OPEN  MOD(seq,8) * 30 days  -> ~3 quarters, so an entity-pattern window of 20-36
--            complaints still puts 7+ OPEN cases in each quarter and clears MIN_OPEN_CASES.
--            A wider spread would thin every window below the floor and the rollup would go
--            silent for the exact reason this seed exists.
--      CLOSED MOD(seq,24) * 23 days -> ~18 months. The clause rollup does not key on
--            quarter, so breadth is free here and makes the register look less synthetic.
--      DUP   base - 6*copy_no (or 3*copy_no for the high-volume filer) -> a TIGHT cluster,
--            which is the whole point of the fixture.
-- -------------------------------------------------------------------------------------
INSERT INTO complaints (
  complaint_number, complainant_name, complainant_email, complainant_phone,
  complainant_state, complainant_district, declaration_accepted,
  subject, description, relief_sought,
  status, workflow_stage, department, assigned_role, assigned_officer,
  category_id, ground_of_complaint_id, entity_code, rbio_office_code,
  maintainability_determination, maintainability_determined_at, maintainability_determined_by,
  scheme_version, filing_type, priority, sla_priority,
  closure_clause, closure_cause, closure_authority_name, closure_authority_designation,
  created_at, updated_at, filed_at, stage_assigned_at, last_status_change_date,
  sla_deadline, current_stage_deadline, closed_at, resolved_at,
  prior_re_complaint, re_replied_and_dissatisfied, reopen_count, record_version
)
SELECT
  CONCAT('AIQA-', LPAD(e.seq, 5, '0')),
  CASE WHEN e.dup_cluster = 99 THEN 'QA Bulk Filer Zero Nine Nine'
       WHEN e.dup_cluster IS NOT NULL THEN CONCAT('QA Repeat Filer ', LPAD(e.dup_cluster, 2, '0'))
       ELSE CONCAT('QA Test Complainant ', LPAD(e.seq, 5, '0')) END,
  -- .invalid is the RFC 2606 reserved TLD: these addresses cannot resolve and cannot
  -- belong to a real person, which is what "must look like test data" has to mean for a
  -- column that notices get sent to.
  CASE WHEN e.dup_cluster = 99 THEN 'aiqa.bulkfiler@qa.invalid.test'
       WHEN e.dup_cluster IS NOT NULL THEN CONCAT('aiqa.repeat', LPAD(e.dup_cluster, 2, '0'), '@qa.invalid.test')
       ELSE CONCAT('aiqa.c', LPAD(e.seq, 5, '0'), '@qa.invalid.test') END,
  -- Sequential, obviously-synthetic 10-digit numbers in the 90000xxxxx block.
  CASE WHEN e.dup_cluster = 99 THEN '9000099099'
       WHEN e.dup_cluster IS NOT NULL THEN CONCAT('90000', LPAD(e.dup_cluster, 5, '0'))
       ELSE CONCAT('9', LPAD(e.seq, 9, '0')) END,
  ELT(1 + MOD(e.seq, 6), 'Maharashtra','Karnataka','Delhi','Tamil Nadu','Gujarat','West Bengal'),
  ELT(1 + MOD(e.seq, 6), 'Mumbai','Bengaluru','New Delhi','Chennai','Ahmedabad','Kolkata'),
  1,
  CONCAT(ELT(e.category_id,
      'Disputed ATM withdrawal not reversed',
      'Credit card charge disputed and not resolved',
      'Internet banking transfer debited twice',
      'UPI payment debited but not credited to payee',
      'Loan EMI debited after foreclosure',
      'Fixed deposit maturity proceeds not credited',
      'Pension credit delayed for three months',
      'NEFT remittance not credited to beneficiary',
      'Insurance premium debited without consent',
      'Service deficiency not addressed by the branch'),
    ' [', e.entity_code, ']'),
  CONCAT('QA seed record ', e.cell_code, '/', e.copy_no,
         '. Synthetic complaint narrative for assistance-rollup coverage. Entity: ',
         e.entity_code, '. Department: ', e.department, '.'),
  ELT(1 + MOD(e.seq, 4), 'Refund of the disputed amount',
                         'Reversal and compensation for the delay',
                         'Correction of the account entry',
                         'Written explanation and closure'),
  e.final_status,
  CASE e.kind WHEN 'CLOSED' THEN 'CLOSED' ELSE UPPER(e.final_status) END,
  e.department,
  CASE e.department WHEN 'RBIO' THEN 'RBIO_DEALING_OFFICIAL'
                    WHEN 'CRPC' THEN 'CRPC_HEAD'
                    ELSE 'CEPC_DO' END,
  CONCAT('qa.officer', LPAD(1 + MOD(e.seq, 12), 2, '0'), '@', LOWER(e.department), '.test'),
  e.category_id,
  e.ground_id,
  e.entity_code,
  e.office_code,
  e.maint,
  CASE WHEN e.maint IS NULL THEN NULL
       ELSE DATE_SUB(TIMESTAMP('2026-09-15 10:00:00'), INTERVAL (MOD(e.seq, 24) * 23 - 10) DAY) END,
  CASE WHEN e.maint IS NULL THEN NULL ELSE 'qa.maintainability@rbi.test' END,
  'RBIOS_2021',
  ELT(1 + MOD(e.seq, 3), 'ONLINE', 'EMAIL', 'LETTER'),
  ELT(1 + MOD(e.seq, 3), 'medium', 'high', 'low'),
  ELT(1 + MOD(e.seq, 3), 'P2', 'P1', 'P3'),
  e.clause,
  CASE WHEN e.clause IS NULL THEN NULL
       WHEN e.maint = 'NON_MAINTAINABLE' THEN 'NON_MAINTAINABLE'
       ELSE 'RESOLVED' END,
  CASE WHEN e.clause IS NULL THEN NULL ELSE 'QA Closure Authority' END,
  CASE WHEN e.clause IS NULL THEN NULL
       WHEN e.department = 'RBIO' THEN 'Ombudsman'
       ELSE 'General Manager' END,
  -- created_at
  CASE e.kind
    WHEN 'DUP'  THEN DATE_SUB(TIMESTAMP('2026-08-20 09:30:00'),
                              INTERVAL (CASE WHEN e.dup_cluster = 99 THEN 3 ELSE 6 END * e.copy_no) DAY)
    WHEN 'OPEN' THEN DATE_SUB(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (MOD(e.seq, 8) * 30 + MOD(e.seq, 7)) DAY)
    ELSE             DATE_SUB(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (MOD(e.seq, 24) * 23 + MOD(e.seq, 7)) DAY)
  END,
  TIMESTAMP('2026-09-30 12:00:00'),
  -- filed_at mirrors created_at
  CASE e.kind
    WHEN 'DUP'  THEN DATE_SUB(TIMESTAMP('2026-08-20 09:30:00'),
                              INTERVAL (CASE WHEN e.dup_cluster = 99 THEN 3 ELSE 6 END * e.copy_no) DAY)
    WHEN 'OPEN' THEN DATE_SUB(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (MOD(e.seq, 8) * 30 + MOD(e.seq, 7)) DAY)
    ELSE             DATE_SUB(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (MOD(e.seq, 24) * 23 + MOD(e.seq, 7)) DAY)
  END,
  CASE e.kind
    WHEN 'DUP'  THEN DATE_SUB(TIMESTAMP('2026-08-22 09:30:00'),
                              INTERVAL (CASE WHEN e.dup_cluster = 99 THEN 3 ELSE 6 END * e.copy_no) DAY)
    WHEN 'OPEN' THEN DATE_SUB(TIMESTAMP('2026-09-17 10:00:00'),
                              INTERVAL (MOD(e.seq, 8) * 30 + MOD(e.seq, 7)) DAY)
    ELSE             DATE_SUB(TIMESTAMP('2026-09-17 10:00:00'),
                              INTERVAL (MOD(e.seq, 24) * 23 + MOD(e.seq, 7)) DAY)
  END,
  CASE e.kind
    WHEN 'DUP'  THEN DATE_SUB(TIMESTAMP('2026-08-22 09:30:00'),
                              INTERVAL (CASE WHEN e.dup_cluster = 99 THEN 3 ELSE 6 END * e.copy_no) DAY)
    WHEN 'OPEN' THEN DATE_SUB(TIMESTAMP('2026-09-17 10:00:00'),
                              INTERVAL (MOD(e.seq, 8) * 30 + MOD(e.seq, 7)) DAY)
    ELSE             DATE_ADD(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (45 - MOD(e.seq, 24) * 23 - MOD(e.seq, 7)) DAY)
  END,
  -- sla_deadline: created_at + 30 days. Deliberately in the PAST for the older OPEN rows,
  -- so the deadline-triage surface has genuinely overdue work and not only future dates.
  CASE e.kind
    WHEN 'DUP'  THEN DATE_ADD(TIMESTAMP('2026-08-20 09:30:00'),
                              INTERVAL (30 - (CASE WHEN e.dup_cluster = 99 THEN 3 ELSE 6 END * e.copy_no)) DAY)
    WHEN 'OPEN' THEN DATE_ADD(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (30 - MOD(e.seq, 8) * 30 - MOD(e.seq, 7)) DAY)
    ELSE             DATE_ADD(TIMESTAMP('2026-09-15 10:00:00'),
                              INTERVAL (30 - MOD(e.seq, 24) * 23 - MOD(e.seq, 7)) DAY)
  END,
  CASE e.kind
    WHEN 'CLOSED' THEN NULL
    WHEN 'DUP'    THEN DATE_ADD(TIMESTAMP('2026-08-20 09:30:00'),
                                INTERVAL (20 - (CASE WHEN e.dup_cluster = 99 THEN 3 ELSE 6 END * e.copy_no)) DAY)
    ELSE               DATE_ADD(TIMESTAMP('2026-09-15 10:00:00'),
                                INTERVAL (20 - MOD(e.seq, 8) * 30 - MOD(e.seq, 7)) DAY)
  END,
  CASE WHEN e.kind = 'CLOSED'
       THEN DATE_ADD(TIMESTAMP('2026-09-15 10:00:00'),
                     INTERVAL (45 - MOD(e.seq, 24) * 23 - MOD(e.seq, 7)) DAY) END,
  CASE WHEN e.kind = 'CLOSED' AND e.maint <> 'NON_MAINTAINABLE'
       THEN DATE_ADD(TIMESTAMP('2026-09-15 10:00:00'),
                     INTERVAL (44 - MOD(e.seq, 24) * 23 - MOD(e.seq, 7)) DAY) END,
  CASE WHEN MOD(e.seq, 2) = 0 THEN b'1' ELSE b'0' END,
  CASE WHEN MOD(e.seq, 3) = 0 THEN b'1' ELSE b'0' END,
  0,
  0
FROM aiqa_seed_expand e
WHERE NOT EXISTS (
  SELECT 1 FROM complaints c WHERE c.complaint_number = CONCAT('AIQA-', LPAD(e.seq, 5, '0')));

-- -------------------------------------------------------------------------------------
-- 6. COMPLAINT_TIMELINE, with performed_by_role populated on EVERY row.
--
--    AssistanceNextActionRefreshService keys on (from_status, performed_by_role, category_id)
--    and needs MIN_COHORT_SAMPLE = 5 events AND a winner holding MIN_CONFIDENCE = 0.5 of the
--    cohort. The action at each step is therefore varied by MOD(seq, k) so the winner lands
--    between 60% and 86% -- a real plurality rather than a 100% cohort, which would clear the
--    floor without ever testing it.
--
--    Step 7 is the opposite case ON PURPOSE: for the 'pending' cells it writes a four-way
--    35/30/20/15 split under (pending, CEPC_ADMIN), whose winner is BELOW MIN_CONFIDENCE.
--    Expected outcome: that cohort is REJECTED and nothing is written for it. Until now the
--    confidence floor had only ever rejected thin cohorts, never a genuinely indecisive one.
--
--    Roles are the register's REAL role strings (CEPC_DO, CEPC_REVIEWER, CEPC_INCHARGE,
--    CEPC_CLOSING_AUTHORITY, CEPC_ADMIN, RBIO_DEALING_OFFICIAL, RBIO_REVIEWER,
--    RBIO_DEPUTY_OMBUDSMAN, RBIO_OMBUDSMAN, RBIO_ADMIN, CRPC_HEAD) -- an invented role would
--    key cohorts no read could ever reach.
-- -------------------------------------------------------------------------------------
CREATE TABLE aiqa_seed_steps (
  step_no     INT         NOT NULL PRIMARY KEY,
  from_status VARCHAR(30) NOT NULL,
  to_status   VARCHAR(30) NOT NULL,
  role_slot   VARCHAR(20) NOT NULL,   -- ADMIN | DO | REVIEWER | INCHARGE | CLOSER
  day_offset  INT         NOT NULL,
  applies_to  VARCHAR(16) NOT NULL    -- ALL | CLOSED | PENDING
) ENGINE=InnoDB;

INSERT INTO aiqa_seed_steps VALUES
 (1,'new'            ,'assigned'        ,'ADMIN'   , 0,'ALL'),
 (2,'assigned'       ,'in_progress'     ,'DO'      , 2,'ALL'),
 (3,'in_progress'    ,'reviewer_review' ,'DO'      , 7,'ALL'),
 (4,'reviewer_review','incharge_review' ,'REVIEWER',14,'CLOSED'),
 (5,'incharge_review','awaiting_closure','INCHARGE',21,'CLOSED'),
 (6,'awaiting_closure','closed'         ,'CLOSER'  ,30,'CLOSED'),
 (7,'pending'        ,'pending'         ,'ADMIN'   , 4,'PENDING');

INSERT INTO complaint_timeline
  (complaint_id, action, from_status, to_status, performed_by, performed_by_role,
   performed_at, remarks, event_source, closure_clause)
SELECT
  c.id,
  CASE s.step_no
    WHEN 1 THEN 'CREATED'
    WHEN 2 THEN CASE WHEN MOD(e.seq, 6) = 5 THEN 'ESCALATE' ELSE 'ACCEPT' END
    WHEN 3 THEN CASE MOD(e.seq, 5)
                  WHEN 3 THEN 'QUERY_RAISED_CLARIFICATION'
                  WHEN 4 THEN 'QUERY_RAISED_DOCUMENT_REQUEST'
                  ELSE 'SUBMIT_FOR_REVIEW' END
    WHEN 4 THEN CASE WHEN MOD(e.seq, 7) = 6 THEN 'SEND_BACK_DO' ELSE 'APPROVE_REVIEW' END
    WHEN 5 THEN CASE WHEN MOD(e.seq, 8) = 7 THEN 'SEND_BACK_DO' ELSE 'APPROVE_CLOSURE' END
    WHEN 6 THEN CASE WHEN MOD(e.seq, 4) = 3 THEN 'RESOLVE' ELSE 'CLOSE_COMPLAINT' END
    ELSE        CASE WHEN MOD(e.seq,20) <  7 THEN 'REMINDER_SENT'
                     WHEN MOD(e.seq,20) < 13 THEN 'FORWARD_DEPT'
                     WHEN MOD(e.seq,20) < 17 THEN 'ESCALATE'
                     ELSE 'QUERY_RAISED_CLARIFICATION' END
  END,
  s.from_status,
  CASE WHEN s.step_no = 6 THEN e.final_status ELSE s.to_status END,
  CONCAT('qa.', LOWER(e.department), '.step', s.step_no, '@rbi.test'),
  CASE e.department
    WHEN 'CRPC' THEN 'CRPC_HEAD'
    WHEN 'RBIO' THEN CASE s.role_slot
                       WHEN 'ADMIN'    THEN 'RBIO_ADMIN'
                       WHEN 'DO'       THEN 'RBIO_DEALING_OFFICIAL'
                       WHEN 'REVIEWER' THEN 'RBIO_REVIEWER'
                       WHEN 'INCHARGE' THEN 'RBIO_DEPUTY_OMBUDSMAN'
                       ELSE 'RBIO_OMBUDSMAN' END
    ELSE             CASE s.role_slot
                       WHEN 'ADMIN'    THEN 'CEPC_ADMIN'
                       WHEN 'DO'       THEN 'CEPC_DO'
                       WHEN 'REVIEWER' THEN 'CEPC_REVIEWER'
                       WHEN 'INCHARGE' THEN 'CEPC_INCHARGE'
                       ELSE 'CEPC_CLOSING_AUTHORITY' END
  END,
  DATE_ADD(c.created_at, INTERVAL s.day_offset DAY),
  CONCAT('AIQA seed step ', s.step_no),
  'MANUAL',
  CASE WHEN s.step_no = 6 THEN e.clause END
FROM aiqa_seed_expand e
JOIN complaints c ON c.complaint_number = CONCAT('AIQA-', LPAD(e.seq, 5, '0'))
JOIN aiqa_seed_steps s
  ON s.applies_to = 'ALL'
  OR (s.applies_to = 'CLOSED'  AND e.kind = 'CLOSED')
  OR (s.applies_to = 'PENDING' AND e.final_status = 'pending')
WHERE NOT EXISTS (
  SELECT 1 FROM complaint_timeline t
   WHERE t.complaint_id = c.id
     AND t.remarks = CONCAT('AIQA seed step ', s.step_no));

-- -------------------------------------------------------------------------------------
-- 7. Drop the helpers. The plan is reproducible from this file, so leaving three scratch
--    tables in cms_db for other sessions to trip over buys nothing.
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS aiqa_seed_expand;
DROP TABLE IF EXISTS aiqa_seed_plan;
DROP TABLE IF EXISTS aiqa_seed_nums;
DROP TABLE IF EXISTS aiqa_seed_steps;
