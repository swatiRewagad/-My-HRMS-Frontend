import { test, expect, APIRequestContext } from '@playwright/test';

/**
 * S7 — report filter operators are real, and report access fails closed.
 *
 * Two defect classes are covered here, both server-side, so this is an API spec rather than a browser
 * one. The UI cannot demonstrate either: the operators were compiled wrongly on the server while the
 * client looked correct, and the access check was bypassable precisely by not being a browser.
 *
 * WHAT WAS WRONG
 *
 *  1. OPERATORS WERE COSMETIC. QueryCompiler matched exactly one operator string, "RANGE", and sent
 *     every other value into an else that became cb.equal. BETWEEN arrived as the literal pipe-joined
 *     string "from|to" and was compared as a string, so it could never match a row; IN was compared as
 *     one comma-joined string, likewise; GREATER_THAN and LESS_THAN silently became equality, which is
 *     the dangerous case because those DO return rows, just the wrong ones.
 *
 *  2. THE SCOPE FAILED OPEN. The caller's role and department came from X-User-Role (defaulting to
 *     "SENIOR") and X-User-Department (defaulting to ""), and buildAuthScope granted unrestricted
 *     access for either. A request with NO headers read every complaint in the database.
 *
 * WHY THESE ASSERTIONS ARE SHAPED THIS WAY
 * A refusal is asserted as a 4xx WITH an explanatory body, following the e2e/admin/timeline-config.ts
 * precedent. Asserting only the status would pass if the endpoint 400'd for an unrelated reason — and
 * the thing being verified is that the SERVER understood the operator, not merely that it complained.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const REPORTS = `${API_BASE}/api/v1/reports`;

/**
 * An identity with report access. Under dev-local the backend honours X-User-* headers, so this is how
 * a test presents a role — but note the point of the change: the JWT is authoritative, and these
 * headers are consulted ONLY because allow-dev-identity-headers is true in this profile.
 */
function adminHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': 'admin_001',
    'X-User-Name': 'admin_001',
    'X-User-Roles': 'ADMIN'
  };
}

async function execute(request: APIRequestContext, filters: any[], headers = adminHeaders()) {
  return request.post(`${REPORTS}/execute`, {
    headers,
    data: { subjectId: 'list', sentence: 'all complaints', filters },
    failOnStatusCode: false
  });
}

