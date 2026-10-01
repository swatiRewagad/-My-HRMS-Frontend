import { test, expect } from '../fixtures';

/**
 * S1 — the delegated-UAM authority contract.
 *
 * <h2>Why this suite exists</h2>
 * User administration is delegated to the SSO: users (both RBI staff and Regulated Entity users) and the
 * user→role→authority mapping live there, and this application only DECLARES and ENFORCES authorities at
 * the REST API level. That makes two things testable end to end, and both are invisible from the UI:
 *
 * <ol>
 *   <li>the published catalogue, which is the contract the SSO administrator maps against — if an
 *       authority is renamed here and nobody remaps it, every holder is silently refused;</li>
 *   <li>the enforcement itself, which must hold even under the {@code dev-local} profile where the filter
 *       chain is {@code anyRequest().permitAll()}. That last point is the important one: a filter-chain
 *       fix CANNOT be proven by an E2E test against dev-local, so if the authority aspect were the only
 *       control and it were absent, these endpoints would be wide open here and nothing would show it.</li>
 * </ol>
 */

const API = process.env.API_BASE_URL || 'http://localhost:8092';

/** Dev-profile headers naming authorities explicitly. Honoured only when dev identity headers are on. */
function withAuthorities(user: string, authorities: string[], entityCode?: string) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-User-Id': user,
    'X-User-Name': user,
    'X-User-Authorities': authorities.join(','),
  };
  if (entityCode) headers['X-Entity-Code'] = entityCode;
  return headers;
}

