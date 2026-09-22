import { test, expect } from '@playwright/test';

/**
 * UST875 — PII masking, reveal, and reveal logging.
 *
 * API-level rather than browser-driven: the realm has no RE users, and these assertions are about
 * what crosses the wire. A client-side mask still ships the real value in the response body, so
 * checking rendered text would not prove the control exists.
 *
 * Mutation-verified: setting cms.security.pii.masking_enabled=false in SYSTEM_CONFIG makes the
 * masking assertions below fail, confirming they are not vacuous.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8093';

/** Any complaint number with complainant data; discovered rather than hardcoded. */
async function anyComplaintNumber(request: any): Promise<string | null> {
  const res = await request.get(`${API_BASE}/api/v1/complaints/recent?limit=1`);
  if (!res.ok()) return null;
  const body = await res.json();
  return body?.data?.[0]?.complaintNumber ?? null;
}

const MASKED_NAME = /^.\*+$/;
const MASKED_PHONE = /^\*+\d{4}$/;

test.describe('UST875 — complainant PII is masked by default', () => {

  test('anonymous tracker gets masked name and phone', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.get(`${API_BASE}/api/v1/complaints/${cn}`);
    expect(res.ok()).toBeTruthy();
    const d = (await res.json()).data;

    expect(d.piiMasked).toBe(true);
    if (d.complainantName) expect(d.complainantName).toMatch(MASKED_NAME);
    if (d.complainantPhone) expect(d.complainantPhone).toMatch(MASKED_PHONE);
    if (d.complainantEmail) expect(d.complainantEmail).toContain('***');
  });

  /**
   * Regression guard for the original defect: PII used to unlock on the mere presence of an
   * Authorization header, so any non-empty string was enough.
   */
  test('a garbage Authorization header does not unmask PII', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.get(`${API_BASE}/api/v1/complaints/${cn}`, {
      headers: { Authorization: 'Bearer totally-invalid-token' }
    });
    const d = (await res.json()).data;

    expect(d.piiMasked).toBe(true);
    if (d.complainantName) expect(d.complainantName).toMatch(MASKED_NAME);
  });

  test('the legacy raw-entity tracker is masked too', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    // This endpoint serialised the JPA entity directly, including accountNumber.
    const res = await request.get(`${API_BASE}/api/complaints/track/${cn}`);
    expect(res.ok()).toBeTruthy();
    const d = await res.json();

    expect(d.piiMasked).toBe(true);
    if (d.complainantName) expect(d.complainantName).toMatch(MASKED_NAME);
    if (d.accountNumber) expect(d.accountNumber).toMatch(/^\*+\d{0,4}$/);
  });

  test('the public recent list does not leak real names in bulk', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/complaints/recent?limit=10`);
    expect(res.ok()).toBeTruthy();

    for (const item of (await res.json()).data ?? []) {
      if (item.complainantName) {
        expect(item.complainantName).toMatch(MASKED_NAME);
      }
    }
  });

  test('staff see masked values by default, but may reveal', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.get(`${API_BASE}/api/v1/complaints/${cn}`, {
      headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' }
    });
    const d = (await res.json()).data;

    expect(d.piiMasked).toBe(true);
    expect(d.canRevealPii).toBe(true);
  });
});

test.describe('UST875 — reveal is gated and recorded', () => {

  test('reveal without a justification is rejected', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
      headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' },
      data: {}
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).error).toBe('JUSTIFICATION_REQUIRED');
  });

  test('a too-short justification is rejected (boundary)', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
      headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' },
      data: { justification: 'short' }
    });
    expect(res.status()).toBe(400);
  });

  test('an unauthenticated caller cannot reveal', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
      data: { justification: 'a perfectly reasonable stated reason' }
    });
    expect(res.status()).toBe(401);
  });

  /** RE staff work for the bank being complained about, so they never receive complainant PII. */
  test('an RE role cannot reveal complainant PII', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
      headers: {
        'X-User-Id': 're.nodal.test',
        'X-User-Roles': 'RE_NODAL_OFFICER',
        'X-Entity-Code': 'HDFC'
      },
      data: { justification: 'wanting to look at the complainant details' }
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).error).toBe('REVEAL_NOT_PERMITTED');
  });

  test('a role without reveal permission is refused', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
      headers: { 'X-User-Id': 'helpdesk1', 'X-User-Roles': 'TOLL_FREE_HELPDESK' },
      data: { justification: 'curious about who filed this one' }
    });
    expect(res.status()).toBe(403);
  });

  test('an authorised reveal returns unmasked values and is auditable', async ({ request }) => {
    const cn = await anyComplaintNumber(request);
    test.skip(!cn, 'no complaint available in this environment');

    const res = await request.post(`${API_BASE}/api/v1/complaints/${cn}/reveal-pii`, {
      headers: {
        'X-User-Id': 'cepc_do1',
        'X-User-Name': 'DO One',
        'X-User-Roles': 'CEPC_DO'
      },
      data: { justification: 'Confirming contact details before calling the complainant' }
    });
    expect(res.ok()).toBeTruthy();

    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('complainantName');

    // Revealed values must not still be masked.
    if (body.data.complainantName) {
      expect(body.data.complainantName).not.toMatch(MASKED_NAME);
    }

    // The reveal must be visible in the admin trail; that record is the point of the story.
    const audit = await request.get(
      `${API_BASE}/api/v1/admin/security/pii-reveals?userId=cepc_do1&size=5`,
      { headers: { 'X-User-Id': 'cms.admin', 'X-User-Roles': 'ADMIN' } });
    expect(audit.ok()).toBeTruthy();

    const entries = (await audit.json()).data ?? [];
    expect(entries.length).toBeGreaterThan(0);
    expect(entries[0].userId).toBe('cepc_do1');
    expect(entries[0].justification).toContain('Confirming contact details');
  });
});

test.describe('UST875 — the reveal trail is admin-only', () => {

  test('a non-admin cannot read the reveal trail', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/admin/security/pii-reveals`, {
      headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' }
    });
    expect(res.status()).toBe(403);
  });

  test('an anonymous caller cannot read the reveal trail', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/admin/security/pii-reveals`);
    expect(res.status()).toBe(403);
  });
});
