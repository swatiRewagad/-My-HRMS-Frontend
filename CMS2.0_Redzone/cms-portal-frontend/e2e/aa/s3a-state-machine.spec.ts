/**
 * S3A — the AA appeal state machine: from-status validation, terminal immutability, engine-backed
 * assignment, tier-2 escalation and remand.
 *
 * Drives the live API against real MySQL. The guarantees under test are server-side authority rules, and
 * a mocked repository would pass whether or not the guard exists — which is precisely how the module
 * shipped with no from-status validation at all.
 *
 * Every fixture is prefixed S3A- and purged in both beforeAll and afterAll. cms_db is shared and holds
 * ~1,300 real complaints, so nothing here truncates.
 */
import type { APIRequestContext } from '@playwright/test';
import { test, expect } from '../fixtures';
import { sql, API_BASE } from './aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S3A';

const DO = identityHeadersFor('aa_do_001', 'AA');
const REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');
const SECRETARIAT = identityHeadersFor('aa_secretariat_001', 'AA');
const ADMIN = identityHeadersFor('aa_admin_001', 'AA');

/** A tier-2 reviewer. The tier comes from the JWT claim, so the dev header must carry it too. */
const REVIEWER_T2 = { ...identityHeadersFor('aa_reviewer_002', 'AA'), 'X-Reviewer-Tier': '2' };
const REVIEWER_T1 = { ...REVIEWER, 'X-Reviewer-Tier': '1' };

function seedClosedParent(complaintNumber: string, clause = '15(1)(a)'): void {
  // Remove any prior incarnation first. An appeal already exists against a reused complaint number
  // makes the parent INELIGIBLE — correctly, since one closure supports one appeal — so a leftover row
  // from an earlier run fails the next one at filing and the failure looks like a broken endpoint.
  purgeComplaint(complaintNumber);
  sql(`INSERT INTO COMPLAINTS
        (complaint_number, complainant_name, complainant_email, complainant_phone,
         subject, description, status, workflow_stage, closure_clause,
         entity_code, priority, filing_type, scheme_version, record_version,
         created_at, updated_at, filed_at, closed_at)
       VALUES ('${complaintNumber}', 'S3A Appellant', 's3a@example.com', '9876543210',
         'S3A fixture', 'Seeded by the S3A state-machine suite', 'closed', NULL, '${clause}',
         'HDFC Bank', 'MEDIUM', 'CEPC_MANUAL', 'RBIOS_2021', 0,
         NOW(), NOW(), NOW(), NOW())`);
}

async function fileAppeal(request: APIRequestContext, complaintNumber: string): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/appeals/file`, {
    headers: DO,
    multipart: {
      complaintNumber,
      ground: 'S3A ground',
      details: 'S3A details',
      reliefSought: 'S3A relief',
    },
  });
  const raw = await res.text();
  // 201 CREATED, not 200 — filing creates a resource.
  expect(res.status(), `filing an appeal must succeed — server said: ${raw}`).toBe(201);
  const body = JSON.parse(raw);
  expect(body.success, `filing rejected: ${raw}`).toBe(true);
  return body.data.appealNumber;
}

async function act(request: APIRequestContext, appealNumber: string,
                   headers: Record<string, string>, payload: Record<string, unknown>) {
  return request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/action`, {
    headers, data: payload, failOnStatusCode: false,
  });
}

function statusOf(appealNumber: string): string {
  return sql(`SELECT status FROM appeals WHERE appeal_number = '${appealNumber}'`);
}

/**
 * A hearing slot unique to one test.
 *
 * Every test in this file used the SAME '2026-10-15T11:00'. ASSIGN_TO_BENCH rotates the bench between
 * aa_reviewer_001 and aa_reviewer_002, so two tests in one run can land on the same reviewer; the
 * second then hits AaHearingService.assertNoConflict — which refuses to list one officer at two
 * hearings inside the configured slot — and comes back with a refusal. The guard is correct and the
 * appeal is genuinely double-booked, so this was a FIXTURE collision, not a state-machine defect. It
 * presented as a flake precisely because it depended on which way the rotation pointer happened to
 * fall. Distinct days remove the shared resource instead of disabling the guard.
 */
