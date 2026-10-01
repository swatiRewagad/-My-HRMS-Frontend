/**
 * Filing TIMELINES — the configured complaint and appeal filing windows, end to end.
 *
 * `timeline-config.spec.ts` (UST115) already covers the config ENGINE generically: that the listing
 * is populated, that a write round-trips, that 0 / -1 / 999 / 'abc' / '' are refused with distinct
 * messages, that a non-timeline key cannot be rewritten, and that a refused write leaves no audit
 * row. None of that is repeated here.
 *
 * This file covers the two windows an administrator actually configures — the COMPLAINT filing window
 * and the APPEAL filing window — and, crucially, whether configuring them CHANGES WHAT THE SERVER
 * ACCEPTS. A config screen that stores a number nothing reads is worse than no screen at all: the
 * administrator believes a statutory limit is in force when it is not.
 *
 * ── Why the enforcement assertions read the filing APIs ─────────────────────────────────────────
 * The only proof that a window is enforced is that a submission outside it is refused. So the
 * enforcement cases set the config, then submit through the real intake / appeal endpoints and assert
 * on the server's answer, never on the config echo.
 *
 * ── Shared mutable state ────────────────────────────────────────────────────────────────────────
 * SYSTEM_CONFIG is shared and persistent, and a concurrent session has already been observed moving
 * `timeline.complaint.filing_window_days` off its seeded value. So NO test here assumes a starting
 * value: every test reads the current one, and restores exactly that in a `finally`.
 *
 * AppealEligibilityService.getConfigInt reads SystemConfigRepository directly (no cache), verified
 * live: a PUT of 60 changed the verdict on the very next request. So these tests do NOT need
 * setSystemConfig's 35-second cache wait, and must not be "fixed" by adding one.
 */
import { test, expect } from '../fixtures';
import { APIRequestContext, Page } from '@playwright/test';
import { createTestComplaint, advanceToStatus } from '../utils/test-data';
import { sql } from '../aa/aa-shared-fixtures';
import { loginAsRbioRole } from '../utils/auth';

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

const TIMELINES = `${API_BASE}/api/v1/admin/config/timelines`;

/**
 * ADMIN, stated explicitly rather than derived.
 *
 * identityHeadersFor('cms.admin') would yield CEPC_ADMIN, which the admin controllers refuse. The
 * timeline config controller happens to check nothing at all, but the headers still have to name a
 * real identity so the audit rows this file asserts on are attributable.
 */
const ADMIN = { 'X-User-Id': 'cms.admin', 'X-User-Name': 'ft-admin', 'X-User-Roles': 'ADMIN' };

const COMPLAINT_WINDOW_KEY = 'timeline.complaint.filing_window_days';
const APPEAL_WINDOW_KEY = 'timeline.appeal.filing_window_days';
const APPEAL_EXTENDED_KEY = 'timeline.appeal.extended_window_days';

const PREFIX = 'FTWIN';

// ── config plumbing ───────────────────────────────────────────────────────────────────────────────

async function listConfigs(request: APIRequestContext): Promise<Array<Record<string, string>>> {
  const res = await request.get(TIMELINES, { headers: ADMIN });
  expect(res.status(), `GET ${TIMELINES} — is the backend at ${API_BASE} up?`).toBe(200);
  const configs = (await res.json()) as Array<Record<string, string>>;
  expect(Array.isArray(configs)).toBeTruthy();
  return configs;
}

/** Current stored value, read back through the listing rather than trusting a PUT echo. */
async function readConfig(request: APIRequestContext, key: string): Promise<string> {
  const row = (await listConfigs(request)).find((c) => c['configKey'] === key);
  expect(
    row,
    `${key} is absent from SYSTEM_CONFIG, so the window it governs cannot be configured at all`
  ).toBeTruthy();
  return row!['configValue'];
}

/** Writes a value and asserts it was accepted. Used for setup, so a silent refusal cannot hide. */
async function writeConfig(
  request: APIRequestContext,
  key: string,
  value: string,
  changedBy = 'ft-admin'
): Promise<void> {
  const res = await request.put(`${TIMELINES}/${key}`, {
    data: { value },
    headers: { ...ADMIN, 'X-User-Name': changedBy },
  });
  expect(res.status(), `setting ${key}=${value} must succeed before anything can be asserted about it`)
    .toBe(200);
}

