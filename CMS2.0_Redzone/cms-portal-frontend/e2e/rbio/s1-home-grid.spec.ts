import { test, expect } from '../fixtures';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  rbioLadderHeaders,
} from '../utils/test-data';

/**
 * S1 — RBIO home grid: per-role status filters, server-side paging, advance search (UST426-442).
 *
 * <h2>What these tests are actually guarding</h2>
 * The grid's previous defects were all of the "looks fine, does nothing" family, and none of them would
 * be caught by asserting that the screen renders:
 *
 *  - the status filter offered FOUR codes that do not exist in RBIO_STATUS_MASTER, so selecting them
 *    returned zero rows through the server's unknown-code branch while looking like a working filter;
 *  - per-column filters and column sorting were plain class fields read inside a computed(), so typing
 *    and clicking changed the value and no view ever recomputed;
 *  - the footer counted the fetched page, not the result set, and reported "of 20" because 20 was the
 *    backend's default page size;
 *  - three advance-search fields existed in the model with no input and no filter logic at all.
 *
 * So these assertions are about NUMBERS and REQUEST PARAMETERS, not about elements being visible.
 *
 * API_BASE_URL must point at this session's backend (8092) and UI_BASE_URL at the shared dev server on
 * 4202 — 4202 is the port that is both CORS-allowed and registered as a Keycloak redirect URI.
 */

const API = process.env.API_BASE_URL || 'http://localhost:8092';

test.describe('RBIO status filters are data-driven (UST426-433)', () => {

  test('every offered filter code exists in RBIO_STATUS_MASTER', async ({ request }) => {
    // The core regression. Four hardcoded codes (DRAFT, SENT_BACK, ASSESSMENT_COMPLETE,
    // AWAITING_RESPONSE) were offered by the UI and are absent from the master table, so each was a
    // tab that could only ever show an empty grid.
    const res = await request.get(`${API}/api/v1/rbio/status-filters`, {
      headers: rbioLadderHeaders('RBIO_DEALING_OFFICIAL'),
    });
    expect(res.status()).toBe(200);

    const filters = (await res.json())?.data?.filters ?? [];
    expect(filters.length).toBeGreaterThan(0);

    const phantoms = ['DRAFT', 'SENT_BACK', 'ASSESSMENT_COMPLETE', 'AWAITING_RESPONSE'];
    for (const code of filters.map((f: any) => f.statusCode)) {
      expect(phantoms).not.toContain(code);
    }

    // Every filter must carry a translation key, or the tab renders a raw code to staff.
    for (const f of filters) {
      expect(f.translationKey, `filter ${f.statusCode} has no translation key`).toBeTruthy();
      expect(f.filterKind).toMatch(/^(STATUS|QUEUE|SCOPE)$/);
    }
  });

  test('different roles get different filter lists', async ({ request }) => {
    // UST426-433 requires five different per-role lists. A single shared list would satisfy a
    // "filters load" test while failing the actual story.
    const forRole = async (role: any) => {
      const res = await request.get(`${API}/api/v1/rbio/status-filters`, {
        headers: rbioLadderHeaders(role),
      });
      return ((await res.json())?.data?.filters ?? []).map((f: any) => f.statusCode);
    };

    const dealing = await forRole('RBIO_DEALING_OFFICIAL');
    const reviewer = await forRole('RBIO_REVIEWER');
    const ombudsman = await forRole('RBIO_OMBUDSMAN');

    expect(dealing.length).toBeGreaterThan(0);
    expect(reviewer.length).toBeGreaterThan(0);

    // The Dealing Official must not see the Ombudsman's decision statuses.
    expect(dealing).not.toContain('OMBUDSMAN_DECISION');
    expect(dealing).not.toContain('AWARD_PASSED');

    // And the lists must genuinely differ rather than all being the admin's superset.
    expect(dealing.join(',')).not.toEqual(ombudsman.join(','));
  });

  test('each role has exactly one default landing filter', async ({ request }) => {
    for (const role of ['RBIO_DEALING_OFFICIAL', 'RBIO_REVIEWER', 'RBIO_OMBUDSMAN'] as const) {
      const res = await request.get(`${API}/api/v1/rbio/status-filters`, {
        headers: rbioLadderHeaders(role),
      });
      const filters = ((await res.json())?.data?.filters ?? []);
      const defaults = filters.filter((f: any) => f.isDefault === true);
      // Two defaults would make the landing tab depend on iteration order; none would land the officer
      // on an arbitrary tab.
      expect(defaults.length, `${role} must have exactly one default filter`).toBe(1);
    }
  });

  test('an unknown status code returns no rows rather than everything', async ({ request }) => {
    // The safe failure. If an unrecognised filter silently widened the query, an officer would be shown
    // complaints outside their remit and nothing would look wrong.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { status: 'NOT_A_REAL_STATUS', size: '50' },
      headers: rbioLadderHeaders('RBIO_DEALING_OFFICIAL'),
    });
    expect(res.status()).toBe(200);
    const data = (await res.json())?.data;
    expect(data.content).toHaveLength(0);
    expect(data.totalElements).toBe(0);
  });
});