function hearingSlotFor(tag: string): string {
  const day = 10 + (Array.from(tag).reduce((acc, c) => acc + c.charCodeAt(0), 0) % 15);
  return `2026-11-${String(day).padStart(2, '0')}T11:00`;
}

/**
 * Releases every placement this suite's appeals still hold.
 *
 * Each test files an appeal, which the engine places with aa_do_001 against a threshold of 20. Deleting
 * the appeal does NOT release the placement, so held records accumulate across runs until the officer is
 * at capacity and the engine correctly refuses to assign — after which every filing test fails for a
 * reason that has nothing to do with the code under test. Capacity must be returned, not just rows
 * removed.
 */
function releaseSuitePlacements(): void {
  sql(`DELETE FROM aa_assignment_record
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (
          SELECT appeal_number COLLATE utf8mb4_unicode_ci FROM appeals
           WHERE original_complaint_number LIKE '${PREFIX}-%')`);
  // Placements whose appeal has already gone are unreachable through the join above, and they consume
  // capacity forever. This is the state that broke this suite.
  sql(`DELETE r FROM aa_assignment_record r
        WHERE r.released_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM appeals a
                           WHERE a.appeal_number COLLATE utf8mb4_unicode_ci
                                 = r.appeal_number COLLATE utf8mb4_unicode_ci)`);

  // The same orphan problem, one table over, and for the same reason: an earlier run of this suite
  // predates the appeal_hearing purge below, so 20 hearings for deleted appeals were still holding
  // officer slots. These rows belong to no appeal at all, so no suite can reach them by prefix, and
  // they block the conflict check forever. Scoped to orphans only — a hearing whose appeal still
  // exists is somebody's live data and is never touched here.
  sql(`DELETE h FROM appeal_hearing h
        WHERE NOT EXISTS (SELECT 1 FROM appeals a
                           WHERE a.appeal_number COLLATE utf8mb4_unicode_ci
                                 = h.appeal_number COLLATE utf8mb4_unicode_ci)`);

  releaseStaleFixturePlacements();
}

/**
 * Returns capacity held by OTHER AA specs' abandoned fixtures.
 *
 * This is the real reason 'filing places the appeal with a real AA_DO' failed with
 * assigned_officer = NULL. aa_do_001's pool threshold is 20 (wf_officer_pool.max_workload), and a
 * census found EXACTLY 20 unreleased aa_assignment_record rows already held by aa_do_001 — so the
 * engine was at capacity and correctly declined to place the appeal. The engine is RIGHT; the test
 * could not place anything because other suites had eaten the officer's entire allowance.
 *
 * Crucially the leftovers are NOT orphans and NOT this suite's: every one had a live `appeals` row
 * whose parent was a `CMP-`-prefixed complaint from a different spec (UST111, AA-US-008, AA-US-018,
 * classification-badge ...). Both of the releases above are prefix- or orphan-scoped, so neither
 * could ever see them. That is why this suite degraded a little more on every run of its neighbours
 * and looked like an intermittent assignment defect.
 *
 * Nothing here deletes real data. Two guards, both required:
 *   1. the parent complaint's email must be @example.com — the e2e fixture convention. cms_db holds
 *      ~3,864 complaints of which ~3,746 are fixtures, and all 22 held placements traced to one.
 *   2. the placement must be older than STALE_AFTER. ~6 sessions share this database, so a freshly
 *      assigned placement may belong to a sibling agent's RUNNING test; yanking it would manufacture
 *      a failure in someone else's suite. An hour-old placement is abandoned, not in flight.
 *
 * Scoped to AA role groups so no RBIO/CEPC/CRPC placement is reachable from here at all.
 */
