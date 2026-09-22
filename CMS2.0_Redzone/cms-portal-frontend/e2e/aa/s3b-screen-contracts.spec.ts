/**
 * S3B — the CONTRACTS the AA screens depend on, asserted without a browser.
 *
 * Why this exists alongside s3b-appeal-screens.spec.ts: browser-driven AA login is broken in this repo
 * independently of this session (the pre-existing dashboard suite fails the same way, and the staff route
 * guard silently renders the public layout instead of redirecting to Keycloak). Rather than ship no
 * verification at all, this suite asserts every server-side fact the rewritten screens now rely on. If
 * one of these breaks, the screens break — so these are the regressions worth catching.
 *
 * The browser suite stays in the tree, correct and skipped, and will pass once SSO is repaired. It is the
 * only thing that can prove RENDERING; this is the closest honest substitute.
 *
 * Fixtures are prefixed S3BC- and purged in beforeAll and afterAll. cms_db is shared, so nothing here
 * truncates.
 */
import type { APIRequestContext } from '@playwright/test';
import { test, expect } from '@playwright/test';
import { sql, API_BASE, TEN_LOCALES, fetchTranslations } from './aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S3BC';

const DO = identityHeadersFor('aa_do_001', 'AA');
const REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');
const SECRETARIAT = identityHeadersFor('aa_secretariat_001', 'AA');

function seedClosedParent(complaintNumber: string): void {
  purgeComplaint(complaintNumber);
  sql(`INSERT INTO COMPLAINTS
        (complaint_number, complainant_name, complainant_email, complainant_phone,
         subject, description, status, workflow_stage, closure_clause,
         entity_code, entity_name, priority, filing_type, scheme_version,
         created_at, updated_at, filed_at, closed_at)
       VALUES ('${complaintNumber}', 'S3BC Appellant', 's3bc@example.com', '9876543210',
         'S3BC fixture', 'Seeded by the S3B contract suite', 'closed', NULL, '15(1)(a)',
         'HDFC Bank', 'Fixture Entity', 'MEDIUM', 'CEPC_MANUAL', 'RBIOS_2021',
         NOW(), NOW(), NOW(), NOW())`);
}

/** Deleting an appeal does not release its placement; unreleased rows exhaust the officer's threshold. */
function releasePlacements(): void {
  sql(`DELETE r FROM aa_assignment_record r
        WHERE r.released_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM appeals a
                           WHERE a.appeal_number COLLATE utf8mb4_unicode_ci
                                 = r.appeal_number COLLATE utf8mb4_unicode_ci)`);
}

/**
 * Removes hearings this suite created.
 *
 * The server refuses to double-book a presiding officer (409), which is correct — but it means a fixed
 * hearing slot collides with anything an earlier run left behind, and the failure then looks like a
 * broken endpoint instead of a working guard.
 */
function purgeHearings(complaintNumber: string): void {
  sql(`DELETE FROM appeal_hearing
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (
          SELECT appeal_number COLLATE utf8mb4_unicode_ci FROM appeals
           WHERE original_complaint_number = '${complaintNumber}')`);
}

function purgeComplaint(complaintNumber: string): void {
  purgeHearings(complaintNumber);
  // aa_assignment_record is utf8mb4_unicode_ci, appeals is utf8mb4_0900_ai_ci: joining them without an
  // explicit COLLATE raises "Illegal mix of collations" and the statement fails outright.
  const appealsOf = `SELECT appeal_number COLLATE utf8mb4_unicode_ci FROM appeals
                      WHERE original_complaint_number = '${complaintNumber}'`;
  sql(`DELETE FROM appeal_timeline
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM aa_assignment_record
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM appeals WHERE original_complaint_number = '${complaintNumber}'`);
  sql(`DELETE FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`);
}

function purge(): void {
  sql(`SELECT complaint_number FROM COMPLAINTS WHERE complaint_number LIKE '${PREFIX}-%'`)
    .split('\n').map(s => s.trim()).filter(Boolean)
    .forEach(purgeComplaint);
  releasePlacements();
}

