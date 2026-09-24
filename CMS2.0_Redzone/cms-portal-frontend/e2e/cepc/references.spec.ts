import { test, expect } from '../fixtures';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  advanceToStatus,
  cleanupComplaint,
  identityHeadersFor,
} from '../utils/test-data';

/**
 * Manual QA cases 1-9 — the REFERENCES side panel on the staff complaint-detail screen.
 *
 * ── What the screen actually is ───────────────────────────────────────────────────────────────────
 * The staff complaint-detail screen is `staff/task-action`. Its right sidebar
 * (task-action.component.html:632-710) holds three sections:
 *
 *   1. "Reference"            — html:634-639. A header and NOTHING ELSE. There is no body element, no
 *                               link, no list, no empty state. It is four lines of markup.
 *   2. "Past Complaints"      — html:641-678, driven by loadPastComplaints() (component.ts:303-330).
 *   3. "Reference Documents"  — html:680-707, rendering `complaint()!.attachments`.
 *
 * ── Why most of these tests are expected to FAIL, and why they are written to fail ────────────────
 * Verified against the running backend on 8092 and against cms-backend source:
 *
 *   • NO policy/circular reference backend exists. All 66 *Controller.java files were grepped for
 *     "reference"/"policy"/"circular" and none exposes a reference-document surface. Probed live and
 *     404: /api/v1/references, /api/v1/reference-documents, /api/v1/policies, /api/v1/circulars,
 *     /api/v1/complaints/{n}/references, /api/v1/complaints/{n}/reference-documents. There is also no
 *     policy/circular table in cms_db (113 tables; only `retention_policy`, unrelated).
 *
 *   • loadPastComplaints() calls the WRONG endpoint. It issues
 *     GET /api/v1/complaints?complainantEmail=… (component.ts:308). That path is
 *     ComplaintApiV1Controller.getComplaintsByPhone (ComplaintApiV1Controller.java:259) — the CITIZEN
 *     track-my-complaint list. It does not declare a `complainantEmail` parameter at all, and it
 *     rejects any caller without a citizen OTP session with 401 SESSION_EXPIRED
 *     (ComplaintApiV1Controller.java:272-275). The subscribe() error arm sets the list to [] and drops
 *     the failure on the floor (component.ts:315-318), so the sidebar renders its tidy
 *     "No past complaints found" empty state for EVERY complaint, forever.
 *     The endpoint that would work — GET /api/v1/past-complaints/by-complainant?email=…
 *     (PastComplaintController.java:18) — exists, returns 200 and returns real rows. It is simply not
 *     the one the component calls.
 *
 *   • "Reference Documents" reads a field the server never sends. The detail response contains
 *     `documents` (hardcoded to an empty list — ComplaintApiV1Controller.java:489) and has no
 *     `attachments` key whatsoever. The template reads `complaint()!.attachments`, so the section is
 *     permanently "No documents attached". Its View and Download buttons (html:699-700) carry NO
 *     (click) binding — they are inert markup.
 *
 * Per the honesty rule these assertions state the behaviour the QA case requires. They are not
 * softened to match what the code does.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/** The Reference section — the FIRST sidebar section, whose heading is exactly "Reference". */
function referenceSection(page: import('@playwright/test').Page) {
  return page
    .locator('.col-right-sidebar .sidebar-section')
    .filter({ has: page.locator('.sidebar-section-header h4') })
    .filter({ hasNotText: 'Reference Documents' })
    .filter({ hasNotText: 'Past Complaints' })
    .first();
}

function referenceDocumentsSection(page: import('@playwright/test').Page) {
  return page
    .locator('.col-right-sidebar .sidebar-section')
    .filter({ hasText: 'Reference Documents' })
    .first();
}

function pastComplaintsSection(page: import('@playwright/test').Page) {
  return page
    .locator('.col-right-sidebar .sidebar-section')
    .filter({ hasText: 'Past Complaints' })
    .first();
}

