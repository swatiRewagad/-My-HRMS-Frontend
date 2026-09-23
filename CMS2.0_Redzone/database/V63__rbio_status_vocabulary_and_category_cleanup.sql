-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V63 — Complete the RBIO status vocabulary, and de-pollute CATEGORY_MASTER.
--
-- ══ PART 1: THE MISSING STATUSES ════════════════════════════════════════════════════════════════
--
-- RBIO_STATUS_MASTER holds 34 rows, but live complaints carry status strings that resolve to no row at
-- all, so the grid cannot label them, the status filter cannot offer them and closure semantics
-- (IS_CLOSED) are undefined for them.
--
-- MEASURED 2026-09-22 against cms_db. The brief said "13 status strings on 383 complaints". Both
-- figures needed correcting, and the distinction matters:
--   * 13 values match no STATUS_CODE, covering 395 complaints (not 383 — the DB is live and still
--     accreting, so do not hardcode either number).
--   * but 5 of those 13 ALREADY RESOLVE through the LEGACY_VALUE column, which is the join key the
--     readers actually use: pending->NEW_COMPLAINT, adjudicated->AWARD_PASSED, conciliated->SETTLED,
--     forwarded_external->SENT_TO_OTHER_DEPT, sent_to_other->SENT_TO_OTHER_OFFICE.
--   * Seeding new STATUS_CODE rows for those 5 would DUPLICATE states that already exist under another
--     name and create LEGACY_VALUE collisions where none exist today. So they are deliberately NOT
--     seeded.
--
-- Only the genuinely orphaned values are seeded — those matching neither STATUS_CODE nor LEGACY_VALUE.
-- Verified query:
--   SELECT c.status, COUNT(*) FROM complaints c GROUP BY c.status
--    HAVING NOT EXISTS (SELECT 1 FROM rbio_status_master m
--                        WHERE UPPER(m.status_code)=UPPER(c.status) OR m.legacy_value=c.status);
--   -> forwarded 161, awaiting_details 59, reviewer_review 41, re_responded 19, returned 7,
--      awaiting_closure 1, incharge_review 1, under_review 1   (8 values, 290 complaints)
--
-- IS_CLOSED SEMANTICS, stated deliberately — this column is authoritative for "is the file shut" and
-- every open-work queue, reminder scheduler and closure-communication path reads it:
--   * FORWARDED / FORWARDED_OUT: the complaint has left RBIO, so RBIO considers the file shut -> 'Y',
--     matching the existing SENT_TO_OTHER_DEPT and SENT_TO_OTHER_RE rows. IS_TERMINAL stays 'N'
--     because a forwarded complaint can still be reopened (the V97 rationale).
--   * Everything else here is work IN FLIGHT (awaiting details, awaiting closure, under review with a
--     reviewer or in-charge, an RE response received, a file sent back) -> IS_CLOSED 'N'.
--   * AWAITING_CLOSURE is 'N' on purpose: awaiting closure is not closed. Marking it 'Y' would drop
--     complaints out of the open-work queue before anyone had actually closed them.
--
-- Note these columns are varchar(1) 'Y'/'N' STRINGS in this table, not bit(1) — CLOSURE_CLAUSE_MASTER
-- and CATEGORY_MASTER use real bits. Do not copy a 0/1 idiom in here.
--
-- ROLE VISIBILITY IS A SEPARATE TABLE. A row added to RBIO_STATUS_MASTER is invisible in every role's
-- filter list until RBIO_STATUS_ROLE_VISIBILITY also carries it. Both are populated below.
--
-- ══ PART 2: CATEGORY_MASTER IS 100% TEST POLLUTION ══════════════════════════════════════════════
--
-- Measured: 14 rows, ONE distinct name ('S1 authority e2e probe'), ZERO active. Not one real category.
-- COMPLAINT_CATEGORIES holds the ten real RBI grievance categories and is AUTHORITATIVE per the ruling.
--
-- The citizen filing wizard read the POLLUTED table (/api/v1/masters/categories). Because every row is
-- inactive the endpoint returned an empty array and the wizard silently substituted a COMPILED-IN list
-- — so a complainant was choosing from hardcoded constants no master table governed. The frontend is
-- repointed at /api/categories in this same change and now fails closed with a retry.
--
-- THE POLLUTION REGENERATES. e2e/rbio/s1-authorities.spec.ts POSTs 'S1 authority e2e probe' and then
-- DELETEs it, but MasterDataController implements DELETE as a SOFT delete (setActive(false)), so the row
-- is never removed: +1 inactive row per run. This purge is therefore a cleanup, NOT a permanent fix —
-- see the report. A guard written as `WHERE NOT EXISTS (SELECT 1 FROM CATEGORY_MASTER)` would silently
-- stop working after the next e2e run, which is why the purge below is scoped to the probe NAME.
--
-- NOT DROPPING THE TABLE. Retiring CATEGORY_MASTER or converting it to a view is a schema change and is
-- proposed in the report for explicit approval rather than executed here.
--
-- Re-running is safe: every INSERT is guarded with WHERE NOT EXISTS and the purge is idempotent.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