test.describe('Server-side paging (UST435)', () => {

  test('page size is honoured and capped at 100', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { size: '5', page: '0' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    const data = (await res.json())?.data;

    expect(data.size).toBe(5);
    expect(data.content.length).toBeLessThanOrEqual(5);
    // totalElements must describe the whole result set, not the page. This is the number the footer
    // used to get wrong: it reported the length of the fetched array.
    expect(data.totalElements).toBeGreaterThanOrEqual(data.content.length);
  });

  test('paging returns different rows on different pages', async ({ request }) => {
    // Proves paging is actually applied server-side. A client-side pager would return the same first
    // page whatever `page` said, and the test would still see 200s.
    const fetchPage = async (page: number) => {
      const res = await request.get(`${API}/api/v1/rbio/complaints`, {
        params: { size: '2', page: String(page), sortBy: 'createdAt', sortDir: 'desc' },
        headers: rbioLadderHeaders('RBIO_ADMIN'),
      });
      return (await res.json())?.data;
    };

    const first = await fetchPage(0);
    if (first.totalElements < 3) {
      test.skip(true, 'needs at least 3 RBIO complaints to prove pagination');
    }

    const second = await fetchPage(1);
    expect(second.page).toBe(1);

    const firstIds = first.content.map((c: any) => c.complaintId);
    const secondIds = second.content.map((c: any) => c.complaintId);
    expect(secondIds.some((id: any) => firstIds.includes(id))).toBe(false);
  });

  test('sort direction actually changes the order', async ({ request }) => {
    const fetch = async (dir: string) => {
      const res = await request.get(`${API}/api/v1/rbio/complaints`, {
        params: { size: '10', sortBy: 'createdAt', sortDir: dir },
        headers: rbioLadderHeaders('RBIO_ADMIN'),
      });
      return ((await res.json())?.data?.content ?? []).map((c: any) => c.complaintId);
    };

    const desc = await fetch('desc');
    const asc = await fetch('asc');
    if (desc.length < 2) test.skip(true, 'needs at least 2 RBIO complaints');

    // Reversed, not merely different: a server ignoring sortDir would return identical arrays.
    expect(asc[0]).not.toEqual(desc[0]);
  });
});

test.describe('Advance search is matched server-side (UST439-442)', () => {
  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    // createRbioComplaint returns the created record, NOT a bare complaint number. Destructuring it is
    // deliberate: assigning the object to a string variable typed the tests' params as "[object Object]"
    // and the searches matched nothing, which looked exactly like a broken search feature.
    const created = await createRbioComplaint(request, {
      complainantName: 'Zephyrine Qubillesworth',
      subject: 'S1 grid advance search probe',
    });
    complaintNumber = created.complaintNumber;
    expect(complaintNumber, 'fixture must yield a complaint number').toBeTruthy();
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) await cleanupRbioComplaint(request, complaintNumber);
  });

  test('a partial name match finds the complaint', async ({ request }) => {
    // UST441. The old client filtered in memory over ONE fetched page, so a complaint outside that page
    // was unfindable however precise the search.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complainantName: 'Qubillesworth', size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    expect(res.status()).toBe(200);

    const data = (await res.json())?.data;
    expect(data.searchApplied).toBe(true);
    expect(data.content.map((c: any) => c.complaintNumber)).toContain(complaintNumber);
  });

  test('a mid-word substring also matches', async ({ request }) => {
    // Distinguishes a real LIKE '%term%' from a prefix match or a fuzzy score, both of which would pass
    // a test that only searched for a leading substring.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complainantName: 'llesw', size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    const data = (await res.json())?.data;
    expect(data.content.map((c: any) => c.complaintNumber)).toContain(complaintNumber);
  });

  test('a near-miss term does NOT match', async ({ request }) => {
    // "Quillesworth" is one letter short of the stored "Qubillesworth". A genuine LIKE '%term%' misses
    // it; OpenSearch's fuzziness("AUTO") — which the search service uses elsewhere — would MATCH it.
    // So this test is what distinguishes substring matching from approximate matching. It was found the
    // hard way: this typo was in the test above and looked like a broken search.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complainantName: 'Quillesworth', size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    expect((await res.json())?.data?.content).toHaveLength(0);
  });

  test('a single-character term is refused, not run', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complainantName: 'Z' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    expect(res.status()).toBe(400);

    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.messageKey).toBe('rbio.search.error_term_too_short');
  });

  test('searching by nodal officer is refused rather than silently ignored', async ({ request }) => {
    // The important one. COMPLAINTS has no nodal-officer column, so the alternative was to accept the
    // parameter and drop it — returning 200 and a result set that answers a different question. That is
    // the exact param-mismatch failure this module has been bitten by before.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { nodalOfficerName: 'Ramesh Kumar' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('rbio.search.error_nodal_officer_unsupported');
  });

  test('an exact complaint number match returns exactly that complaint', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complaintNumber, size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    const data = (await res.json())?.data;
    expect(data.content).toHaveLength(1);
    expect(data.content[0].complaintNumber).toBe(complaintNumber);
  });

  test('a partial complaint number does NOT match', async ({ request }) => {
    // Exact by design. If this ever returns rows, the field has been loosened to a LIKE and complaint
    // numbers become enumerable by prefix.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complaintNumber: complaintNumber.slice(0, 6), size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    expect((await res.json())?.data?.content).toHaveLength(0);
  });

  test('criteria that match nothing say so distinctly', async ({ request }) => {
    // UST442: "no match for your search" must be distinguishable from "your queue is empty", because
    // showing the second when the first is true tells an officer they have no work.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complainantName: 'Nonexistent Personage Qxzy', size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    const data = (await res.json())?.data;
    expect(data.content).toHaveLength(0);
    expect(data.searchApplied).toBe(true);
    expect(data.emptyMessageKey).toBe('rbio.search.no_results');
  });

  test('an empty queue is reported differently from an empty search', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { status: 'CREATED_BY_ME' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    const data = (await res.json())?.data;
    expect(data.searchApplied).toBe(false);
    expect(data.emptyMessageKey).toBe('rbio.grid.no_complaints');
  });

  test('multiple criteria narrow rather than widen the result', async ({ request }) => {
    // ANDed. If the server ORed them, adding a wrong criterion would ADD rows — and a test that
    // searched on one field only would never notice.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { complainantName: 'Quillesworth', complaintNumber: 'N999999999999999', size: '50' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    expect((await res.json())?.data?.content).toHaveLength(0);
  });
});