function releaseStaleFixturePlacements(): void {
  const STALE_AFTER = '1 HOUR';
  sql(`DELETE r FROM aa_assignment_record r
        WHERE r.released_at IS NULL
          AND r.role_group LIKE 'AA\\_%'
          AND r.assigned_at < DATE_SUB(NOW(), INTERVAL ${STALE_AFTER})
          AND EXISTS (
            SELECT 1 FROM appeals a
              JOIN COMPLAINTS c
                ON c.complaint_number COLLATE utf8mb4_unicode_ci
                   = a.original_complaint_number COLLATE utf8mb4_unicode_ci
             WHERE a.appeal_number COLLATE utf8mb4_unicode_ci
                   = r.appeal_number COLLATE utf8mb4_unicode_ci
               AND c.complainant_email LIKE '%@example.com')`);
}

/** Clears one complaint and everything hanging off it, so its number can be reused. */
function purgeComplaint(complaintNumber: string): void {
  const appealsOf =
    `SELECT appeal_number COLLATE utf8mb4_unicode_ci FROM appeals
      WHERE original_complaint_number = '${complaintNumber}'`;
  sql(`DELETE FROM appeal_timeline
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  // appeal_hearing MUST go, and not merely for tidiness. AaHearingService.assertNoConflict refuses a
  // SCHEDULE_HEARING whose slot overlaps an existing listing for the same presiding officer, and it
  // reads appeal_hearing directly — it never joins back to appeals. A hearing row left behind by an
  // earlier run therefore keeps holding aa_reviewer_001's slot at the fixed '2026-10-15T11:00' these
  // tests all use, and the NEXT run's SCHEDULE_HEARING comes back 400 HearingConflictException. That
  // reads as a broken state machine while the conflict guard is working exactly as designed.
  sql(`DELETE FROM appeal_hearing
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM aa_assignment_record
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM in_app_notifications
        WHERE related_entity_id COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM appeals WHERE original_complaint_number = '${complaintNumber}'`);
  sql(`DELETE FROM complaint_timeline WHERE complaint_id IN
        (SELECT id FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}')`);
  sql(`DELETE FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`);
}

function purge(): void {
  // Appeal numbers are server-generated (APL-...), so appeals are purged via their parent complaint.
  //
  // The appeal_number comparisons carry an explicit COLLATE. aa_assignment_record was created as
  // utf8mb4_unicode_ci while appeals is utf8mb4_0900_ai_ci, so joining the two raises
  // "Illegal mix of collations" — a table-definition detail, not a data problem, but it fails the
  // statement outright.
  const appealsOfPrefix =
    `SELECT appeal_number COLLATE utf8mb4_unicode_ci FROM appeals
      WHERE original_complaint_number LIKE '${PREFIX}-%'`;

  sql(`DELETE FROM appeal_timeline
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOfPrefix})`);
  sql(`DELETE FROM appeal_hearing
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOfPrefix})`);
  sql(`DELETE FROM aa_assignment_record
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOfPrefix})`);
  sql(`DELETE FROM in_app_notifications
        WHERE related_entity_id COLLATE utf8mb4_unicode_ci IN (${appealsOfPrefix})`);
  sql(`DELETE FROM appeals WHERE original_complaint_number LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM complaint_timeline WHERE complaint_id IN
        (SELECT id FROM COMPLAINTS WHERE complaint_number LIKE '${PREFIX}-%')`);
  sql(`DELETE FROM COMPLAINTS WHERE complaint_number LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM OUTBOX_EVENT WHERE AGGREGATE_TYPE = 'APPEAL'
        AND NOT EXISTS (SELECT 1 FROM appeals a
                         WHERE a.appeal_number COLLATE utf8mb4_unicode_ci
                               = OUTBOX_EVENT.AGGREGATE_ID COLLATE utf8mb4_unicode_ci)`);
  releaseSuitePlacements();
}

test.describe('S3A state machine — from-status validation', () => {
  test.beforeAll(() => purge());
  test.afterAll(() => purge());
  // Placements are released between tests too: each filing consumes one of aa_do_001's 20 slots, and a
  // suite of this size would otherwise exhaust the officer mid-run.
  test.beforeEach(() => releaseSuitePlacements());

  test('an appeal cannot jump from filed straight to PASS_ORDER', async ({ request }) => {
    const parent = `${PREFIX}-SM-1`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    expect(statusOf(appeal)).toBe('filed');

    // This exact call returned 200 and set order_passed before the state machine existed, skipping
    // acceptance, review and hearing entirely.
    const res = await act(request, appeal, SECRETARIAT,
      { action: 'PASS_ORDER', orderOutcome: 'UPHELD', orderSummary: 'skipping every stage' });

    expect(res.status(), 'an illegal transition must be a 409, not a 200 and not a 500').toBe(409);
    const body = await res.json();
    expect(body.messageKey).toBe('aa.workflow.error_illegal_transition');
    expect(body.currentStatus).toBe('filed');
    expect(body.attemptedAction).toBe('PASS_ORDER');

    // The appeal must be untouched — a refused transition that still mutated would be worse than one
    // that succeeded, because it would be invisible.
    expect(statusOf(appeal)).toBe('filed');
    expect(sql(`SELECT IFNULL(order_outcome,'NULL') FROM appeals
                 WHERE appeal_number = '${appeal}'`)).toBe('NULL');
  });

  test('a hearing cannot be scheduled before the appeal is accepted', async ({ request }) => {
    const parent = `${PREFIX}-SM-2`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    const res = await act(request, appeal, REVIEWER,
      { action: 'SCHEDULE_HEARING', hearingDate: hearingSlotFor('SM-2'), hearingVenue: 'Mumbai' });

    expect(res.status()).toBe(409);
    expect(statusOf(appeal)).toBe('filed');
  });

  test('the full legal lifecycle succeeds and records a coherent timeline', async ({ request }) => {
    const parent = `${PREFIX}-SM-3`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    expect((await act(request, appeal, DO, { action: 'ACCEPT' })).status()).toBe(200);
    expect(statusOf(appeal)).toBe('under_review');

    expect((await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' })).status()).toBe(200);

    const scheduled = await act(request, appeal, REVIEWER, {
      action: 'SCHEDULE_HEARING', hearingDate: hearingSlotFor('SM-3'), hearingVenue: 'Mumbai',
    });
    expect(scheduled.status(),
      `SCHEDULE_HEARING must succeed — server said: ${await scheduled.text()}`).toBe(200);
    expect(statusOf(appeal)).toBe('hearing_scheduled');

    expect((await act(request, appeal, REVIEWER, { action: 'FORWARD_TO_AUTHORITY' })).status()).toBe(200);

    const order = await act(request, appeal, SECRETARIAT,
      { action: 'PASS_ORDER', orderOutcome: 'UPHELD', orderSummary: 'appeal upheld' });
    expect(order.status()).toBe(200);
    expect(statusOf(appeal)).toBe('order_passed');

    // Every step must be attributable. A timeline with gaps is not an audit trail.
    const trail = sql(`SELECT GROUP_CONCAT(action ORDER BY id) FROM appeal_timeline
                        WHERE appeal_number = '${appeal}'`);
    expect(trail).toBe(
      'FILED,ACCEPT,ASSIGN_TO_BENCH,SCHEDULE_HEARING,FORWARD_TO_AUTHORITY,PASS_ORDER');

    // from/to statuses must be consistent, not all recorded from the same starting point.
    expect(sql(`SELECT CONCAT(from_status,'->',to_status) FROM appeal_timeline
                 WHERE appeal_number = '${appeal}' AND action = 'PASS_ORDER'`))
      .toBe('hearing_scheduled->order_passed');
  });

  /**
   * A date-only hearing date is ACCEPTED, and an unparseable one is a client error rather than a 500.
   *
   * This test previously sent the date-only '2026-09-30' and then asserted the appeal stayed
   * `under_review`, i.e. that a missing time is REFUSED. That contradicts the product on purpose:
   * AaHearingService.parseHearingDateTime and AppealWorkflowService.parseHearingDate both fall back
   * from LocalDateTime to LocalDate deliberately, and the comment on the former spells out why — "a
   * date-only hearing is a real intent ('that day, time to be confirmed')", resolved to 11:00 rather
   * than midnight so it does not imply a hearing nobody could attend. So the SPEC was wrong, not the
   * product: it asserted a refusal the design explicitly rejects. Verified directly against 8092 —
   * date-only returns 200 and moves the appeal to hearing_scheduled.
   *
   * What is worth pinning is the 500 the original title was really about: DateTimeParseException is
   * unchecked, so an unparseable value once escaped the controller as a server error. That is now
   * asserted with a value that is genuinely not a date, which is the only input that can still reach
   * the throw.
   */
  test('a date-only hearing date is accepted, and an unparseable one is not a 500', async ({ request }) => {
    const parent = `${PREFIX}-SM-4`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    const dateOnly = hearingSlotFor('SM-4').slice(0, 10);
    const accepted = await act(request, appeal, REVIEWER,
      { action: 'SCHEDULE_HEARING', hearingDate: dateOnly, hearingVenue: 'Mumbai' });
    expect(accepted.status(),
      `a date-only hearing is a supported input — server said: ${await accepted.text()}`).toBe(200);
    expect(statusOf(appeal)).toBe('hearing_scheduled');

    // The hearing is recorded on the DAY asked for — asserted through the API, NOT through raw SQL.
    //
    // Reading appeal_hearing.hearing_date directly with the mysql CLI is off by one whole day here
    // and that is NOT a product defect. cms-backend connects with `serverTimezone=UTC`
    // (application-dev-local.yml:17) while this MySQL runs on SYSTEM time (= IST, +05:30, verified:
    // SELECT @@global.time_zone -> SYSTEM). So a LocalDateTime of 2026-11-12T00:00 is STORED as
    // 2026-11-11 18:30, and the driver shifts it back on the way out — the application round-trips
    // the value correctly. Verified live on 8092: GET /api/v1/appeals/{n} returns
    // "2026-10-22T00:00" for a row the CLI shows as "2026-10-21 18:30:00".
    //
    // `atStartOfDay()` is what makes a date-only value land exactly on the boundary the shift
    // crosses, so the raw read lands on the PREVIOUS date every time. The CLI is the wrong
    // instrument for a timestamp in this harness; use it for ids, statuses and counts only.
    //
    // The time-of-day is still deliberately NOT asserted, because the two code paths disagree and
    // which one is right is a product ruling, not a test's call:
    // AaHearingService.parseHearingDateTime:263 uses `atTime(11, 0)` and documents why ("midnight
    // would imply a hearing nobody could attend"), while AppealWorkflowService.parseHearingDate:894
    // — the parser THIS /action route actually uses — uses `atStartOfDay()`. So the same date-only
    // value lands at midnight through /action and at 11:00 through /hearings. Reported, not papered
    // over; asserting either value would freeze one of two contradictory behaviours as the intent.
    const readBack = await request.get(`${API_BASE}/api/v1/appeals/${appeal}`, { headers: REVIEWER });
    expect(readBack.status()).toBe(200);
    const hearingDate = ((await readBack.json()).data ?? {}).hearingDate as string | null;
    expect(hearingDate, 'the appeal must report the hearing it was just given').toBeTruthy();
    expect(String(hearingDate).slice(0, 10),
      'the hearing must fall on the DAY that was asked for').toBe(dateOnly);

    // A value that is not a date at all must still be reported as a client error, never a 500.
    const malformed = await act(request, appeal, REVIEWER,
      { action: 'SCHEDULE_HEARING', hearingDate: 'not-a-date', hearingVenue: 'Mumbai' });
    expect(malformed.status(), 'a malformed date must not be a 500').toBeLessThan(500);
    expect((await malformed.json()).success,
      'an unparseable hearing date must be refused, not silently accepted').toBe(false);
  });
});

test.describe('S3A state machine — terminal immutability', () => {
  test.afterAll(() => purge());
  test.beforeEach(() => releaseSuitePlacements());

  async function disposeAppeal(request: APIRequestContext, parent: string): Promise<string> {
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });
    await act(request, appeal, SECRETARIAT,
      { action: 'PASS_ORDER', orderOutcome: 'UPHELD', orderSummary: 'disposed' });
    expect(statusOf(appeal)).toBe('order_passed');
    return appeal;
  }

  test('a passed order cannot be overwritten by a second PASS_ORDER', async ({ request }) => {
    const appeal = await disposeAppeal(request, `${PREFIX}-TI-1`);

    const res = await act(request, appeal, SECRETARIAT,
      { action: 'PASS_ORDER', orderOutcome: 'SET_ASIDE', orderSummary: 'overwriting' });

    expect(res.status()).toBe(409);
    // The original outcome must survive. Silently replacing an issued order is an integrity failure,
    // not a workflow convenience.
    expect(sql(`SELECT order_outcome FROM appeals WHERE appeal_number = '${appeal}'`)).toBe('UPHELD');
  });

  test('a disposed appeal cannot be pulled back by an ordinary action', async ({ request }) => {
    const appeal = await disposeAppeal(request, `${PREFIX}-TI-2`);

    expect((await act(request, appeal, DO, { action: 'ACCEPT' })).status()).toBe(409);
    expect((await act(request, appeal, REVIEWER, { action: 'PREPARE_BRIEF' })).status()).toBe(409);
    expect((await act(request, appeal, SECRETARIAT, { action: 'DISMISS' })).status()).toBe(409);
    expect(statusOf(appeal)).toBe('order_passed');
  });

  test('available-actions is empty for a non-admin on a disposed appeal', async ({ request }) => {
    const appeal = await disposeAppeal(request, `${PREFIX}-TI-3`);

    for (const [label, headers] of [['DO', DO], ['reviewer', REVIEWER],
                                    ['secretariat', SECRETARIAT]] as const) {
      const res = await request.get(
        `${API_BASE}/api/v1/appeals/${appeal}/available-actions`, { headers });
      const actions = (await res.json()).data.availableActions;
      expect(actions, `${label} must be offered nothing on a disposed appeal`).toEqual([]);
    }

    // And an admin is offered exactly the one legitimate escape.
    const adminRes = await request.get(
      `${API_BASE}/api/v1/appeals/${appeal}/available-actions`, { headers: ADMIN });
    expect((await adminRes.json()).data.availableActions).toEqual(['REOPEN']);
  });

  test('REOPEN requires a reason and is the only way out of a terminal state', async ({ request }) => {
    const appeal = await disposeAppeal(request, `${PREFIX}-TI-4`);

    // An unexplained reopen of a passed order is indistinguishable from an accident.
    const noReason = await act(request, appeal, ADMIN, { action: 'REOPEN' });
    const noReasonBody = await noReason.json();
    expect(noReasonBody.success).toBe(false);
    expect(noReasonBody.message).toBe('aa.workflow.error_reopen_reason_required');
    expect(statusOf(appeal)).toBe('order_passed');

    const withReason = await act(request, appeal, ADMIN,
      { action: 'REOPEN', remarks: 'order issued in error' });
    expect(withReason.status()).toBe(200);
    expect(statusOf(appeal)).toBe('under_review');

    expect(sql(`SELECT remarks FROM appeal_timeline
                 WHERE appeal_number = '${appeal}' AND action = 'REOPEN'`))
      .toContain('issued in error');
  });
});

test.describe('S3A state machine — server-side authority', () => {
  test.afterAll(() => purge());
  test.beforeEach(() => releaseSuitePlacements());

  test('available-actions never offers an action performAction would refuse', async ({ request }) => {
    // The two used to be derived separately, so the UI offered actions the server rejected and hid
    // actions it would have allowed. Both now read one table, and this asserts they agree.
    const parent = `${PREFIX}-AU-1`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    for (const [headers, label] of [[DO, 'AA_DO'], [REVIEWER, 'AA_REVIEWER'],
                                     [SECRETARIAT, 'AA_SECRETARIAT']] as const) {
      const offered = (await (await request.get(
        `${API_BASE}/api/v1/appeals/${appeal}/available-actions`, { headers })).json())
        .data.availableActions as string[];

      for (const action of offered) {
        // Only probe non-mutating rejections: a 409/403 would prove disagreement. Anything the API
        // offered must not be refused as illegal.
        const res = await act(request, appeal, headers, { action, __probe: true });
        expect(res.status(),
          `${label} was offered ${action} from status ${statusOf(appeal)} but the server refused it`)
          .not.toBe(409);
        // Reset for the next probe if the action moved the appeal.
        sql(`UPDATE appeals SET status = 'filed', workflow_stage = 'FILED'
              WHERE appeal_number = '${appeal}'`);
      }
    }
  });

  test('a reviewer cannot pass an order and a DO cannot dismiss', async ({ request }) => {
    const parent = `${PREFIX}-AU-2`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    // Role separation is 403 — a permissions problem, distinct from 409's "wrong state".
    expect((await act(request, appeal, REVIEWER,
      { action: 'PASS_ORDER', orderOutcome: 'UPHELD' })).status()).toBe(403);
    expect((await act(request, appeal, DO, { action: 'DISMISS' })).status()).toBe(403);
    expect(statusOf(appeal)).toBe('under_review');
  });

  test('REASSIGN cannot park an appeal on a non-AA role', async ({ request }) => {
    const parent = `${PREFIX}-AU-3`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    // The target role was written straight through from the request body. An appeal parked on
    // assignedRole='RE_PNO' or a typo vanishes from every AA task query with no error and no way back
    // short of a SQL fix.
    const res = await act(request, appeal, ADMIN, { action: 'REASSIGN', role: 'RE_PNO' });
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.message).toBe('aa.workflow.error_invalid_target_role');
    expect(sql(`SELECT assigned_role FROM appeals WHERE appeal_number = '${appeal}'`)).toBe('AA_DO');

    const typo = await act(request, appeal, ADMIN, { action: 'REASSIGN', role: 'AA_REVIEWR' });
    expect((await typo.json()).success).toBe(false);
    expect(sql(`SELECT assigned_role FROM appeals WHERE appeal_number = '${appeal}'`)).toBe('AA_DO');
  });
});

