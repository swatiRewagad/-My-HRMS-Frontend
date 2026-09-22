import { test, expect } from '@playwright/test';

/**
 * B1 (authorization enforcement), UST873 (anomaly detection), UST877 (credential revocation).
 *
 * These run against the dev-local profile, which deliberately accepts X-User-* identity headers with
 * no JWT so local work is possible. That means the assertions here prove ROLE checks and the
 * revocation list, not JWT signature validation — the enforcing chain is unit-tested separately in
 * SecurityConfigEnforcementTest, which runs without the dev-local profile.
 *
 * Mutation-verified: raising cms.security.anomaly.cross_entity_threshold to 99 makes the alert
 * assertion fail, and setting CREDENTIAL_REVOCATION.ACTIVE=0 makes the revocation assertion fail.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8093';

const ADMIN = { 'X-User-Id': 'cms.admin', 'X-User-Roles': 'ADMIN' };

test.describe('B1 — the admin surface is role-gated server-side', () => {

  const adminPaths = [
    '/api/v1/admin/security/alerts',
    '/api/v1/admin/security/events',
    '/api/v1/admin/security/pii-reveals',
    '/api/v1/admin/security/revocations',
    '/api/v1/admin/security/retention/policies',
    '/api/v1/admin/security/config'
  ];

  for (const path of adminPaths) {
    test(`anonymous is refused ${path}`, async ({ request }) => {
      const res = await request.get(`${API_BASE}${path}`);
      expect(res.status()).toBe(403);
    });

    test(`a non-admin staff role is refused ${path}`, async ({ request }) => {
      const res = await request.get(`${API_BASE}${path}`, {
        headers: { 'X-User-Id': 'rbio.officer', 'X-User-Roles': 'RBIO_OFFICER' }
      });
      expect(res.status()).toBe(403);
    });
  }

  test('an ADMIN reaches the console', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/admin/security/alerts`, { headers: ADMIN });
    expect(res.ok()).toBeTruthy();
    expect((await res.json()).success).toBe(true);
  });

  /**
   * The realm has no SECURITY_ADMIN role, so ADMIN is the gate. If a SECURITY_ADMIN role is added
   * later this test documents that it grants nothing until the config is updated.
   */
  test('an invented SECURITY_ADMIN role grants nothing', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/admin/security/alerts`, {
      headers: { 'X-User-Id': 'someone', 'X-User-Roles': 'SECURITY_ADMIN' }
    });
    expect(res.status()).toBe(403);
  });
});

test.describe('UST873 — repeated denials raise a counted alert', () => {

  test('unauthorised reveal attempts cross the threshold and raise one alert', async ({ request }) => {
    const recent = await request.get(`${API_BASE}/api/v1/complaints/recent?limit=1`);
    const cn = (await recent.json())?.data?.[0]?.complaintNumber;
    test.skip(!cn, 'no complaint available in this environment');

    // A unique subject per run so a previous run's suppression window cannot mask the result.
    const subject = `e2e.prober.${Date.now()}`;

    const thresholdRes = await request.get(`${API_BASE}/api/v1/admin/security/config`, { headers: ADMIN });
    const configs = (await thresholdRes.json()).data ?? [];
    const thresholdCfg = configs.find(
      (c: any) => c.configKey === 'cms.security.anomaly.cross_entity_threshold');
    const threshold = thresholdCfg ? Number(thresholdCfg.configValue) : 3;

    for (let i = 0; i < threshold + 1; i++) {
      const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
        headers: { 'X-User-Id': subject, 'X-User-Roles': 'TOLL_FREE_HELPDESK' },
        data: { justification: 'probing around the complainant record' }
      });
      expect(res.status()).toBe(403);
    }

    const alerts = await request.get(
      `${API_BASE}/api/v1/admin/security/alerts?status=OPEN&size=100`, { headers: ADMIN });
    const raised = ((await alerts.json()).data ?? []).filter((a: any) => a.subject === subject);

    expect(raised.length).toBe(1);
    expect(raised[0].alertType).toBe('UNAUTHORISED_PII_REVEAL_ATTEMPTS');
    expect(raised[0].severity).toBe('HIGH');
    expect(raised[0].eventCount).toBeGreaterThanOrEqual(threshold);

    // Suppression: a sustained attack must not flood the console with one alert per request.
    const events = await request.get(
      `${API_BASE}/api/v1/admin/security/events?subject=${subject}&size=100`, { headers: ADMIN });
    const eventCount = ((await events.json()).data ?? []).length;
    expect(eventCount).toBeGreaterThan(raised.length);
  });

  test('an alert can be acknowledged, and acknowledgement is attributed', async ({ request }) => {
    const open = await request.get(
      `${API_BASE}/api/v1/admin/security/alerts?status=OPEN&size=1`, { headers: ADMIN });
    const alert = ((await open.json()).data ?? [])[0];
    test.skip(!alert, 'no open alert to acknowledge');

    const ack = await request.post(
      `${API_BASE}/api/v1/admin/security/alerts/${alert.id}/acknowledge`,
      { headers: ADMIN, data: { note: 'Reviewed during E2E verification' } });
    expect(ack.ok()).toBeTruthy();

    const after = await request.get(
      `${API_BASE}/api/v1/admin/security/alerts?status=ACKNOWLEDGED&size=100`, { headers: ADMIN });
    const found = ((await after.json()).data ?? []).find((a: any) => a.id === alert.id);
    expect(found).toBeTruthy();
    expect(found.acknowledgedBy).toBe('cms.admin');
  });

  test('thresholds are tunable without a code change', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/admin/security/config`, { headers: ADMIN });
    const keys = ((await res.json()).data ?? []).map((c: any) => c.configKey);

    expect(keys).toContain('cms.security.anomaly.window_minutes');
    expect(keys).toContain('cms.security.anomaly.cross_entity_threshold');
    expect(keys).toContain('cms.security.anomaly.denied_access_threshold');
    expect(keys).toContain('cms.security.pii.masked_fields');
  });

  test('config edits are confined to the cms.security namespace', async ({ request }) => {
    const res = await request.put(
      `${API_BASE}/api/v1/admin/security/config/timeline.something_unrelated`,
      { headers: ADMIN, data: { value: '1' } });
    expect(res.status()).toBe(400);
  });
});