// ── complaint fixtures ────────────────────────────────────────────────────────────────────────────

/** Creates a complaint, drives it to closed, and back-dates closure so an appeal age can be set. */
async function complaintClosedDaysAgo(
  request: APIRequestContext,
  daysAgo: number
): Promise<string> {
  const created = await createTestComplaint(request, {
    subject: `${PREFIX} filing-window fixture ${daysAgo}d ${Date.now().toString(36)}`,
  });
  await advanceToStatus(request, created.complaintNumber, 'closed');

  if (!/^[A-Za-z0-9-]+$/.test(created.complaintNumber)) {
    throw new Error(`Refusing an unexpected complaint number in SQL: ${created.complaintNumber}`);
  }
  // Back-dated, because "an appeal filed 45 days after closure" cannot otherwise be produced inside
  // a test. resolved_at is moved with it: AppealEligibilityService falls back to resolved_at, and
  // leaving it at NOW() would make the age depend on which column happened to be populated.
  sql(
    `UPDATE complaints SET closed_at = DATE_SUB(NOW(), INTERVAL ${Math.round(daysAgo)} DAY), ` +
    `resolved_at = DATE_SUB(NOW(), INTERVAL ${Math.round(daysAgo)} DAY) ` +
    `WHERE complaint_number = '${created.complaintNumber}'`
  );
  return created.complaintNumber;
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - days);
  return d.toISOString().slice(0, 10);
}

/** Registers a public complaint whose grievance to the entity was raised `days` ago. */
async function registerComplaintAgedDays(request: APIRequestContext, days: number) {
  const suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  return request.post(`${API_BASE}/api/v1/complaints`, {
    headers: { 'Content-Type': 'application/json' },
    data: {
      complainantName: `${PREFIX} Complainant ${suffix}`,
      complainantEmail: `${PREFIX.toLowerCase()}_${suffix}@example.com`,
      complainantPhone: '9876543210',
      complainantAddress: '123 Test Street, Mumbai',
      subject: `${PREFIX} filing window ${days}d ${suffix}`,
      description: `Filing-window enforcement probe: grievance raised with the entity ${days} days ago.`,
      entityName: 'Test Bank Ltd',
      filingType: 'ONLINE',
      priorReComplaint: true,
      reComplaintDate: isoDaysAgo(days),
      reRepliedAndDissatisfied: true,
      declarationAccepted: true,
    },
  });
}

/** Files an appeal WITHOUT a delay justification, returning the raw response so refusals are visible. */
function fileAppealRaw(
  request: APIRequestContext,
  complaintNumber: string,
  extra: Record<string, string> = {}
) {
  const suffix = Date.now().toString(36);
  return request.post(`${API_BASE}/api/v1/appeals/file`, {
    headers: { 'X-User-Id': 'aa_registrar_001', 'X-User-Name': 'aa_registrar_001', 'X-User-Roles': 'AA_DO' },
    multipart: {
      complaintNumber,
      ground: `${PREFIX} appeal ground ${suffix}`,
      details: `${PREFIX} appeal details for the filing-window check ${suffix}`,
      reliefSought: 'Corrective action',
      classification: 'APPEAL',
      ...extra,
    },
  });
}

// ── UI plumbing ───────────────────────────────────────────────────────────────────────────────────

/**
 * Candidate locations for an administrator's filing-timelines screen.
 *
 * Probed rather than hardcoded so case 8 fails on the ABSENCE of the screen and not on a routing
 * rename. Note that app.routes.ts ends in `{ path: '**', redirectTo: '' }`, so an unknown admin URL
 * silently lands on the public home page — the screen's absence is invisible from the URL alone, and
 * has to be asserted on rendered content.
 */
const CANDIDATE_SCREENS = [
  '/admin/config/timelines',
  '/admin/timelines',
  '/admin/filing-timelines',
  '/admin/filing-windows',
  '/admin/master-data',
  '/admin/dashboard',
];