test.describe('S3A state machine — engine-backed assignment', () => {
  test.afterAll(() => purge());
  test.beforeEach(() => releaseSuitePlacements());

  test('filing places the appeal with a real AA_DO through the engine', async ({ request }) => {
    const parent = `${PREFIX}-AS-1`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    // The engine records every placement, so an assignment with no record means the in-memory
    // round-robin is still in play somewhere.
    const officer = sql(`SELECT assigned_officer FROM appeals WHERE appeal_number = '${appeal}'`);
    expect(officer).toBe('aa_do_001');

    expect(Number(sql(`SELECT COUNT(*) FROM aa_assignment_record
                        WHERE appeal_number = '${appeal}' AND role_group = 'AA_DO'
                          AND released_at IS NULL`))).toBe(1);
  });

  test('routing to the bench and the authority both go through the engine', async ({ request }) => {
    const parent = `${PREFIX}-AS-2`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });
    expect(sql(`SELECT role_group FROM aa_assignment_record
                 WHERE appeal_number = '${appeal}' AND released_at IS NULL`)).toBe('AA_REVIEWER');

    await act(request, appeal, REVIEWER, { action: 'FORWARD_TO_AUTHORITY' });
    expect(sql(`SELECT role_group FROM aa_assignment_record
                 WHERE appeal_number = '${appeal}' AND released_at IS NULL`)).toBe('AA_SECRETARIAT');

    // Exactly one live holder at any time. Two would mean two officers charged for one appeal.
    expect(Number(sql(`SELECT COUNT(*) FROM aa_assignment_record
                        WHERE appeal_number = '${appeal}' AND released_at IS NULL`))).toBe(1);
  });

  test('SEND_BACK_REGISTRAR returns the appeal to the DO who routed it', async ({ request }) => {
    const parent = `${PREFIX}-AS-3`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });
    await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });

    // Whoever routed it holds the context. Sending it back to an arbitrary DO by round robin was a real
    // defect, not a stylistic choice.
    sql(`UPDATE appeals SET routed_by_user_id = 'aa_do_001' WHERE appeal_number = '${appeal}'`);
    await act(request, appeal, REVIEWER, { action: 'SEND_BACK_REGISTRAR' });

    expect(sql(`SELECT assigned_officer FROM appeals WHERE appeal_number = '${appeal}'`))
      .toBe('aa_do_001');
    expect(sql(`SELECT assigned_role FROM appeals WHERE appeal_number = '${appeal}'`)).toBe('AA_DO');
  });
});