test.describe('S7 — report filter operators (UST616-619, 671-672)', () => {

  test('the semantic model publishes the operators the SERVER compiles', async ({ request }) => {
    const res = await request.get(`${REPORTS}/semantic-model`, { headers: adminHeaders() });
    expect(res.ok(), 'the semantic model must load').toBeTruthy();
    const body = await res.json();

    // Previously the client invented its own six-operator catalogue with no server counterpart at all,
    // which is how the two came to disagree silently.
    expect(body.operators, 'the server must publish its operator list').toContain('BETWEEN');
    expect(body.operators).toContain('IN');
    expect(body.operators).toContain('LIKE');
    expect(body.operators).toContain('GREATER_THAN');
    expect(body.operators).toContain('LESS_THAN');
    // The bounds the server ENFORCES, so the client stops keeping its own copy.
    expect(body.maxDateRangeDays, 'the range cap must come from the server').toBe(365);
    expect(body.minDateRangeDays).toBe(1);
    expect(body.maxInValues).toBe(100);
  });

  test('BETWEEN is parsed, not string-compared', async ({ request }) => {
    const res = await execute(request, [
      { field: 'filedDate', operator: 'BETWEEN', value: '2020-01-01|2020-12-31' }
    ]);

    // The assertion is that the server UNDERSTOOD the pipe format. Before the fix this reached
    // cb.equal(createdAt, "2020-01-01|2020-12-31") — comparing a timestamp column to that literal
    // string, which cannot match and (depending on the dialect) can fail at bind time.
    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('results');
    expect(Array.isArray(body.results)).toBeTruthy();
  });

  test('a sub-one-day range is REFUSED by the server, not just by the browser', async ({ request }) => {
    const res = await execute(request, [
      { field: 'filedDate', operator: 'BETWEEN', value: '2026-01-01|2026-01-01' }
    ]);

    expect(res.status(), 'the server must enforce the minimum, since a client cap is not a control')
      .toBe(400);
    const text = await res.text();
    expect(text, 'the refusal must explain itself').toMatch(/minimum is 1/i);
  });

  test('a reversed range is refused rather than silently swapped', async ({ request }) => {
    // The client used Math.abs, so to < from passed validation there and a year of unrequested data
    // would have come back had the server merely reordered the bounds.
    const res = await execute(request, [
      { field: 'filedDate', operator: 'BETWEEN', value: '2026-06-01|2026-01-01' }
    ]);

    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/after its To date/i);
  });

  test('a range over one year is auto-capped AND the response says so', async ({ request }) => {
    // 2021 is deliberately the From year rather than 2020: the cap is max_date_range_days (365) added to
    // the From date, and 2020 is a leap year, so a 2020 start caps to 2020-12-31 — 365 days later but one
    // day short of the anniversary. Asserting "2021-01-01" from a 2020 start would have been asserting a
    // calendar quirk rather than the rule. (The client's own setFullYear(+1) has the mirror-image bug: it
    // produces 366 days across a leap year, exceeding the very cap it is enforcing.)
    const res = await execute(request, [
      { field: 'filedDate', operator: 'BETWEEN', value: '2021-01-01|2026-01-01' }
    ]);

    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    // The notice matters as much as the cap: a silently truncated range reads as a complete answer to
    // the question the user actually asked.
    expect(body.notices, 'an auto-capped range must be disclosed').toBeTruthy();
    expect(JSON.stringify(body.notices)).toMatch(/capped to 2022-01-01/i);
  });

  test('IN beyond the configured maximum is refused, not silently truncated', async ({ request }) => {
    const tooMany = Array.from({ length: 101 }, (_, i) => `v${i}`).join(',');
    const res = await execute(request, [
      { field: 'status', operator: 'IN', value: tooMany }
    ]);

    expect(res.status()).toBe(400);
    // Truncating server-side would return a confidently incomplete report with no indication that
    // values were dropped.
    expect(await res.text()).toMatch(/maximum is 100/i);
  });

  test('IN at exactly the maximum is accepted (boundary)', async ({ request }) => {
    const exactly100 = Array.from({ length: 100 }, (_, i) => `v${i}`).join(',');
    const res = await execute(request, [
      { field: 'status', operator: 'IN', value: exactly100 }
    ]);

    expect(res.status(), await res.text()).toBe(200);
  });

  test('a multi-parameter LIKE is refused (UST618: exactly one)', async ({ request }) => {
    const res = await execute(request, [
      { field: 'reName', operator: 'LIKE', value: 'HDFC,ICICI' }
    ]);

    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/exactly one value/i);
  });

  test('an unknown operator is refused instead of defaulting to equality', async ({ request }) => {
    const res = await execute(request, [
      { field: 'status', operator: 'NOT_AN_OPERATOR', value: 'closed' }
    ]);

    // Defaulting is what made the original defect invisible: a typo, a null, and an operator the server
    // had not implemented all produced plausible output.
    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/Unsupported filter operator/i);
  });

  test('an empty filter value is refused, because dropping it would WIDEN the report', async ({ request }) => {
    const res = await execute(request, [
      { field: 'status', operator: 'EQUAL', value: '' }
    ]);

    // The old code logged and dropped an unusable filter. Removing a predicate returns MORE rows than
    // were asked for, which for a PII-bearing export is the worst possible failure mode.
    expect(res.status()).toBe(400);
  });

  test('GREATER_THAN and LESS_THAN are accepted and compiled', async ({ request }) => {
    for (const operator of ['GREATER_THAN', 'LESS_THAN']) {
      const res = await execute(request, [
        { field: 'filedDate', operator, value: '2020-01-01' }
      ]);
      expect(res.status(), `${operator} must compile: ${await res.text()}`).toBe(200);
    }
  });

  test('a named relative range still works, because saved widgets hold them', async ({ request }) => {
    // SemanticModelRegistry pins RANGE on its ten time filters, and persisted dashboard widgets carry
    // it in their stored query JSON. Dropping support would break every saved report in the database.
    const res = await execute(request, [
      { field: 'filedDate', operator: 'RANGE', value: 'LAST_30D' }
    ]);

    expect(res.status(), await res.text()).toBe(200);
  });

  test('an unknown named range is refused rather than dropped', async ({ request }) => {
    const res = await execute(request, [
      { field: 'filedDate', operator: 'RANGE', value: 'LAST_FORTNIGHT' }
    ]);

    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/Unknown named date range/i);
  });
});