/** Editable numeric-looking inputs whose surrounding label mentions the given subject and a window. */
async function windowFieldCount(page: Page, subject: RegExp): Promise<number> {
  const inputs = page.locator('input:not([type=hidden]):not([disabled]), select:not([disabled])');
  const total = await inputs.count();
  let matches = 0;
  for (let i = 0; i < total; i++) {
    const input = inputs.nth(i);
    // The label text as the operator sees it: an explicit <label>, an aria-label, the placeholder, or
    // the text of the row the control sits in.
    const described = (
      (await input.getAttribute('aria-label')) + ' ' +
      (await input.getAttribute('placeholder')) + ' ' +
      (await input.getAttribute('name')) + ' ' +
      (await input.getAttribute('formcontrolname')) + ' ' +
      (await input.locator('xpath=ancestor::*[self::tr or self::label or self::div][1]')
        .textContent()
        .catch(() => '') ?? '')
    );
    if (subject.test(described) && /window|deadline|days|timeline/i.test(described)) {
      matches++;
    }
  }
  return matches;
}

test.describe('Filing timelines — configuring the complaint and appeal windows, and enforcing them', () => {

  /**
   * Case 8. The administrator needs a SCREEN. The API exists, but an administrator does not curl.
   *
   * Asserted through the browser as an ADMIN, over every plausible route, requiring editable fields
   * for BOTH windows on one screen — which is the requirement as written ("showing editable fields
   * for both complaint and appeal filing windows").
   */
  test('an admin can open a filing-timelines screen with editable complaint AND appeal window fields', async ({ page }) => {
    // cms.admin, not cepc_admin1: admin/* is guarded by staffRoleGuard(['ADMIN','CRPC_ADMIN',
    // 'CRPC_HEAD']) and cepc_admin1 carries only CEPC_ADMIN, so it would be bounced to
    // /staff/unauthorized and the test would report a missing screen that is merely a wrong login.
    await loginAsRbioRole(page, 'RBIO_ADMIN', `${APP_BASE}/admin/dashboard`);

    const findings: string[] = [];
    let found = false;

    for (const route of CANDIDATE_SCREENS) {
      await page.goto(`${APP_BASE}${route}`, { waitUntil: 'networkidle' }).catch(() => {});
      await page.waitForTimeout(1500);

      const complaintFields = await windowFieldCount(page, /complaint/i);
      const appealFields = await windowFieldCount(page, /appeal/i);
      findings.push(`${route}: landed on ${new URL(page.url()).pathname}, ` +
        `complaint-window fields=${complaintFields}, appeal-window fields=${appealFields}`);

      if (complaintFields > 0 && appealFields > 0) {
        found = true;
        break;
      }
    }

    expect(
      found,
      'no admin screen exposes editable complaint AND appeal filing-window fields. ' +
        'GET/PUT /api/v1/admin/config/timelines exists (TimelineConfigController), but no Angular ' +
        'route or component consumes it, and app.routes.ts redirects unknown paths to the public ' +
        `home page so the absence is silent. Probed: ${findings.join(' | ')}`
    ).toBeTruthy();
  });

  /**
   * Case 9. The COMPLAINT window persists. Read back through GET, because the PUT response is built
   * from the in-memory entity and would report the new value even if nothing were committed.
   */
  test('an admin update to the complaint filing window persists on re-read', async ({ request }) => {
    const original = await readConfig(request, COMPLAINT_WINDOW_KEY);
    // Derived from what is actually stored, never a fixed pair: a concurrent session has been
    // observed moving this key, and writing a value that is already stored would make the update a
    // no-op that passes vacuously.
    const target = String(Number(original) === 31 ? 32 : 31);
    try {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, target);
      expect(
        await readConfig(request, COMPLAINT_WINDOW_KEY),
        'the complaint filing window did not survive a re-read, so the change was never committed'
      ).toBe(target);
    } finally {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, original);
    }
  });

  /**
   * Case 10. The APPEAL window accepts 45 — a value inside the 30–60 range the requirement states —
   * and persists it.
   */
  test('an admin can set the appeal filing window to 45 days and it persists', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    try {
      await writeConfig(request, APPEAL_WINDOW_KEY, '45');
      expect(
        await readConfig(request, APPEAL_WINDOW_KEY),
        'the appeal filing window did not survive a re-read'
      ).toBe('45');
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, original);
    }
  });

  /**
   * Case 11. With NO configured value, the statutory default of 30 days governs.
   *
   * The row is removed rather than set to something invalid, because "unset" is the condition under
   * test: AppealEligibilityService.DEFAULT_FILING_WINDOW_DAYS is only reached when the lookup finds
   * nothing. The window without the row is two HTTP calls wide and it is restored in `finally` with
   * its exact prior value.
   */
  test('with no configured appeal window the 30-day default governs', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    const atBoundary = await complaintClosedDaysAgo(request, 30);
    const pastBoundary = await complaintClosedDaysAgo(request, 31);

    try {
      sql(`DELETE FROM SYSTEM_CONFIG WHERE config_key = '${APPEAL_WINDOW_KEY}'`);

      const inside = await request.get(
        `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${atBoundary}`);
      expect(inside.status()).toBe(200);
      const insideData = (await inside.json()).data;
      expect(
        insideData.eligible,
        'an appeal 30 days after closure must be accepted under the 30-day default'
      ).toBe(true);
      expect(
        insideData.delayedFiling,
        'an appeal exactly 30 days after closure is inside the default window, so it must not be ' +
          'treated as a delayed filing needing justification'
      ).toBe(false);

      // And the default must actually be 30, not merely "large": the day after the boundary has to
      // fall outside the standard window, or any default at all would satisfy the test above.
      const outside = await request.get(
        `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${pastBoundary}`);
      const outsideData = (await outside.json()).data;
      expect(
        outsideData.delayedFiling,
        'with no configured value the default window is not 30 days: an appeal 31 days after ' +
          'closure was still treated as being inside the standard window'
      ).toBe(true);
      expect(String(outsideData.reason)).toMatch(/standard window: 30 days/);
    } finally {
      sql(
        `INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at) ` +
        `VALUES ('${APPEAL_WINDOW_KEY}', '${original}', ` +
        `'Days after closure to file an appeal without delay reason', 'ft-restore', NOW()) ` +
        `ON DUPLICATE KEY UPDATE config_value = '${original}'`
      );
      expect(await readConfig(request, APPEAL_WINDOW_KEY)).toBe(original);
    }
  });

  /**
   * Case 12. A complaint INSIDE the configured window is accepted.
   *
   * Paired deliberately with case 13: on its own this assertion is satisfied by a server that
   * enforces nothing at all, which is exactly the state case 13 exposes.
   */
  test('a complaint filed inside the configured filing window is accepted', async ({ request }) => {
    const original = await readConfig(request, COMPLAINT_WINDOW_KEY);
    try {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, '30');

      const res = await registerComplaintAgedDays(request, 30);
      expect(
        res.status(),
        `a complaint raised with the entity 30 days ago is inside a 30-day window and must be ` +
          `accepted; server said ${res.status()}: ${await res.text()}`
      ).toBe(201);
      expect((await res.json()).success).toBe(true);
    } finally {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, original);
    }
  });

  /**
   * Case 13. A complaint OUTSIDE the configured window is refused, and the refusal says so.
   *
   * `timeline.complaint.filing_window_days` has NO consumer anywhere in cms-backend — a repository
   * grep for the key returns only the seed row. The window is therefore configurable but inert, and
   * the separate window logic that does exist (EligibilityWizardController) runs off @Value
   * properties with a hardcoded 365-day limit and is never consulted at registration.
   *
   * Asserted as it MUST behave, so the gap is recorded rather than accommodated.
   */
  test('a complaint filed outside the configured filing window is refused with a filing-window error', async ({ request }) => {
    const original = await readConfig(request, COMPLAINT_WINDOW_KEY);
    try {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, '30');

      const res = await registerComplaintAgedDays(request, 35);
      const body = await res.text();

      expect(
        res.status(),
        'a complaint raised with the entity 35 days ago, against a configured 30-day filing window, ' +
          'was accepted. No code reads timeline.complaint.filing_window_days, so the configured ' +
          `window is never applied at registration. Server said ${res.status()}: ${body}`
      ).toBe(400);
      expect(
        body,
        'the refusal must name the filing window, or the citizen cannot tell a timing bar from a ' +
          'validation error'
      ).toMatch(/filing window|outside|time-barred|deadline/i);
    } finally {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, original);
    }
  });

  /**
   * Case 14. An appeal inside the configured window is accepted outright — no delay justification
   * demanded, which is the substance of "within the window".
   */
  test('an appeal filed inside the configured appeal window is accepted', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    try {
      await writeConfig(request, APPEAL_WINDOW_KEY, '30');
      const complaintNumber = await complaintClosedDaysAgo(request, 30);

      const res = await fileAppealRaw(request, complaintNumber);
      expect(
        res.status(),
        `an appeal 30 days after closure is inside a 30-day window and must be accepted without a ` +
          `delay justification; server said ${res.status()}: ${await res.text()}`
      ).toBe(201);
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, original);
    }
  });

  /**
   * Case 15. An appeal BEYOND the configured window is refused, and the refusal names the window.
   *
   * The second assertion documents what the product does instead of an outright bar: 31–60 days is a
   * DELAYED filing, admissible with a recorded reason (AppealEligibilityService, 3-tier model). That
   * is a condonation-of-delay design, not an oversight — but it means "beyond the window" is refused
   * only while no justification is offered. Flagged for confirmation: if the requirement intends an
   * absolute bar at the standard window, the second assertion is the defect.
   */
  test('an appeal filed beyond the configured appeal window is refused', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    try {
      await writeConfig(request, APPEAL_WINDOW_KEY, '30');

      const bare = await complaintClosedDaysAgo(request, 35);
      const refused = await fileAppealRaw(request, bare);
      const refusedBody = await refused.text();
      expect(
        refused.status(),
        `an appeal 35 days after closure, against a configured 30-day window, was accepted with no ` +
          `justification at all; server said ${refused.status()}: ${refusedBody}`
      ).toBe(400);
      expect(
        refusedBody,
        'the refusal must explain that the standard filing window has passed'
      ).toMatch(/window|delay|deadline|not eligible/i);

      // What the product admits instead, asserted so the 3-tier behaviour is on the record rather
      // than inferred from a passing status code.
      const justified = await complaintClosedDaysAgo(request, 35);
      const condoned = await fileAppealRaw(request, justified, {
        reasonForDelay: 'Appellant was hospitalised during the standard filing window.',
      });
      expect(
        condoned.status(),
        'an appeal 35 days after closure WITH a recorded reason for delay is admitted under the ' +
          'condonation tier. If the requirement intends an absolute bar at the standard window, ' +
          'this is the non-compliance.'
      ).toBe(201);
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, original);
    }
  });

  /**
   * Case 16. Widening the window to 60 makes a 60-day-old appeal ordinary, not delayed. This is the
   * assertion that proves the configured value is READ rather than stored: at window 30 the same
   * complaint would have required a delay reason.
   */
  test('with the appeal window set to 60, an appeal at 60 days is accepted', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    try {
      await writeConfig(request, APPEAL_WINDOW_KEY, '60');
      const complaintNumber = await complaintClosedDaysAgo(request, 60);

      const eligibility = await request.get(
        `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${complaintNumber}`);
      const data = (await eligibility.json()).data;
      expect(data.eligible).toBe(true);
      expect(
        data.delayedFiling,
        'with a 60-day window configured, an appeal at 60 days is inside it and must not be ' +
          'treated as delayed — otherwise the configured value is not being read'
      ).toBe(false);

      const res = await fileAppealRaw(request, complaintNumber);
      expect(
        res.status(),
        `appeal at 60 days with a 60-day window must be accepted; server said ${res.status()}: ` +
          `${await res.text()}`
      ).toBe(201);
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, original);
    }
  });

  /**
   * Case 17. Past the MAXIMUM window there is no condonation: 61 days is refused outright and the
   * refusal states the maximum.
   *
   * Both window keys are pinned to 60. Pinning only the standard window would leave the maximum at
   * whatever a concurrent session had left behind, and a maximum of 90 would make 61 days admissible
   * for a reason that has nothing to do with this requirement.
   */
  test('with the appeal window set to 60, an appeal at 61 days is refused as exceeding the maximum', async ({ request }) => {
    const originalWindow = await readConfig(request, APPEAL_WINDOW_KEY);
    const originalExtended = await readConfig(request, APPEAL_EXTENDED_KEY);
    try {
      await writeConfig(request, APPEAL_WINDOW_KEY, '60');
      await writeConfig(request, APPEAL_EXTENDED_KEY, '60');
      const complaintNumber = await complaintClosedDaysAgo(request, 61);

      const eligibility = await request.get(
        `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${complaintNumber}`);
      const data = (await eligibility.json()).data;
      expect(
        data.eligible,
        'an appeal 61 days after closure exceeds the 60-day maximum and must be ineligible'
      ).toBe(false);
      expect(
        String(data.reason),
        'the refusal must state the maximum filing window, not merely that the appeal is ineligible'
      ).toMatch(/within 60 days|deadline exceeded/i);

      // And the filing endpoint must honour it, not merely the eligibility probe. A reason for delay
      // is supplied precisely so the refusal cannot be attributed to a missing justification.
      const res = await fileAppealRaw(request, complaintNumber, {
        reasonForDelay: 'Testing that no justification can revive an appeal past the maximum window.',
      });
      expect(
        res.status(),
        `filing an appeal 61 days after closure past a 60-day maximum must be refused; server said ` +
          `${res.status()}: ${await res.text()}`
      ).toBe(400);
      expect(await res.text()).toMatch(/within 60 days|deadline exceeded|not eligible/i);
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, originalWindow);
      await writeConfig(request, APPEAL_EXTENDED_KEY, originalExtended);
    }
  });

  /**
   * Case 18. The COMPLAINT-window change is attributable: who, when, from what, to what.
   *
   * The audit row is matched on the specific old→new pair this test caused, not merely "the newest
   * row" — five other suites write to this table concurrently and the latest row is frequently not
   * ours.
   */
  test('changing the complaint filing window writes an audit entry', async ({ request }) => {
    const original = await readConfig(request, COMPLAINT_WINDOW_KEY);
    const target = String(Number(original) === 33 ? 34 : 33);
    const actor = `ft-complaint-audit-${Date.now().toString(36)}`;
    try {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, target, actor);

      const res = await request.get(`${TIMELINES}/audit/${COMPLAINT_WINDOW_KEY}`, { headers: ADMIN });
      expect(res.status()).toBe(200);
      const logs = (await res.json()) as any[];

      const ours = logs.find((l) => l.changedBy === actor);
      expect(
        ours,
        `no CONFIG_AUDIT_LOG row attributes the complaint-window change to ${actor}, so the change ` +
          'is unattributable'
      ).toBeTruthy();
      expect(ours.configKey).toBe(COMPLAINT_WINDOW_KEY);
      expect(ours.oldValue, 'the audit row must record what the value was').toBe(original);
      expect(ours.newValue).toBe(target);
      expect(ours.changedAt, 'the audit row must record when').toBeTruthy();
    } finally {
      await writeConfig(request, COMPLAINT_WINDOW_KEY, original);
    }
  });

  /** Case 19. The same guarantee for the APPEAL window. */
  test('changing the appeal filing window writes an audit entry', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    const target = String(Number(original) === 44 ? 43 : 44);
    const actor = `ft-appeal-audit-${Date.now().toString(36)}`;
    try {
      await writeConfig(request, APPEAL_WINDOW_KEY, target, actor);

      const res = await request.get(`${TIMELINES}/audit/${APPEAL_WINDOW_KEY}`, { headers: ADMIN });
      expect(res.status()).toBe(200);
      const logs = (await res.json()) as any[];

      const ours = logs.find((l) => l.changedBy === actor);
      expect(
        ours,
        `no CONFIG_AUDIT_LOG row attributes the appeal-window change to ${actor}`
      ).toBeTruthy();
      expect(ours.configKey).toBe(APPEAL_WINDOW_KEY);
      expect(ours.oldValue).toBe(original);
      expect(ours.newValue).toBe(target);
      expect(ours.changedAt).toBeTruthy();
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, original);
    }
  });

  /**
   * Case 20. Input validation on the appeal filing window, every value submitted and then RE-READ.
   *
   * Re-reading is the whole point. A refusal status proves the API said no; only the read-back proves
   * nothing was written. And an acceptance echo proves nothing was rejected; only the read-back
   * proves it was committed.
   *
   * The range asserted is 30–60, as the requirement states. The service validates 1–365
   * (TimelineConfigService.updateConfig), so 29 and 61 are currently ACCEPTED — asserted as they must
   * behave, because a window of 1 day silently defeats the statutory minimum this field exists to
   * express.
   */
  test('appeal filing window input validation, verified by re-reading each submitted value', async ({ request }) => {
    const original = await readConfig(request, APPEAL_WINDOW_KEY);
    const tablesBefore = sql(
      `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'cms_db'`);
    const configRowsBefore = sql(`SELECT COUNT(*) FROM SYSTEM_CONFIG`);

    const failures: string[] = [];

    try {
      // Accepted: the stated valid value and both boundaries.
      for (const value of ['45', '30', '60']) {
        const res = await request.put(`${TIMELINES}/${APPEAL_WINDOW_KEY}`, {
          data: { value }, headers: { ...ADMIN, 'X-User-Name': 'ft-sweep' },
        });
        if (res.status() !== 200) {
          failures.push(`${value} was refused (${res.status()}: ${await res.text()}) but is inside the 30–60 range`);
          continue;
        }
        const stored = await readConfig(request, APPEAL_WINDOW_KEY);
        if (stored !== value) {
          failures.push(`${value} was accepted but re-read as ${stored}, so it was never committed`);
        }
      }

      // Refused: each must be rejected AND leave the stored value untouched.
      const rejects: Array<{ value: string; why: string }> = [
        { value: '29', why: 'below the 30-day minimum, so it would permit a window shorter than the statutory one' },
        { value: '61', why: 'above the 60-day maximum' },
        { value: '45.5', why: 'a filing window cannot be fractional days' },
        { value: 'forty-five', why: 'not a number' },
        { value: '', why: 'empty' },
        { value: '45@', why: 'contains a non-numeric character' },
        { value: '45; DROP TABLE Users;', why: 'a SQL injection attempt' },
      ];

      for (const { value, why } of rejects) {
        const before = await readConfig(request, APPEAL_WINDOW_KEY);
        const res = await request.put(`${TIMELINES}/${APPEAL_WINDOW_KEY}`, {
          data: { value }, headers: { ...ADMIN, 'X-User-Name': 'ft-sweep' },
        });
        const body = await res.text();

        if (res.status() !== 400) {
          failures.push(`"${value}" (${why}) was not refused — HTTP ${res.status()}: ${body}`);
        } else if (!body || !/error/i.test(body)) {
          failures.push(`"${value}" was refused with no explanation, so the screen has nothing to show`);
        }

        const after = await readConfig(request, APPEAL_WINDOW_KEY);
        if (after !== before) {
          failures.push(`"${value}" changed the stored value from ${before} to ${after}`);
        }
      }

      // The injection attempt must not have touched the schema or any other config row.
      const tablesAfter = sql(
        `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'cms_db'`);
      const configRowsAfter = sql(`SELECT COUNT(*) FROM SYSTEM_CONFIG`);
      if (tablesAfter !== tablesBefore) {
        failures.push(`the table count moved from ${tablesBefore} to ${tablesAfter} — the injection ran`);
      }
      if (configRowsAfter !== configRowsBefore) {
        failures.push(`SYSTEM_CONFIG row count moved from ${configRowsBefore} to ${configRowsAfter}`);
      }

      // Reported as one list so a single run names every defective value, rather than stopping at
      // the first and hiding the rest behind a re-run.
      expect(failures, `appeal-window validation defects:\n  - ${failures.join('\n  - ')}`).toEqual([]);
    } finally {
      await writeConfig(request, APPEAL_WINDOW_KEY, original);
    }
  });
});