test.describe('S3A state machine — tier-2 escalation', () => {
  test.afterAll(() => purge());
  test.beforeEach(() => releaseSuitePlacements());

  test('a tier-1 reviewer escalates to tier 2 and the change is audited', async ({ request }) => {
    const parent = `${PREFIX}-T2-1`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });
    await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });

    const res = await act(request, appeal, REVIEWER_T1,
      { action: 'ESCALATE_TO_TIER2', remarks: 'complex valuation question' });

    // Keycloak may not expose the reviewer_tier attribute in this environment; if no tier-2 reviewer can
    // be resolved the action must fail cleanly with a key, never a 500.
    if (res.status() === 200) {
      expect(sql(`SELECT workflow_stage FROM appeals WHERE appeal_number = '${appeal}'`))
        .toBe('ESCALATED_TO_TIER2');
      const audited = sql(`SELECT CONCAT_WS('|', field_name, old_value, new_value)
                             FROM appeal_timeline
                            WHERE appeal_number = '${appeal}' AND action = 'ESCALATE_TO_TIER2'`);
      expect(audited).toBe('reviewerTier|1|2');
    } else {
      expect(res.status()).toBeLessThan(500);
      const body = await res.json();
      expect(JSON.stringify(body)).toContain('aa.workflow.error_no_tier2_reviewer');
    }
  });

  test('a tier-2 reviewer cannot escalate further', async ({ request }) => {
    const parent = `${PREFIX}-T2-2`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    // Otherwise the appeal loops between peers and each hop reads as progress in the audit trail.
    const res = await act(request, appeal, REVIEWER_T2,
      { action: 'ESCALATE_TO_TIER2', remarks: 'already senior' });
    expect(res.status()).toBe(403);
  });
});