test.describe('The authority catalogue is the SSO contract', () => {

  test('every authority is published with a description and a principal type', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/authorities/catalogue`);
    expect(res.status()).toBe(200);

    const data = (await res.json())?.data;
    expect(data.count).toBeGreaterThan(0);
    expect(data.authorities).toHaveLength(data.count);

    for (const a of data.authorities) {
      expect(a.authority, 'an authority with no name cannot be mapped').toBeTruthy();
      // A blank description leaves the administrator guessing what they are granting.
      expect(a.description, `${a.authority} has no description`).toBeTruthy();
      expect(['RBI', 'REGULATED_ENTITY']).toContain(a.principalType);
      // ROLE_ is Spring's convention for roles. These are deliberately not roles, and a prefixed name
      // would invite mapping it as a realm role and wondering why nothing was granted.
      expect(a.authority).not.toMatch(/^ROLE_/);
    }
  });

  test('the claim names the SSO must populate are published', async ({ request }) => {
    // Without these an administrator has to guess which claim to write the authorities into, and a wrong
    // guess produces a token that authenticates perfectly and authorises nothing.
    const data = (await (await request.get(`${API}/api/v1/authorities/catalogue`)).json())?.data;
    expect(data.expectedClaim).toBe('authorities');
    expect(data.entityScopeClaim).toBe('entity_code');
  });

  test('both user types are represented', async ({ request }) => {
    // RBI staff and Regulated Entity users arrive through the SAME SSO, so the catalogue has to tell an
    // administrator which authorities belong to which population.
    const data = (await (await request.get(`${API}/api/v1/authorities/catalogue`)).json())?.data;
    const types = new Set(data.authorities.map((a: any) => a.principalType));
    expect(types).toContain('RBI');
    expect(types).toContain('REGULATED_ENTITY');
  });

  test('a caller can read what they hold, including their entity scope', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/authorities/mine`,
      { headers: withAuthorities('re_nodal_001', ['RE_COMPLAINT_READ'], 'HDFC') });
    const data = (await res.json())?.data;

    // entity_code is what makes a caller a Regulated Entity principal AND is their data scope.
    expect(data.principalType).toBe('REGULATED_ENTITY');
    expect(data.entityCode).toBe('HDFC');
    expect(data.authorities).toContain('RE_COMPLAINT_READ');
  });

  test('an RBI staff caller has no entity scope', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/authorities/mine`,
      { headers: withAuthorities('rbio_admin_001', ['MASTER_DATA_WRITE']) });
    const data = (await res.json())?.data;

    expect(data.principalType).toBe('RBI');
    // Null, not blank-string. An RE query scoped to "" would match nothing; scoped to null the server
    // knows there is no entity constraint to apply.
    expect(data.entityCode).toBeNull();
  });

  test('an authority this build does not declare is dropped, not carried', async ({ request }) => {
    // A stale SSO mapping naming a removed authority must not appear to work. Dropping it means the
    // holder is refused, which is visible; carrying it forward would let the mapping look correct.
    const res = await request.get(`${API}/api/v1/authorities/mine`,
      { headers: withAuthorities('rbio_admin_001', ['MASTER_DATA_WRITE', 'NOT_A_REAL_AUTHORITY']) });
    const data = (await res.json())?.data;

    expect(data.authorities).toContain('MASTER_DATA_WRITE');
    expect(data.authorities).not.toContain('NOT_A_REAL_AUTHORITY');
  });
});

test.describe('Master data writes are enforced by authority, not only by the filter chain', () => {

  test('reads stay anonymous so the citizen wizard keeps working', async ({ request }) => {
    // If this ever turns 401/403, the fix has overreached and a complainant cannot file.
    const res = await request.get(`${API}/api/v1/masters/categories`);
    expect(res.status()).toBe(200);
  });

  test('an anonymous write is refused EVEN under dev-local', async ({ request }) => {
    // The whole reason the authority aspect exists as a second control. Under dev-local the filter chain
    // is anyRequest().permitAll(), so before this the same call succeeded and rewrote CATEGORY_MASTER —
    // a table that decides which office a complaint reaches and whether it is maintainable.
    const res = await request.post(`${API}/api/v1/masters/categories`, {
      data: { categoryName: 'anonymous probe' },
    });
    expect(res.status()).toBe(403);
  });

  test('a signed-in caller without the authority is still refused', async ({ request }) => {
    // Being authenticated is not a permission. A dealing official must not be able to re-route every
    // future complaint.
    const res = await request.post(`${API}/api/v1/masters/categories`, {
      headers: withAuthorities('rbio_do_001', ['RBIO_COMPLAINT_READ']),
      data: { categoryName: 'wrong authority probe' },
    });
    expect(res.status()).toBe(403);
  });

  test('a holder of MASTER_DATA_WRITE succeeds, then the probe is removed', async ({ request }) => {
    const created = await request.post(`${API}/api/v1/masters/categories`, {
      headers: withAuthorities('rbio_admin_001', ['MASTER_DATA_WRITE']),
      data: { categoryName: 'S1 authority e2e probe', schemeVersion: 'RBIOS_2021' },
    });
    expect(created.status()).toBe(200);
    const id = (await created.json())?.id;
    expect(id).toBeTruthy();

    // Soft-deleted through the API rather than left behind: CATEGORY_MASTER feeds the citizen filing
    // wizard, so a stray test row would appear in a real complainant's category list.
    const deleted = await request.delete(`${API}/api/v1/masters/categories/${id}`, {
      headers: withAuthorities('rbio_admin_001', ['MASTER_DATA_WRITE']),
    });
    expect(deleted.status()).toBe(204);
  });

  test('MASTER_DATA_READ does not substitute for MASTER_DATA_WRITE', async ({ request }) => {
    // Guards against any "close enough" matching. Similar names must not satisfy each other.
    const res = await request.post(`${API}/api/v1/masters/categories`, {
      headers: withAuthorities('rbio_admin_001', ['MASTER_DATA_READ']),
      data: { categoryName: 'read-only probe' },
    });
    expect(res.status()).toBe(403);
  });
});

test.describe('Closure authority replaces the in-application eligibility flag (UST629-631)', () => {

  test('the grid reports no closure capability without the authority', async ({ request }) => {
    // UST629: a new Reviewer defaults to "No". Under delegated UAM that is satisfied by the SSO simply
    // not granting RBIO_COMPLAINT_CLOSE_FINAL — nobody has to remember to set a flag, and there is no
    // second permission store to drift out of step with the SSO.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      headers: {
        'X-User-Id': 'rbio_reviewer_001',
        'X-User-Roles': 'RBIO_REVIEWER',
        'X-User-Authorities': 'RBIO_COMPLAINT_READ',
      },
      params: { size: '1' },
    });
    expect(res.status()).toBe(200);
    expect((await res.json())?.data?.canCloseFinal).toBe(false);
  });

  test('the grid reports closure capability when the SSO grants it', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      headers: {
        'X-User-Id': 'rbio_reviewer_001',
        'X-User-Roles': 'RBIO_REVIEWER',
        'X-User-Authorities': 'RBIO_COMPLAINT_READ,RBIO_COMPLAINT_CLOSE_FINAL',
      },
      params: { size: '1' },
    });
    expect((await res.json())?.data?.canCloseFinal).toBe(true);
  });
});