-- ══ PART 1a: the eight orphaned statuses ════════════════════════════════════════════════════════
-- DISPLAY_ORDER continues after the existing 34. TRANSLATION_KEY follows the table's own unbroken
-- convention, rbio.status.<lowercased status_code>.
INSERT INTO RBIO_STATUS_MASTER
  (STATUS_CODE, LEGACY_VALUE, LABEL_EN, TRANSLATION_KEY, FILTER_KIND, MILESTONE_CODE,
   IS_CLOSED, IS_TERMINAL, IS_ACTIVE, IS_CITIZEN_VISIBLE, DISPLAY_ORDER, SCHEME_VERSION, QUEUE_ROLE, BLOCKS_MEETING)
SELECT * FROM (
  SELECT 'FORWARDED'        AS a, 'forwarded'        AS b, 'Forwarded'                   AS c, 'rbio.status.forwarded'        AS d, 'STATUS' AS e, 'FORWARD'        AS f, 'Y' AS g, 'N' AS h, 'Y' AS i, 'Y' AS j, 35 AS k, 'RBIOS_2021' AS l, NULL AS m, NULL AS n UNION ALL
  SELECT 'AWAITING_DETAILS',      'awaiting_details',      'Awaiting Details',            'rbio.status.awaiting_details',      'STATUS', 'ASSESSMENT',     'N', 'N', 'Y', 'Y', 36, 'RBIOS_2021', NULL, NULL UNION ALL
  SELECT 'REVIEWER_REVIEW',       'reviewer_review',       'Under Reviewer Review',       'rbio.status.reviewer_review',       'STATUS', 'ASSESSMENT',     'N', 'N', 'Y', 'Y', 37, 'RBIOS_2021', NULL, NULL UNION ALL
  SELECT 'RE_RESPONDED',          're_responded',          'Regulated Entity Responded',  'rbio.status.re_responded',          'STATUS', 'ASSESSMENT',     'N', 'N', 'Y', 'Y', 38, 'RBIOS_2021', NULL, NULL UNION ALL
  SELECT 'RETURNED',              'returned',              'Returned',                    'rbio.status.returned',              'STATUS', 'ASSESSMENT',     'N', 'N', 'Y', 'Y', 39, 'RBIOS_2021', NULL, NULL UNION ALL
  SELECT 'AWAITING_CLOSURE',      'awaiting_closure',      'Awaiting Closure',            'rbio.status.awaiting_closure',      'STATUS', 'FINAL_DECISION', 'N', 'N', 'Y', 'Y', 40, 'RBIOS_2021', NULL, NULL UNION ALL
  SELECT 'INCHARGE_REVIEW',       'incharge_review',       'Under In-charge Review',      'rbio.status.incharge_review',       'STATUS', 'ASSESSMENT',     'N', 'N', 'Y', 'Y', 41, 'RBIOS_2021', NULL, NULL UNION ALL
  SELECT 'UNDER_REVIEW',          'under_review',          'Under Review',                'rbio.status.under_review',          'STATUS', 'ASSESSMENT',     'N', 'N', 'Y', 'Y', 42, 'RBIOS_2021', NULL, NULL
) src
 WHERE NOT EXISTS (SELECT 1 FROM RBIO_STATUS_MASTER m WHERE m.STATUS_CODE = src.a)
   AND NOT EXISTS (SELECT 1 FROM RBIO_STATUS_MASTER m2 WHERE m2.LEGACY_VALUE = src.b);