test.describe('S7 — report access fails closed (UST615, 669, 670)', () => {

  test('a request with NO identity headers is REFUSED, not served everything', async ({ request }) => {
    const res = await request.post(`${REPORTS}/execute`, {
      headers: { 'Content-Type': 'application/json' },
      data: { subjectId: 'list', sentence: 'all complaints', filters: [] },
      failOnStatusCode: false
    });

    // THE headline security assertion. Before the fix, X-User-Role defaulted to "SENIOR" and
    // X-User-Department to "", and buildAuthScope granted unrestricted access for either — so this exact
    // request returned up to 5000 complainants' names, addresses and grievances.
    expect(res.status(), 'an unidentified caller must not receive report data').not.toBe(200);
    expect([401, 403]).toContain(res.status());
  });

  test('the forged SENIOR role no longer grants anything', async ({ request }) => {
    const res = await request.post(`${REPORTS}/execute`, {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'attacker',
        'X-User-Roles': 'SENIOR'
      },
      data: { subjectId: 'list', sentence: 'all complaints', filters: [] },
      failOnStatusCode: false
    });

    // "SENIOR" was never a real Keycloak role — it was reachable only as a header default, which is
    // exactly what made the old check trivially bypassable. It must now grant nothing.
    expect(res.status(), 'SENIOR is not a real role and must grant no access').not.toBe(200);
  });

  test('a role with no REPORT_ACCESS_ROLE row is refused with an explanation', async ({ request }) => {
    const res = await request.post(`${REPORTS}/execute`, {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'nobody_001',
        'X-User-Roles': 'RE_NODAL_OFFICER'
      },
      data: { subjectId: 'list', sentence: 'all complaints', filters: [] },
      failOnStatusCode: false
    });

    expect(res.status()).toBe(403);
    const body = await res.json();
    // A translation key, so the refusal renders in the officer's own language.
    expect(body.messageKey).toBe('reports.error_access_denied');
  });

  test('a seeded admin role CAN run a report (the fix is not simply deny-all)', async ({ request }) => {
    const res = await execute(request, []);

    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('results');
    expect(body).toHaveProperty('canExport');
  });

  test('export is refused for a view-only role, server-side', async ({ request }) => {
    // CEPD_ADMIN is seeded with CAN_EXPORT='N'. Export used to be "enforced" only by hiding a button
    // for two hardcoded role names, so anyone able to POST /execute obtained the same rows anyway.
    const res = await request.post(`${REPORTS}/export`, {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'cepd_admin_001',
        'X-User-Roles': 'CEPD_ADMIN'
      },
      data: { subjectId: 'list', sentence: 'all complaints', filters: [] },
      failOnStatusCode: false
    });

    expect(res.status(), 'a view-only role must not be able to export').toBe(403);
    expect(await res.text()).toMatch(/not export/i);
  });

  test('the access list is not writable by an ordinary staff role', async ({ request }) => {
    // Without the dedicated SecurityConfig matcher this would sit under the broad /api/v1/reports/**
    // STAFF_ROLES grant, letting any of ~22 staff roles rewrite the table that governs report access —
    // including granting themselves export.
    const res = await request.post(`${REPORTS}/access-roles`, {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'rbio_do_001',
        'X-User-Roles': 'RBIO_OFFICER'
      },
      data: [{ id: 0, reportType: 'ALL', roleName: 'RBIO_OFFICER', canExport: true }],
      failOnStatusCode: false
    });

    expect([401, 403]).toContain(res.status());
  });

  test('the access list endpoint exists and returns the seeded rows', async ({ request }) => {
    // It previously 404'd, and the client swallowed that — which is why an empty list came to mean
    // "everyone may export".
    const res = await request.get(`${REPORTS}/access-roles`, { headers: adminHeaders() });

    expect(res.status(), await res.text()).toBe(200);
    const rows = await res.json();
    expect(Array.isArray(rows)).toBeTruthy();
    expect(rows.length, 'V98 seeds the UST615/669 role list').toBeGreaterThan(0);

    const cepd = rows.find((r: any) => r.roleName === 'CEPD_ADMIN');
    expect(cepd, 'CEPD_ADMIN must be listed').toBeTruthy();
    expect(cepd.canExport, 'UST669 makes CEPD Admin view-only').toBe(false);

    const secretariat = rows.find((r: any) => r.roleName === 'AA_SECRETARIAT');
    expect(secretariat, 'AA Secretariat is a DISTINCT actor from AA Admin (UST669)').toBeTruthy();
    expect(secretariat.canExport).toBe(true);
  });

  test('the access list cannot be emptied by a bulk save', async ({ request }) => {
    // The client sends the WHOLE list on every change, so a stale client working from an empty state
    // could otherwise remove report access from every role at once.
    const res = await request.post(`${REPORTS}/access-roles`, {
      headers: adminHeaders(),
      data: [],
      failOnStatusCode: false
    });

    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/cannot be emptied/i);
  });
});

test.describe('S7 — drill-down scoping (UST620-623)', () => {

  test('the drill-down endpoint exists', async ({ request }) => {
    // The popup UI was complete and the endpoint did not exist, so the modal always errored.
    const res = await request.get(`${REPORTS}/drill-down/no-records`, {
      headers: adminHeaders(),
      params: { complaintId: 'DOES-NOT-EXIST' },
      failOnStatusCode: false
    });

    expect(res.status(), 'the endpoint must exist rather than 404').toBe(200);
    expect(Array.isArray(await res.json()), 'an unknown complaint yields an empty list').toBeTruthy();
  });

  test('the drill-down refuses an unidentified caller', async ({ request }) => {
    // UST622: the drill-down must be unreachable by any path that skips the parent report's rules. It
    // takes a complaint number directly, so it is trivially enumerable if unscoped.
    const res = await request.get(`${REPORTS}/drill-down/no-records`, {
      params: { complaintId: 'CMP-ANY' },
      failOnStatusCode: false
    });

    expect(res.status()).not.toBe(200);
  });
});
