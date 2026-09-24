/**
 * Retention LIFECYCLE — the 7-year obligation as experienced by a complaint record.
 *
 * `retention.spec.ts` (UST890) already covers the retention ENGINE: that policies exist, that audit
 * categories carry their own 7-year period, that a run is a dry run by default, that the deletion log
 * records it, and that the endpoints are admin-only. None of that is repeated here.
 *
 * This file covers the obligation from the other end — a closed complaint that must survive seven
 * years, stay searchable and viewable throughout, be protected from an ad-hoc deletion, and be
 * flagged for review before anything purges it. Those are properties of the RECORD, not of the
 * policy table, so they are asserted against the complaint itself.
 *
 * ── Why server-side truth everywhere ────────────────────────────────────────────────────────────
 * A retention breach is invisible in the UI by definition: a deleted record simply stops appearing,
 * which looks identical to a filter. So every assertion here reads either the API or cms_db.
 *
 * ── Fixtures ────────────────────────────────────────────────────────────────────────────────────
 * Complaints are tagged RETLC- in their subject. Nothing is truncated; cms_db is shared and
 * persistent. closed_at is back-dated on our OWN fixture rows only, because "a record six years into
 * its retention period" cannot be produced any other way inside a test.
 */
import { test, expect } from '../fixtures';
import { APIRequestContext } from '@playwright/test';
import { createTestComplaint, advanceToStatus, identityHeadersFor } from '../utils/test-data';
import { sql } from '../aa/aa-shared-fixtures';

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/** The statutory period, in days, as the seeded policies express it. */
const SEVEN_YEARS_DAYS = 2555;

/**
 * ADMIN, stated explicitly.
 *
 * identityHeadersFor('cms.admin') derives CEPC_ADMIN from the name, which SecurityAdminController
 * correctly refuses — requireAdmin() wants the bare ADMIN role. Deriving it would make every
 * assertion here fail with 403 and look like a broken feature.
 */
const ADMIN = { 'X-User-Id': 'cms.admin', 'X-User-Roles': 'ADMIN' };
const STAFF = identityHeadersFor('cepc_do_001');

const PREFIX = 'RETLC';

interface Fixture {
  complaintNumber: string;
  complaintId: number;
}

/** Creates a complaint, drives it to closed, and returns its number and numeric id. */
async function closedComplaint(
  request: APIRequestContext,
  filingType: 'CEPC_MANUAL' | 'PHYSICAL_LETTER' | 'EMAIL' = 'CEPC_MANUAL'
): Promise<Fixture> {
  const created = await createTestComplaint(request, {
    subject: `${PREFIX} retention fixture ${filingType} ${Date.now().toString(36)}`,
    filingType,
  });
  await advanceToStatus(request, created.complaintNumber, 'closed');
  return {
    complaintNumber: created.complaintNumber,
    complaintId: Number(created.complaintId),
  };
}

/**
 * Moves a fixture's closure date into the past.
 *
 * The whole point of cases 2/3 is a record part-way through its retention period. A complaint closed
 * seconds ago would pass a "still visible" assertion no matter how retention behaved, so the test
 * would prove nothing.
 */
function backdateClosure(complaintNumber: string, yearsAgo: number): void {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) {
    throw new Error(`Refusing an unexpected complaint number in SQL: ${complaintNumber}`);
  }
  sql(
    `UPDATE complaints SET closed_at = DATE_SUB(NOW(), INTERVAL ${Math.round(yearsAgo * 365)} DAY) ` +
    `WHERE complaint_number = '${complaintNumber}'`
  );
}

async function policies(request: APIRequestContext): Promise<any[]> {
  const res = await request.get(`${API_BASE}/api/v1/admin/security/retention/policies`, {
    headers: ADMIN,
  });
  expect(res.status(), `GET retention/policies — is the backend at ${API_BASE} up?`).toBe(200);
  return (await res.json()).data ?? [];
}

/** True when a policy targets any of the given tables, compared case-insensitively. */
function targets(policy: any, tables: string[]): boolean {
  const t = String(policy.targetTable || '').toUpperCase();
  return tables.some((candidate) => candidate.toUpperCase() === t);
}