/** A future hearing datetime unique to this run, offset by whole days. */
function uniqueHearingSlot(dayOffset: number): string {
  const d = new Date();
  d.setUTCFullYear(d.getUTCFullYear() + 1);
  d.setUTCDate(d.getUTCDate() + dayOffset);
  const iso = d.toISOString().slice(0, 10);
  // Minute derived from the clock so repeated runs never reuse a slot.
  const minute = String(new Date().getMinutes()).padStart(2, '0');
  return `${iso}T10:${minute}:00`;
}

async function fileAppeal(request: APIRequestContext, complaintNumber: string): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/appeals/file`, {
    headers: DO,
    multipart: {
      complaintNumber, ground: 'S3BC ground', details: 'S3BC details', reliefSought: 'S3BC relief',
    },
  });
  const raw = await res.text();
  expect(res.status(), `filing must succeed — server said: ${raw}`).toBe(201);
  return JSON.parse(raw).data.appealNumber;
}

async function act(request: APIRequestContext, appealNumber: string,
                   headers: Record<string, string>, payload: Record<string, unknown>) {
  return request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/action`, {
    headers, data: payload, failOnStatusCode: false,
  });
}

async function detail(request: APIRequestContext, appealNumber: string,
                      headers: Record<string, string>) {
  const res = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}`, { headers });
  expect(res.status()).toBe(200);
  return (await res.json()).data;
}

test.describe('S3B screen contracts', () => {
  test.beforeAll(() => purge());
  test.afterAll(() => purge());
  test.beforeEach(() => releasePlacements());

  test('the detail payload carries availableActions, so the UI need not derive them',
    async ({ request }) => {
      const parent = `${PREFIX}-C-1`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);

      const data = await detail(request, appeal, DO);
      expect(Array.isArray(data.availableActions)).toBe(true);
      expect(data.availableActions.length,
        'a filed appeal must offer an AA_DO something, or the screen dead-ends').toBeGreaterThan(0);

      // Whatever the detail payload advertises must match the dedicated endpoint exactly. The screens
      // read the former; any drift between the two would resurface the original defect.
      const viaEndpoint = (await (await request.get(
        `${API_BASE}/api/v1/appeals/${appeal}/available-actions`, { headers: DO })).json())
        .data.availableActions;
      expect(data.availableActions).toEqual(viaEndpoint);
    });

  test('availableActions is ROLE-scoped, so each officer sees only their own actions',
    async ({ request }) => {
      const parent = `${PREFIX}-C-2`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });

      const forReviewer = (await detail(request, appeal, REVIEWER)).availableActions as string[];
      const forSecretariat = (await detail(request, appeal, SECRETARIAT)).availableActions as string[];

      // A reviewer must never be offered the power to dispose of an appeal, and vice versa.
      expect(forReviewer).not.toContain('PASS_ORDER');
      expect(forSecretariat).toContain('PASS_ORDER');
      expect(forSecretariat).not.toContain('SEND_BACK_REGISTRAR');
    });

  test('an accepted appeal offers the reviewer real work — the step-2 dead end is closed',
    async ({ request }) => {
      const parent = `${PREFIX}-C-3`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });
      await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });

      // The old UI gated on `accepted`, which the backend never sets; the real status is under_review, so
      // a reviewer was shown nothing and the workflow could not advance.
      expect(sql(`SELECT status FROM appeals WHERE appeal_number = '${appeal}'`)).toBe('under_review');

      const actions = (await detail(request, appeal, REVIEWER)).availableActions as string[];
      expect(actions.length).toBeGreaterThan(0);
      expect(actions).toContain('SCHEDULE_HEARING');
    });

  test('a disposed appeal offers a non-admin nothing at all', async ({ request }) => {
    const parent = `${PREFIX}-C-4`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });
    await act(request, appeal, SECRETARIAT,
      { action: 'PASS_ORDER', orderOutcome: 'UPHELD', orderSummary: 'disposed' });

    // The screen renders a terminal banner off the back of an empty list, so an empty list is the
    // contract that matters.
    for (const [label, headers] of [['DO', DO], ['reviewer', REVIEWER]] as const) {
      const actions = (await detail(request, appeal, headers)).availableActions;
      expect(actions, `${label} must be offered nothing on a disposed appeal`).toEqual([]);
    }
  });

  test('order fields are FLAT on the payload, not nested under an order object',
    async ({ request }) => {
      const parent = `${PREFIX}-C-5`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });

      const order = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/order`, {
        headers: SECRETARIAT,
        data: { outcome: 'MODIFIED', orderSummary: 'award revised', awardAmount: 7500 },
        failOnStatusCode: false,
      });
      expect(order.status()).toBeLessThan(400);

      const data = await detail(request, appeal, DO);
      // The template bound appeal().order.* against a payload that has no such object, so the whole
      // order section could never render whatever had been ordered.
      expect(data.order, 'there must be NO nested order object').toBeUndefined();
      expect(data.orderOutcome).toBe('MODIFIED');
      expect(Number(data.awardModifiedAmount)).toBe(7500);
      expect(data.orderSummary).toContain('award revised');
    });

  test('the order endpoint accepts `outcome` and `awardAmount`, and validates the vocabulary',
    async ({ request }) => {
      const parent = `${PREFIX}-C-6`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });

      // The old client sent outcome/modifiedAmount to /action, which reads orderOutcome/
      // awardModifiedAmount — so every submission was refused. These are the field names the screen
      // now sends.
      const ok = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/order`, {
        headers: SECRETARIAT,
        data: { outcome: 'UPHELD', orderSummary: 'upheld on the merits' },
        failOnStatusCode: false,
      });
      expect(ok.status()).toBeLessThan(400);
      expect((await ok.json()).success).not.toBe(false);

      // And an invented outcome is refused, not stored — /action accepted any string.
      const bad = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/order`, {
        headers: SECRETARIAT,
        data: { outcome: 'DEFINITELY_NOT_AN_OUTCOME', orderSummary: 'nonsense' },
        failOnStatusCode: false,
      });
      const badBody = await bad.json();
      expect(bad.status() >= 400 || badBody.success === false,
        'an invalid outcome must be refused').toBe(true);
    });

  test('hearings persist a real history, and a reschedule preserves the vacated sitting',
    async ({ request }) => {
      const parent = `${PREFIX}-C-7`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });
      await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });

      // Minute-unique slots: the officer-conflict guard is real, so a hardcoded time collides with any
      // hearing a previous run left in the same diary.
      const slotA = uniqueHearingSlot(0);
      const slotB = uniqueHearingSlot(1);

      const first = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/hearings`, {
        headers: REVIEWER,
        data: {
          hearingDate: slotA, hearingVenue: 'Other',
          hearingMode: 'VIDEO', partiesToNotify: ['appellant'],
        },
        failOnStatusCode: false,
      });
      expect(first.status()).toBeLessThan(400);

      // Notices are RECORDED, never sent: there is no gateway. The screen's copy must match this.
      const firstBody = await first.json();
      expect(firstBody.noticeStatus).toBe('PENDING');

      const second = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/hearings`, {
        headers: REVIEWER,
        data: {
          hearingDate: slotB, hearingVenue: 'Other', hearingMode: 'VIDEO',
          reason: 'presiding officer unavailable', partiesToNotify: ['appellant'],
        },
        failOnStatusCode: false,
      });
      expect(second.status()).toBeLessThan(400);

      const history = (await (await request.get(
        `${API_BASE}/api/v1/appeals/${appeal}/hearings`, { headers: REVIEWER })).json()).hearingHistory;

      // Two rows, not one overwritten row. The old code overwrote Appeal.hearingDate, destroying any
      // record that the earlier sitting was ever fixed — an audit failure for a statutory hearing.
      expect(history.length).toBe(2);
      expect(history.some((h: any) => h.superseded === true),
        'the vacated sitting must remain, marked superseded').toBe(true);

      // The screen binds these exact keys.
      for (const key of ['id', 'date', 'venue', 'eventType', 'outcome', 'superseded']) {
        expect(history[0]).toHaveProperty(key);
      }
    });

  test('the SLA object carries everything the screen renders, and reports a breach',
    async ({ request }) => {
      const parent = `${PREFIX}-C-8`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);

      const sla = (await detail(request, appeal, DO)).sla;
      for (const key of ['stage', 'stageAllowedDays', 'tracked', 'deadline', 'daysRemaining',
                         'breached', 'statusKey']) {
        expect(sla, `sla.${key} is bound by the screen`).toHaveProperty(key);
      }
      expect(sla.breached).toBe(false);
      expect(sla.statusKey).toMatch(/^aa\.sla\./);

      // Backdate the stage clock past the configured allowance. The maths is server-side working days
      // against the holiday master; the browser must never recompute it.
      sql(`UPDATE appeals SET updated_at = DATE_SUB(NOW(), INTERVAL 120 DAY)
            WHERE appeal_number = '${appeal}'`);

      const breached = (await detail(request, appeal, DO)).sla;
      expect(breached.breached).toBe(true);
      expect(breached.daysRemaining, 'overdue is expressed as a NEGATIVE remainder').toBeLessThan(0);
      expect(breached.statusKey).toBe('aa.sla.breached');
    });

  test('a refused write answers 200 with success:false — the convention the screens must handle',
    async ({ request }) => {
      const parent = `${PREFIX}-C-9`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);

      // PASS_ORDER from `filed` is illegal. This is the shape the screens now check for: a rejected
      // write that is NOT an HTTP error, which is why treating any 200 as success reported phantom
      // successes.
      const refused = await act(request, appeal, SECRETARIAT,
        { action: 'PASS_ORDER', orderOutcome: 'UPHELD', orderSummary: 'illegal' });

      const body = await refused.json();
      const refusedVisibly = refused.status() === 409 || body.success === false;
      expect(refusedVisibly, 'the server must visibly refuse this').toBe(true);
      expect(body.messageKey, 'a refusal must carry a translation key for the UI').toBeTruthy();

      // And nothing was written.
      expect(sql(`SELECT status FROM appeals WHERE appeal_number = '${appeal}'`)).toBe('filed');
    });

  test('every key the AA screens render resolves in all ten locales', async ({ request }) => {
    // The screens render keys exclusively. An unseeded key renders as its own id on screen, so this is
    // the check that keeps raw keys off a citizen-facing page.
    const rendered = [
      'aa.detail.title', 'aa.detail.available_actions', 'aa.detail.no_actions_available',
      'aa.detail.sla_deadline', 'aa.detail.sla_overdue_by', 'aa.detail.reviewer_tier',
      'aa.action.accept', 'aa.action.pass_order', 'aa.action.escalate_to_tier2',
      'aa.action.schedule_hearing', 'aa.action.failed',
      'aa.timeline.filed', 'aa.timeline.pass_order',
      'aa.order.title', 'aa.order.outcome', 'aa.order.award_amount', 'aa.order.error_failed',
      'aa.order.outcome_upheld', 'aa.order.outcome_modified',
      'aa.hearing.title', 'aa.hearing.reschedule_title', 'aa.hearing.notice_recorded_caveat',
      'aa.hearing.history', 'aa.hearing.error_conflict', 'aa.hearing.mode_video',
      'aa.draft.title', 'aa.draft.not_maintainable', 'aa.draft.related_caveat',
      'aa.draft.ocr_low_confidence', 'aa.draft.route_onward',
      'aa.sla.on_track', 'aa.sla.breached',
    ];

    const english = await fetchTranslations(request, 'en');
    for (const locale of TEN_LOCALES) {
      const map = await fetchTranslations(request, locale);
      const missing = rendered.filter(k => !map[k] || !map[k].trim());
      expect(missing, `locale ${locale} is missing: ${missing.join(', ')}`).toEqual([]);

      if (locale !== 'en') {
        // A key present but identical to English is an untranslated placeholder, which reads as a bug to
        // a citizen using the portal in their own language.
        const copied = rendered.filter(k => map[k].trim() === english[k]?.trim());
        expect(copied, `locale ${locale} copied English verbatim for: ${copied.join(', ')}`).toEqual([]);
      }
    }
  });
});