test.describe('UST877 — revocation blocks a still-valid session', () => {

  test('a revoked user is refused, and restoring lets them back in', async ({ request }) => {
    const username = `e2e.revoke.${Date.now()}`;

    const revoke = await request.post(`${API_BASE}/api/v1/admin/security/revocations`, {
      headers: ADMIN,
      data: { username, reason: 'OFFBOARDING', notes: 'E2E verification' }
    });
    // 207 when Keycloak could not be reached: the local block still applies, which is what is
    // asserted below.
    expect([200, 207]).toContain(revoke.status());

    // Wait out the revocation-list cache TTL.
    await new Promise(resolve => setTimeout(resolve, 12000));

    const blocked = await request.get(`${API_BASE}/api/v1/workflow/rbio/tasks`, {
      headers: { 'X-User-Id': username, 'X-User-Roles': 'RBIO_OFFICER' }
    });
    expect(blocked.status()).toBe(401);
    expect((await blocked.json()).error).toBe('CREDENTIALS_REVOKED');

    const restore = await request.delete(
      `${API_BASE}/api/v1/admin/security/revocations/${username}`, { headers: ADMIN });
    expect(restore.ok()).toBeTruthy();

    await new Promise(resolve => setTimeout(resolve, 12000));

    const allowed = await request.get(`${API_BASE}/api/v1/workflow/rbio/tasks`, {
      headers: { 'X-User-Id': username, 'X-User-Roles': 'RBIO_OFFICER' }
    });
    expect(allowed.status()).not.toBe(401);
  });

  test('an administrator cannot revoke their own access', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/admin/security/revocations`, {
      headers: ADMIN,
      data: { username: 'cms.admin', reason: 'ADMIN_ACTION' }
    });
    expect(res.status()).toBe(400);
  });

  test('a revocation request needs a username', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/admin/security/revocations`, {
      headers: ADMIN,
      data: { reason: 'OFFBOARDING' }
    });
    expect(res.status()).toBe(400);
  });

  test('a non-admin cannot revoke anyone', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/admin/security/revocations`, {
      headers: { 'X-User-Id': 'rbio.officer', 'X-User-Roles': 'RBIO_OFFICER' },
      data: { username: 'someone.else', reason: 'OFFBOARDING' }
    });
    expect(res.status()).toBe(403);
  });
});