const COMPLAINT_TABLES = ['COMPLAINTS', 'COMPLAINT_MASTER'];
const ATTACHMENT_TABLES = ['COMPLAINT_ATTACHMENTS', 'ATTACHMENT_METADATA', 'APPEAL_ATTACHMENTS'];
const HISTORY_TABLES = ['COMPLAINT_TIMELINE', 'COMPLAINT_HISTORY'];

test.describe('Retention lifecycle — the 7-year obligation on a complaint record', () => {

  /**
   * Case 1. The obligation has to be DECLARED somewhere a purge will consult, for all three data
   * classes. A policy that does not exist cannot be honoured: nothing stops a future policy row from
   * naming complaint_attachments with a 30-day period, because no rule says otherwise.
   */
  test('complaint records, attachments and history each carry a declared 7-year retention', async ({ request }) => {
    const all = await policies(request);

    const complaintPolicies = all.filter((p) => targets(p, COMPLAINT_TABLES));
    const attachmentPolicies = all.filter((p) => targets(p, ATTACHMENT_TABLES));
    const historyPolicies = all.filter((p) => targets(p, HISTORY_TABLES));

    expect(
      complaintPolicies.length,
      'no retention policy covers the complaint record itself'
    ).toBeGreaterThan(0);
    expect(
      attachmentPolicies.length,
      'no retention policy covers complaint ATTACHMENTS, so their 7-year retention is undeclared ' +
        'and a shorter policy row would be permitted by the engine (RetentionService.ALLOWED_TABLES ' +
        'already whitelists COMPLAINT_ATTACHMENTS / ATTACHMENT_METADATA)'
    ).toBeGreaterThan(0);
    expect(
      historyPolicies.length,
      'no retention policy covers complaint HISTORY (COMPLAINT_TIMELINE), so the record of what was ' +
        'done to a complaint has no declared retention at all'
    ).toBeGreaterThan(0);

    // Every declared policy over these three classes must run at least the statutory period, and it
    // must be measured from CLOSURE — a period measured from filing would expire early for a
    // complaint that stayed open for two years.
    for (const policy of [...complaintPolicies, ...attachmentPolicies, ...historyPolicies]) {
      expect(
        policy.retentionDays,
        `${policy.category} keeps ${policy.targetTable} for ${policy.retentionDays} days, ` +
          `less than the ${SEVEN_YEARS_DAYS}-day statutory minimum`
      ).toBeGreaterThanOrEqual(SEVEN_YEARS_DAYS);
    }
  });

  /**
   * Case 2. Six years into the period the record is still findable by an authorised user. Search, not
   * a direct fetch by key: a record that can only be reached by already knowing its number is not
   * meaningfully retained for an investigator.
   */
  test('a record six years into its retention period is still searchable', async ({ request }) => {
    const fixture = await closedComplaint(request);
    backdateClosure(fixture.complaintNumber, 6);

    const res = await request.get(
      `${API_BASE}/api/complaints?search=${encodeURIComponent(fixture.complaintNumber)}`,
      { headers: STAFF }
    );
    expect(res.status()).toBe(200);

    const rows = (await res.json()) as any[];
    expect(Array.isArray(rows)).toBeTruthy();
    expect(
      rows.some((r) => r.complaintNumber === fixture.complaintNumber),
      `${fixture.complaintNumber} closed six years ago is inside the 7-year window but search ` +
        'does not return it'
    ).toBeTruthy();
  });

  /**
   * Case 3. Viewable, and viewable COMPLETELY: the record, its attachments and its history. A shell
   * with no history is not the retained artefact an audit needs.
   */
  test('a record six years into its retention period is viewable with its attachments and history', async ({ request }) => {
    const fixture = await closedComplaint(request);

    // An attachment of our own, so "attachments survive" is asserted against something that exists.
    const upload = await request.post(`${API_BASE}/api/files/upload`, {
      headers: { ...STAFF, 'X-User-Name': 'cepc_do_001' },
      multipart: {
        file: {
          name: 'retention-evidence.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from('evidence that must survive seven years'),
        },
        complaintNumber: fixture.complaintNumber,
        complaintId: String(fixture.complaintId),
      },
    });
    expect(upload.status(), 'the attachment fixture must upload before retention can be asserted').toBe(200);

    backdateClosure(fixture.complaintNumber, 6);

    const detail = await request.get(
      `${API_BASE}/api/v1/complaints/${fixture.complaintNumber}`, { headers: STAFF });
    expect(detail.status(), 'the complaint record must still be viewable').toBe(200);

    const attachments = await request.get(
      `${API_BASE}/api/files/complaint/${fixture.complaintId}`, { headers: STAFF });
    expect(attachments.status()).toBe(200);
    const attachmentRows = (await attachments.json()) as any[];
    expect(
      attachmentRows.length,
      'the attachment is gone six years into a seven-year retention period'
    ).toBeGreaterThan(0);

    const history = await request.get(
      `${API_BASE}/api/complaints/${fixture.complaintId}/timeline`, { headers: STAFF });
    expect(history.status()).toBe(200);
    const historyRows = (await history.json()) as any[];
    expect(
      historyRows.length,
      'the complaint history is empty six years into a seven-year retention period'
    ).toBeGreaterThan(0);
  });

  /**
   * Case 4. Same guarantee whichever door the complaint came through.
   *
   * Asserted two ways, because either alone would be weak. First that no policy discriminates on
   * channel — a channel-qualified policy is how this requirement would actually be broken. Second
   * that a record from each channel is equally retrievable once closed and aged.
   */
  test('retention is identical across the portal, email and physical-letter channels', async ({ request }) => {
    const channels: Array<'CEPC_MANUAL' | 'EMAIL' | 'PHYSICAL_LETTER'> = [
      'CEPC_MANUAL', 'EMAIL', 'PHYSICAL_LETTER',
    ];

    const all = await policies(request);
    for (const policy of all.filter((p) => targets(p, COMPLAINT_TABLES))) {
      const blob = JSON.stringify(policy).toUpperCase();
      for (const channel of ['FILING_TYPE', 'FILINGTYPE', 'PHYSICAL_LETTER', 'CHANNEL']) {
        expect(
          blob.includes(channel),
          `retention policy ${policy.category} mentions ${channel}, so retention is not ` +
            'channel-neutral'
        ).toBeFalsy();
      }
    }

    for (const channel of channels) {
      const fixture = await closedComplaint(request, channel);
      backdateClosure(fixture.complaintNumber, 6);

      const detail = await request.get(
        `${API_BASE}/api/v1/complaints/${fixture.complaintNumber}`, { headers: STAFF });
      expect(
        detail.status(),
        `a ${channel} complaint closed six years ago is not retrievable, so retention differs by channel`
      ).toBe(200);

      const row = sql(
        `SELECT filing_type FROM complaints WHERE complaint_number = '${fixture.complaintNumber}'`
      );
      expect(row, `filing_type was not persisted for a ${channel} complaint`).toBeTruthy();
    }
  });

  /**
   * Case 5. The destructive path.
   *
   * DELETE /api/complaints/{id} reaches ComplaintService.deleteComplaint, whose entire body is
   * `complaintRepository.deleteById(id)` — no role check beyond the URL-level staff grant, no
   * retention check, no audit write. A complaint one day into a seven-year retention period can be
   * erased irreversibly by any staff caller.
   *
   * Asserted as it MUST behave: refused, and the row still present. The read-back is the part that
   * matters — a refusal status with the row gone would be worse than either alone.
   */
  test('a complaint inside its retention period cannot be permanently deleted', async ({ request }) => {
    const fixture = await closedComplaint(request);

    const res = await request.delete(
      `${API_BASE}/api/complaints/${fixture.complaintId}`, { headers: STAFF });

    expect(
      res.status(),
      'a hard delete of a complaint inside its 7-year retention window must be refused'
    ).toBeGreaterThanOrEqual(400);

    const survivors = sql(
      `SELECT COUNT(*) FROM complaints WHERE complaint_number = '${fixture.complaintNumber}'`
    );
    expect(
      Number(survivors),
      `${fixture.complaintNumber} was permanently destroyed inside its retention period`
    ).toBe(1);
  });

  /**
   * Case 6. Nothing may be purged without a human looking first.
   *
   * `rowsPastRetention` is backward-looking — it counts what is ALREADY expired, which is too late to
   * review. The requirement is a forward-looking signal: records approaching the end of the period,
   * surfaced for review before any archival or purge action.
   *
   * Several plausible shapes are accepted (a field on the policy, or a dedicated review endpoint) so
   * the test fails only if NO such signal exists anywhere, rather than on a naming mismatch.
   */
  test('records approaching the end of retention are flagged for review before any purge', async ({ request }) => {
    const all = await policies(request);
    expect(all.length, 'no retention policies at all').toBeGreaterThan(0);

    const FORWARD_LOOKING_FIELDS = [
      'rowsApproachingRetention', 'rowsNearingExpiry', 'approachingRetention',
      'nearingExpiry', 'pendingReview', 'flaggedForReview', 'reviewRequired',
      'rowsDueForReview', 'daysUntilNextPurge',
    ];

    const policyLevelSignal = all.some((policy) =>
      FORWARD_LOOKING_FIELDS.some((field) => policy[field] !== undefined && policy[field] !== null)
    );

    // Any endpoint that answers 200 for an admin and names records awaiting review would satisfy this.
    const CANDIDATE_ENDPOINTS = [
      'retention/approaching', 'retention/review', 'retention/review-queue',
      'retention/pending-review', 'retention/upcoming',
    ];
    let endpointSignal = false;
    const probed: string[] = [];
    for (const path of CANDIDATE_ENDPOINTS) {
      const res = await request.get(`${API_BASE}/api/v1/admin/security/${path}`, { headers: ADMIN });
      probed.push(`${path}=${res.status()}`);
      if (res.status() === 200) {
        endpointSignal = true;
        break;
      }
    }

    expect(
      policyLevelSignal || endpointSignal,
      'nothing flags records approaching the end of the retention period. The policy rows expose ' +
        'only rowsPastRetention (already expired — too late to review), and no review endpoint ' +
        `answers: ${probed.join(', ')}. A purge would therefore run with no prior human review.`
    ).toBeTruthy();
  });

  /**
   * Case 7. An attempted retention breach must leave a trace and raise an alert.
   *
   * This is asserted about the ATTEMPT, deliberately, so the test stays valid once case 5 is fixed:
   * whether the deletion is permitted or refused, somebody trying to destroy a record inside its
   * retention period is an event an investigator has to be able to find.
   *
   * The audit trail is read from AUDIT_LOG directly because no read endpoint exposes it, and the
   * alert through the console the administrator would actually use.
   */
  test('an attempted deletion inside the retention period is audited and alerted', async ({ request }) => {
    const fixture = await closedComplaint(request);

    const alertsBefore = await request.get(
      `${API_BASE}/api/v1/admin/security/alerts?size=100`, { headers: ADMIN });
    expect(alertsBefore.status()).toBe(200);
    const beforeIds = new Set<number>(
      ((await alertsBefore.json()).data ?? []).map((a: any) => a.id)
    );

    await request.delete(`${API_BASE}/api/complaints/${fixture.complaintId}`, { headers: STAFF });

    const auditRows = sql(
      `SELECT action, actor FROM AUDIT_LOG WHERE complaint_number = '${fixture.complaintNumber}' ` +
      `AND (action LIKE '%DELET%' OR action LIKE '%PURG%' OR action LIKE '%RETENTION%')`
    );
    expect(
      auditRows,
      `the deletion attempt on ${fixture.complaintNumber} left no AUDIT_LOG row. ` +
        'ComplaintService.deleteComplaint writes no audit entry, so the record and the evidence ' +
        'that it was destroyed disappear together.'
    ).toBeTruthy();

    const alertsAfter = await request.get(
      `${API_BASE}/api/v1/admin/security/alerts?size=100`, { headers: ADMIN });
    const afterAlerts = ((await alertsAfter.json()).data ?? []) as any[];
    const raised = afterAlerts.filter((a) => !beforeIds.has(a.id));

    expect(
      raised.length,
      'destroying a complaint inside its retention period raised no security alert, so a retention ' +
        'violation would never reach the administrator who has to investigate it'
    ).toBeGreaterThan(0);
  });
});