-- ══ PART 1b: role visibility, or the new rows never appear in a filter ══════════════════════════
-- RBIO_ADMIN sees everything, matching its existing 34-row coverage. The work-in-flight states are also
-- given to the roles that act on them; FORWARDED is supervisory/visibility only.
INSERT INTO RBIO_STATUS_ROLE_VISIBILITY (ROLE_NAME, STATUS_CODE, IS_DEFAULT, DISPLAY_ORDER)
SELECT * FROM (
  SELECT 'RBIO_ADMIN' AS r, 'FORWARDED' AS s, 'N' AS d, 35 AS o UNION ALL
  SELECT 'RBIO_ADMIN', 'AWAITING_DETAILS', 'N', 36 UNION ALL
  SELECT 'RBIO_ADMIN', 'REVIEWER_REVIEW',  'N', 37 UNION ALL
  SELECT 'RBIO_ADMIN', 'RE_RESPONDED',     'N', 38 UNION ALL
  SELECT 'RBIO_ADMIN', 'RETURNED',         'N', 39 UNION ALL
  SELECT 'RBIO_ADMIN', 'AWAITING_CLOSURE', 'N', 40 UNION ALL
  SELECT 'RBIO_ADMIN', 'INCHARGE_REVIEW',  'N', 41 UNION ALL
  SELECT 'RBIO_ADMIN', 'UNDER_REVIEW',     'N', 42 UNION ALL

  SELECT 'RBIO_DEALING_OFFICIAL', 'AWAITING_DETAILS', 'N', 36 UNION ALL
  SELECT 'RBIO_DEALING_OFFICIAL', 'RE_RESPONDED',     'N', 38 UNION ALL
  SELECT 'RBIO_DEALING_OFFICIAL', 'RETURNED',         'N', 39 UNION ALL
  SELECT 'RBIO_DEALING_OFFICIAL', 'FORWARDED',        'N', 35 UNION ALL

  SELECT 'RBIO_REVIEWER', 'REVIEWER_REVIEW',  'N', 37 UNION ALL
  SELECT 'RBIO_REVIEWER', 'AWAITING_DETAILS', 'N', 36 UNION ALL
  SELECT 'RBIO_REVIEWER', 'RE_RESPONDED',     'N', 38 UNION ALL
  SELECT 'RBIO_REVIEWER', 'RETURNED',         'N', 39 UNION ALL

  SELECT 'RBIO_SUPERVISOR', 'FORWARDED',        'N', 35 UNION ALL
  SELECT 'RBIO_SUPERVISOR', 'AWAITING_CLOSURE', 'N', 40 UNION ALL
  SELECT 'RBIO_SUPERVISOR', 'INCHARGE_REVIEW',  'N', 41 UNION ALL
  SELECT 'RBIO_SUPERVISOR', 'UNDER_REVIEW',     'N', 42 UNION ALL

  SELECT 'RBIO_OFFICER', 'AWAITING_DETAILS', 'N', 36 UNION ALL
  SELECT 'RBIO_OFFICER', 'RE_RESPONDED',     'N', 38 UNION ALL
  SELECT 'RBIO_OFFICER', 'RETURNED',         'N', 39
) v
 WHERE EXISTS (SELECT 1 FROM RBIO_STATUS_MASTER m WHERE m.STATUS_CODE = v.s)
   AND NOT EXISTS (SELECT 1 FROM RBIO_STATUS_ROLE_VISIBILITY x
                    WHERE x.ROLE_NAME = v.r AND x.STATUS_CODE = v.s);

-- ══ PART 1c: the status labels, translatable ════════════════════════════════════════════════════
-- English default only. Unlike clause labels these are ordinary UI vocabulary and MAY be translated
-- later; they are not legal statements. Nothing is machine-translated here.
INSERT INTO TRANSLATION_KEYS (CODE, DEFAULT_VALUE, DESCRIPTION, MODULE, CREATED_AT, UPDATED_AT)
SELECT src.c, src.v, 'RBIO status label (RBIO_STATUS_MASTER.translation_key)', 'rbio-status', NOW(), NOW()
  FROM (
  SELECT 'rbio.status.forwarded' AS c,        'Forwarded' AS v UNION ALL
  SELECT 'rbio.status.awaiting_details',      'Awaiting Details' UNION ALL
  SELECT 'rbio.status.reviewer_review',       'Under Reviewer Review' UNION ALL
  SELECT 'rbio.status.re_responded',          'Regulated Entity Responded' UNION ALL
  SELECT 'rbio.status.returned',              'Returned' UNION ALL
  SELECT 'rbio.status.awaiting_closure',      'Awaiting Closure' UNION ALL
  SELECT 'rbio.status.incharge_review',       'Under In-charge Review' UNION ALL
  SELECT 'rbio.status.under_review',          'Under Review'
) src
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS k WHERE k.CODE = src.c);

-- ══ PART 2: purge the CATEGORY_MASTER test residue ══════════════════════════════════════════════
-- Scoped to the probe NAME so a real category added later is never deleted by a re-run. Every one of
-- the 14 rows carries this exact name and active=0; nothing else is touched.
DELETE FROM CATEGORY_MASTER
 WHERE CATEGORY_NAME = 'S1 authority e2e probe'
   AND ACTIVE = 0;