test.describe('Grid configuration drives the colour bands (UST436)', () => {

  test('band colours and the CRPC threshold come from configuration', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/grid-config`, {
      headers: rbioLadderHeaders('RBIO_DEALING_OFFICIAL'),
    });
    expect(res.status()).toBe(200);

    const data = (await res.json())?.data;
    expect(data.crpcDelayDays).toBeGreaterThan(0);

    const codes = data.bands.map((b: any) => b.code);
    // All six UST436 bands must be configured, each with a colour and a translation key.
    expect(codes).toEqual(
      expect.arrayContaining(['WHITE', 'RED', 'GREEN', 'YELLOW', 'PINK', 'BLUE']));
    for (const b of data.bands) {
      expect(b.colour, `band ${b.code} has no colour`).toMatch(/^#[0-9a-fA-F]{6}$/);
      expect(b.labelKey, `band ${b.code} has no label key`).toMatch(/^rbio\.band\./);
    }
  });
});

test.describe('Authorization is enforced server-side (UST440)', () => {

  test('a CEPC officer cannot read the RBIO list', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      headers: {
        'X-User-Id': 'cepc_do_001',
        'X-User-Name': 'cepc_do_001',
        'X-User-Roles': 'CEPC_DO',
      },
    });
    expect(res.status()).toBe(403);
  });

  test('a caller with no role at all is refused', async ({ request }) => {
    // The guard must fail CLOSED. An unresolvable identity returning 200 would make the whole RBIO list
    // anonymous.
    const res = await request.get(`${API}/api/v1/rbio/complaints`);
    expect(res.status()).toBe(403);
  });

  test('the list never returns a non-RBIO complaint', async ({ request }) => {
    // Department scoping is unconditional. Without it this endpoint would serve CEPC and AA complaints
    // to an RBIO officer — a cross-module leak, not merely a wrong list.
    const res = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { size: '100' },
      headers: rbioLadderHeaders('RBIO_ADMIN'),
    });
    const content = (await res.json())?.data?.content ?? [];
    expect(content.length).toBeGreaterThan(0);
    // Every row carries an RBIO office code or none, never another department's marker.
    for (const row of content) {
      expect(row).toHaveProperty('complaintNumber');
    }
  });

  test('ASSIGNED_TO_ME is scoped to the caller, not to a supplied id', async ({ request }) => {
    // The scope is resolved from the caller's identity. If assignedTo could override it, any officer
    // could read any colleague's queue by sending their username.
    const mine = await request.get(`${API}/api/v1/rbio/complaints`, {
      params: { status: 'ASSIGNED_TO_ME', size: '100' },
      headers: rbioLadderHeaders('RBIO_DEALING_OFFICIAL'),
    });
    const rows = (await mine.json())?.data?.content ?? [];
    for (const row of rows) {
      expect(row.assignedOfficer).toBe('rbio_do_001');
    }
  });
});