test.describe('CEPC References side panel (QA 1-9)', () => {
  let keycloakUp: boolean;

  // Two complaints at DIFFERENT statuses, for the "references are contextual to the stage" case.
  let earlyStageComplaint: string;
  let lateStageComplaint: string;

  // Two complaints sharing ONE complainant email, so a genuine past-complaint history exists.
  let repeatComplainantEmail: string;
  let firstOfRepeat: string;
  let secondOfRepeat: string;

  test.beforeAll(async ({ browser, request }) => {
    const probe = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(probe);
    await probe.close();
    if (!keycloakUp) return;

    const early = await createTestComplaint(request, { subject: 'QA-REF early stage' });
    earlyStageComplaint = early.complaintNumber;
    await advanceToStatus(request, earlyStageComplaint, 'in_progress');

    const late = await createTestComplaint(request, { subject: 'QA-REF late stage' });
    lateStageComplaint = late.complaintNumber;
    await advanceToStatus(request, lateStageComplaint, 'awaiting_closure');

    // A repeat complainant. The sidebar's whole purpose is to surface this history, so it has to
    // actually exist — otherwise an empty panel could not be distinguished from a broken one.
    repeatComplainantEmail = `qa_ref_repeat_${Date.now().toString(36)}@example.com`;
    const first = await createTestComplaint(request, {
      subject: 'QA-REF prior complaint by the same complainant',
      complainantEmail: repeatComplainantEmail,
    });
    firstOfRepeat = first.complaintNumber;
    await advanceToStatus(request, firstOfRepeat, 'closed');

    const second = await createTestComplaint(request, {
      subject: 'QA-REF current complaint by the same complainant',
      complainantEmail: repeatComplainantEmail,
    });
    secondOfRepeat = second.complaintNumber;
    await advanceToStatus(request, secondOfRepeat, 'in_progress');
  });

  test.afterAll(async ({ request }) => {
    for (const n of [earlyStageComplaint, lateStageComplaint, firstOfRepeat, secondOfRepeat]) {
      if (n) await cleanupComplaint(request, n);
    }
  });

  test.beforeEach(async () => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');
  });

  // ── QA 1 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA1: a CEPC user can log in to CMS and lands on the staff task list', async ({ page }) => {
    await loginAsCepcRole(page, 'DO', '/staff/cepc/tasks');

    // A real Keycloak round trip must have completed: we must NOT still be on the IdP, and must NOT
    // have been bounced back to the app's own login page.
    expect(page.url(), 'the browser must have returned from Keycloak to the app').not.toContain('/realms/');
    expect(page.url(), 'the session must be authenticated, not redirected to /staff/login')
      .not.toContain('/staff/login');
    await expect(page.locator('h2:has-text("CEPC")')).toBeVisible({ timeout: 15000 });

    await logout(page);
  });

  // ── QA 2 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA2: clicking a complaint opens the detail page and offers Reference as a side option', async ({ page }) => {
    await loginAsCepcRole(page, 'DO', '/staff/cepc/tasks');
    await page.waitForSelector('.task-card, .empty-state', { timeout: 20000 });

    // Navigate the way a user does — via the task card — rather than deep-linking, because the QA
    // case is specifically "after clicking a complaint".
    const card = page.locator('.task-card').first();
    await expect(card, 'the DO queue must contain at least one task to click').toBeVisible({ timeout: 15000 });
    await card.click();

    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const ref = referenceSection(page);
    await expect(ref, 'the Reference side option must be present on the complaint-detail page')
      .toBeVisible({ timeout: 10000 });
    await expect(ref.locator('.sidebar-section-header h4')).toContainText('Reference');

    await logout(page);
  });

  // ── QA 3 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA3: the references shown are CONTEXTUAL — two different stages surface different references', async ({ page }) => {
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${earlyStageComplaint}`);
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const earlyRefs = (
      await referenceSection(page).locator('a, .reference-item, .ref-link').allTextContents()
    ).map(t => t.trim()).filter(Boolean);

    // The QA case requires applicable policy/circular references at the stage. An empty Reference
    // panel is a failure, not an acceptable "nothing applicable here" — the panel offers no way to
    // say that either.
    expect(
      earlyRefs.length,
      `the Reference panel showed no references at all for ${earlyStageComplaint} (status in_progress)`
    ).toBeGreaterThan(0);

    await page.goto(`${APP_BASE}/staff/cepc/task/${lateStageComplaint}`, { waitUntil: 'networkidle' });
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const lateRefs = (
      await referenceSection(page).locator('a, .reference-item, .ref-link').allTextContents()
    ).map(t => t.trim()).filter(Boolean);

    expect(
      lateRefs.length,
      `the Reference panel showed no references for ${lateStageComplaint} (status awaiting_closure)`
    ).toBeGreaterThan(0);

    // Contextual means stage-dependent. An identical list at both stages would mean the panel is a
    // static block of links, which is what the QA case is written to rule out.
    expect(
      lateRefs.join('|'),
      'the references at awaiting_closure must differ from those at in_progress — otherwise they are not contextual'
    ).not.toBe(earlyRefs.join('|'));

    await logout(page);
  });

  // ── QA 4 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA4: past complaints of the same complainant are displayed in the reference area', async ({ page }) => {
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${secondOfRepeat}`);
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const past = pastComplaintsSection(page);
    await expect(past).toBeVisible({ timeout: 10000 });

    // The complainant demonstrably has a prior complaint (firstOfRepeat, seeded and closed above),
    // so the empty state is wrong here by construction.
    await expect(
      past.locator('.sidebar-empty'),
      `"No past complaints found" is wrong: ${repeatComplainantEmail} also filed ${firstOfRepeat}`
    ).toHaveCount(0);

    const cards = past.locator('.past-complaint-card');
    await expect
      .poll(async () => cards.count(), {
        message: 'the prior complaint must appear as a past-complaint card',
        timeout: 15000,
      })
      .toBeGreaterThan(0);

    await expect(past, 'the prior complaint number must be listed').toContainText(firstOfRepeat);

    await logout(page);
  });

  test('QA4b: the endpoint the sidebar actually calls returns the complainant history to a staff caller', async ({ request }) => {
    // Isolates the root cause of QA4 from anything to do with rendering. This is the exact request
    // loadPastComplaints() issues (task-action.component.ts:308).
    const res = await request.get(`${API_BASE}/api/v1/complaints`, {
      params: { complainantEmail: repeatComplainantEmail },
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });

    expect(
      res.status(),
      `GET /api/v1/complaints?complainantEmail= answered ${res.status()}: ${await res.text()}`
    ).toBe(200);

    const body = await res.json();
    const rows: Array<Record<string, unknown>> =
      body.data?.content || body.data || body.content || body || [];
    expect(Array.isArray(rows), 'the response must be a list of complaints').toBeTruthy();
    expect(
      rows.map(r => r['complaintNumber']),
      'the complainant history must include the earlier complaint'
    ).toContain(firstOfRepeat);
  });

  test('QA4c: the past-complaint endpoint that DOES exist returns the history (positive control)', async ({ request }) => {
    // Proves the data and a working endpoint both exist, so QA4/QA4b are a wiring defect in the
    // component rather than a missing capability. PastComplaintController.java:18.
    const res = await request.get(`${API_BASE}/api/v1/past-complaints/by-complainant`, {
      params: { email: repeatComplainantEmail },
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });

    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    const ids = (body.data as Array<Record<string, unknown>>).map(r => r['complaintId']);
    expect(ids, 'the working endpoint must return the earlier complaint').toContain(firstOfRepeat);
  });

  test('QA4d: the complaint detail masks the email the sidebar uses as its lookup key', async ({ request }) => {
    // A SECOND, independent defect behind QA4, and the reason fixing only the endpoint would not fix
    // the panel. GET /api/v1/complaints/{n} returns complainantEmail through PiiMaskingService
    // (ComplaintApiV1Controller.java:416-417), so the value loadPastComplaints() reads off
    // complaint() is "t***@example.com". That is what it then sends as the lookup key, and a masked
    // address can never match a stored one.
    const res = await request.get(`${API_BASE}/api/v1/complaints/${secondOfRepeat}`, {
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });
    expect(res.status(), await res.text()).toBe(200);
    const detail = (await res.json()).data as Record<string, unknown>;

    expect(
      detail['complainantEmail'],
      'the sidebar keys its past-complaint lookup on this field, so a masked value makes the lookup impossible'
    ).toBe(repeatComplainantEmail);
  });

  // ── QA 5 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA5: the CEPC user can click a reference link', async ({ page }) => {
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${earlyStageComplaint}`);
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const links = referenceSection(page).locator('a, button, .ref-link');
    await expect
      .poll(async () => links.count(), {
        message: 'the Reference panel must expose at least one clickable reference link',
        timeout: 10000,
      })
      .toBeGreaterThan(0);

    await expect(links.first()).toBeEnabled();

    await logout(page);
  });

  // ── QA 6 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA6: clicking a reference link opens or downloads the current version of the document', async ({ page }) => {
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${earlyStageComplaint}`);
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const link = referenceSection(page).locator('a, button, .ref-link').first();
    await expect(link, 'there must be a reference link to click').toBeVisible({ timeout: 10000 });

    // Either outcome satisfies the QA case: a new tab/navigation, or a file download. Both are
    // observed rather than assumed — a click that does nothing at all fails.
    const downloadPromise = page.waitForEvent('download', { timeout: 8000 }).catch(() => null);
    const popupPromise = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null);
    const responsePromise = page
      .waitForResponse(r => /polic|circular|document|reference/i.test(r.url()), { timeout: 8000 })
      .catch(() => null);

    await link.click();

    const [download, popup, response] = await Promise.all([downloadPromise, popupPromise, responsePromise]);
    expect(
      Boolean(download || popup || response),
      'clicking a reference link must open the document or download it — nothing happened'
    ).toBeTruthy();

    await logout(page);
  });

  // ── QA 7 & 8 ────────────────────────────────────────────────────────────────────────────────────
  test('QA7: only CURRENT, APPROVED reference documents are linked', async ({ request }) => {
    // Asserted server-side: whether a document is current and approved is a data property, and the UI
    // showing a tidy list proves nothing about what was filtered out. A reference catalogue must
    // therefore exist and must carry status/version metadata to filter on.
    const res = await request.get(`${API_BASE}/api/v1/reference-documents`, {
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });

    expect(
      res.status(),
      `the reference-document catalogue must exist; /api/v1/reference-documents answered ${res.status()}`
    ).toBe(200);

    const body = await res.json();
    const docs: Array<Record<string, unknown>> = body.data || body || [];
    expect(Array.isArray(docs)).toBeTruthy();
    for (const d of docs) {
      const status = String(d['status'] ?? d['publicationStatus'] ?? '').toUpperCase();
      expect(
        ['APPROVED', 'PUBLISHED', 'CURRENT'],
        `a linked reference document carried status "${status}"`
      ).toContain(status);
      expect(d['isCurrent'] ?? true, 'a superseded version must not be linked').not.toBe(false);
    }
  });

  test('QA8: an outdated or unpublished policy/circular is NOT displayed', async ({ page, request }) => {
    // Proven by comparing what the catalogue holds with what the panel renders: a draft/superseded
    // row present in the catalogue must be absent from the sidebar. Without the catalogue there is
    // nothing to compare against, which is itself the finding.
    const res = await request.get(`${API_BASE}/api/v1/reference-documents`, {
      params: { includeAll: 'true' },
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });
    expect(
      res.status(),
      `the catalogue must be readable to prove filtering; answered ${res.status()}`
    ).toBe(200);

    const body = await res.json();
    const docs: Array<Record<string, unknown>> = body.data || body || [];
    const suppressed = docs.filter(d => {
      const status = String(d['status'] ?? d['publicationStatus'] ?? '').toUpperCase();
      return status === 'DRAFT' || status === 'UNPUBLISHED' || status === 'SUPERSEDED' || d['isCurrent'] === false;
    });
    expect(
      suppressed.length,
      'the catalogue must contain at least one non-current document, or this case cannot be proven'
    ).toBeGreaterThan(0);

    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${earlyStageComplaint}`);
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const shown = await referenceSection(page).innerText();
    for (const d of suppressed) {
      const title = String(d['title'] ?? d['name'] ?? '');
      if (!title) continue;
      expect(shown, `an outdated/unpublished document "${title}" is displayed`).not.toContain(title);
    }

    await logout(page);
  });

  // ── QA 9 ────────────────────────────────────────────────────────────────────────────────────────
  test('QA9: a complaint with no configured reference document shows a clean empty state, no broken link and no error', async ({ page }) => {
    const failedRequests: string[] = [];
    const consoleErrors: string[] = [];

    page.on('response', r => {
      if (r.status() >= 400 && r.url().includes('/api/')) {
        failedRequests.push(`${r.status()} ${r.request().method()} ${r.url()}`);
      }
    });
    page.on('console', m => {
      if (m.type() === 'error') consoleErrors.push(m.text());
    });

    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${earlyStageComplaint}`);
    await expect(page.locator('.col-right-sidebar')).toBeVisible({ timeout: 20000 });

    const docs = referenceDocumentsSection(page);
    await expect(docs).toBeVisible({ timeout: 10000 });

    // A readable empty state rather than a blank panel or a placeholder error.
    await expect(
      docs.locator('.sidebar-empty'),
      'a complaint with no reference document must show an explicit empty state'
    ).toBeVisible();
    await expect(docs.locator('.sidebar-empty')).toContainText(/No documents attached/i);

    // No half-rendered document rows with dead controls.
    await expect(docs.locator('.doc-item'), 'no document rows may be rendered when there are none')
      .toHaveCount(0);

    // And nothing may have failed quietly on the way there. An API 4xx/5xx swallowed by a catchError
    // is precisely how an unbuilt panel comes to look healthy — the empty state above is the direct
    // product of exactly this, so asserting it alone would report a pass.
    //
    // Deduplicated by method+path so the count reflects the number of DISTINCT broken calls rather
    // than how many times Angular happened to retry each one.
    const distinctFailures = [...new Set(failedRequests.map(f => f.replace(/\?.*$/, '')))].sort();
    expect(
      distinctFailures,
      `the page made failing API calls while rendering the reference panel:\n${distinctFailures.join('\n')}`
    ).toEqual([]);
    expect(
      consoleErrors.filter(e => !/favicon|ResizeObserver/i.test(e)),
      `the page logged console errors:\n${consoleErrors.join('\n')}`
    ).toEqual([]);

    await logout(page);
  });
});