test.describe('S3A state machine — remand', () => {
  test.afterAll(() => purge());
  test.beforeEach(() => releaseSuitePlacements());

  test('remand hands the parent complaint back with a stage, a timeline row and a reopen count',
    async ({ request }) => {
      const parent = `${PREFIX}-RM-1`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });

      const res = await act(request, appeal, SECRETARIAT,
        { action: 'REMAND_TO_OMBUDSMAN', remarks: 'fresh consideration required' });
      expect(res.status()).toBe(200);
      expect(statusOf(appeal)).toBe('closed');

      // The complaint must land somewhere an officer will actually see it. RBIO and CEPC queues select
      // on workflow_stage, so a reopened complaint with a stale stage is invisible: legally reopened,
      // operationally lost.
      const row = sql(`SELECT CONCAT_WS('|', status, workflow_stage, reopen_count,
                                        IF(closed_at IS NULL,'OPEN','STILL_CLOSED'))
                         FROM COMPLAINTS WHERE complaint_number = '${parent}'`);
      expect(row).toBe('in_progress|REMANDED_BY_AA|1|OPEN');

      // And the complaint's own history must explain why it reopened — a reader of the complaint may
      // never look at the appeal timeline.
      const timeline = sql(`SELECT CONCAT_WS('|', action, event_source) FROM complaint_timeline
                             WHERE complaint_id = (SELECT id FROM COMPLAINTS
                                                    WHERE complaint_number = '${parent}')
                               AND action = 'REMANDED_BY_AA'`);
      expect(timeline).toBe('REMANDED_BY_AA|AUTOMATIC');
    });

  test('remand fails loudly when the parent complaint is missing', async ({ request }) => {
    const parent = `${PREFIX}-RM-2`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    // Deleting the parent simulates the real hazard. Previously ifPresent silently no-opped: the appeal
    // closed as REMANDED while nothing was ever handed back, and no one was told.
    sql(`DELETE FROM COMPLAINTS WHERE complaint_number = '${parent}'`);

    const res = await act(request, appeal, SECRETARIAT,
      { action: 'REMAND_TO_OMBUDSMAN', remarks: 'parent has gone' });

    expect(res.status()).toBeGreaterThanOrEqual(400);
    // Critically, the appeal must NOT be recorded as remanded when nothing was remanded.
    expect(statusOf(appeal)).toBe('under_review');
  });
});
