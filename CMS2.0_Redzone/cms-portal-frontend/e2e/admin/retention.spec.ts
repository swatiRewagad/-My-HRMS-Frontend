import { test, expect } from '@playwright/test';

/**
 * UST890 — retention policies, dry-run safety, and the deletion log.
 *
 * The destructive switch is deliberately NOT exercised here. cms_db is shared and persistent, so a
 * test that actually purged rows could destroy another developer's fixtures. These tests assert that
 * the engine evaluates policies, that audit categories carry their own longer retention, and that
 * nothing is deleted while dry-run is in force.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8093';
const ADMIN = { 'X-User-Id': 'cms.admin', 'X-User-Roles': 'ADMIN' };

test.describe('UST890 — retention policies', () => {

  test('policies exist for operational and audit data', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/admin/security/retention/policies`, { headers: ADMIN });
    expect(res.ok()).toBeTruthy();

    const body = await res.json();
    const categories = (body.data ?? []).map((p: any) => p.category);

    expect(categories).toContain('AUDIT_LOG');
    expect(categories).toContain('PII_REVEAL_AUDIT');
    expect(categories).toContain('SECURITY_ALERT');
  });

  /**
   * Audit logs must outlive the operational records they describe, otherwise purging a complaint
   * would also erase the evidence of who acted on it.
   */
  test('audit categories are retained for 7 years, independently', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/admin/security/retention/policies`, { headers: ADMIN });
    const policies = (await res.json()).data ?? [];

    const auditPolicies = policies.filter((p: any) => p.auditCategory);
    expect(auditPolicies.length).toBeGreaterThan(0);

    for (const policy of auditPolicies) {
      if (policy.category === 'SECURITY_EVENT') continue; // raw events: 2 years by design
      expect(policy.retentionDays).toBe(2555);
    }

    // Asserted, not guarded. `if (operational)` meant the whole operational-vs-audit comparison
    // vanished the moment the category went missing — and a missing operational policy is itself the
    // defect: with no shorter policy, operational data would inherit the 7-year audit retention and
    // the statutory distinction this test exists to prove would not exist in the data at all.
    const operational = policies.find((p: any) => p.category === 'IN_APP_NOTIFICATION');
    expect(
      operational,
      'IN_APP_NOTIFICATION policy is missing, so nothing separates operational data from the audit trail'
    ).toBeTruthy();
    // Operational data is shorter-lived than the audit trail.
    expect(operational.retentionDays).toBeLessThan(2555);
    expect(operational.auditCategory).toBe(false);

    // And the general rule, over every non-audit policy that is not deliberately a record-shell
    // redaction: an operational category retained as long as the audit trail defeats the point.
    for (const policy of policies.filter((p: any) => !p.auditCategory)) {
      if (policy.redactInsteadOfDelete) continue; // COMPLAINT_PII keeps the shell for 7 years by design
      expect(
        policy.retentionDays,
        `${policy.category} is operational but retained as long as the audit trail`
      ).toBeLessThan(2555);
    }
  });

  test('retention defaults to dry-run so nothing is destroyed', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/admin/security/retention/policies`, { headers: ADMIN });
    expect((await res.json()).destructiveEnabled).toBe(false);
  });

  test('a dry run reports candidates and deletes nothing', async ({ request }) => {
    const before = await request.get(
      `${API_BASE}/api/v1/admin/security/retention/deletion-log?size=1`, { headers: ADMIN });
    const countBefore = (await before.json()).totalElements ?? 0;

    const run = await request.post(
      `${API_BASE}/api/v1/admin/security/retention/run`, { headers: ADMIN, data: {} });
    expect(run.ok()).toBeTruthy();

    const body = await run.json();
    expect(body.dryRun).toBe(true);
    expect(body.message).toContain('Dry run');

    for (const result of body.data ?? []) {
      expect(result.dryRun).toBe(true);
    }

    // Even a dry run is recorded: the log is the reviewable evidence of what a real run would do.
    const after = await request.get(
      `${API_BASE}/api/v1/admin/security/retention/deletion-log?size=1`, { headers: ADMIN });
    expect((await after.json()).totalElements).toBeGreaterThan(countBefore);
  });

  test('the deletion log marks dry runs distinctly', async ({ request }) => {
    // A run of our own, so the log is guaranteed to contain a row this test caused. The previous
    // `test.skip(entries.length === 0)` meant an engine that recorded nothing at all — the exact
    // failure that makes a dry run unreviewable — read as a pass.
    const run = await request.post(
      `${API_BASE}/api/v1/admin/security/retention/run`, { headers: ADMIN, data: {} });
    expect(run.ok()).toBeTruthy();
    expect((await run.json()).dryRun, 'this must remain a dry run — cms_db is shared').toBe(true);

    const res = await request.get(
      `${API_BASE}/api/v1/admin/security/retention/deletion-log?size=20`, { headers: ADMIN });
    expect(res.ok()).toBeTruthy();

    const entries = (await res.json()).data ?? [];
    expect(
      entries.length,
      'a dry run just completed, so the deletion log must hold at least one row'
    ).toBeGreaterThan(0);
    // At least one entry must be marked as a dry run, or the flag is not being recorded and a real
    // purge would be indistinguishable from a rehearsal in the audit record.
    expect(entries.some((e: any) => e.dryRun === true)).toBeTruthy();

    for (const entry of entries) {
      expect(entry).toHaveProperty('category');
      expect(entry).toHaveProperty('rowsAffected');
      expect(entry).toHaveProperty('cutoffDate');
      expect(typeof entry.dryRun).toBe('boolean');
    }
  });

  test('a retention period must be positive', async ({ request }) => {
    const res = await request.put(
      `${API_BASE}/api/v1/admin/security/retention/policies/AUDIT_LOG`,
      { headers: ADMIN, data: { retentionDays: 0 } });
    expect(res.status()).toBe(400);
  });

  test('an unknown category cannot be updated', async ({ request }) => {
    const res = await request.put(
      `${API_BASE}/api/v1/admin/security/retention/policies/NOT_A_CATEGORY`,
      { headers: ADMIN, data: { retentionDays: 30 } });
    expect(res.status()).toBe(404);
  });

  test('retention is admin-only', async ({ request }) => {
    const policies = await request.get(`${API_BASE}/api/v1/admin/security/retention/policies`, {
      headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' }
    });
    expect(policies.status()).toBe(403);

    const run = await request.post(`${API_BASE}/api/v1/admin/security/retention/run`, {
      headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' }, data: {}
    });
    expect(run.status()).toBe(403);
  });

  test('anonymous callers cannot trigger retention', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/admin/security/retention/run`, { data: {} });
    expect(res.status()).toBe(403);
  });
});
