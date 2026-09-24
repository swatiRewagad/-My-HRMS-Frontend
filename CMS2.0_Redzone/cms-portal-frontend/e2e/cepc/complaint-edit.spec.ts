import { APIRequestContext } from '@playwright/test';
import { test, expect } from '../fixtures';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  cleanupComplaint,
  advanceToStatus,
  getComplaint,
  identityHeadersFor,
} from '../utils/test-data';

/**
 * CEPC complaint EDIT — manual QA cases 1-10.
 *
 * ── What the product actually offers, verified before these assertions were written ─────────────
 *
 * The ONLY write path that edits complaint fields (as opposed to driving a workflow transition) is
 * PUT /api/complaints/{id} — ComplaintController.java:98 → ComplaintService.updateComplaint:272.
 * It accepts four fields (UpdateComplaintRequest: status, priority, assignedOfficer, remarks), has
 * no method-level guard, and performs no terminal-status check.
 *
 * No Angular component calls it. Grepping the whole frontend for `api/complaints` / `updateComplaint`
 * returns nothing, and neither staff complaint screen renders an edit affordance:
 *   - staff/task-action.component.html — `startEditPresence` is presence TRACKING (UST675), not editing;
 *     the only `pi-pencil` is the closure-letter preview's "Edit" which merely closes a modal (line 873).
 *   - cepc/cepc-complaint-detail.component.html — every field is a read-only `<span>`.
 * The only real editor in the repo is crpc/draft-assessment (`enterEditMode()`), a CRPC screen for
 * PRE-registration drafts, not a CEPC complaint-details editor.
 *
 * These tests therefore assert SERVER truth wherever a write is involved. A hidden button is not a
 * control: the endpoint is reachable with curl, so "the UI shows no Edit button" would be a worthless
 * pass for cases 1, 3 and 7.
 *
 * ── Profile caveat that matters for the two authorisation cases ──────────────────────────────────
 * This backend runs the dev-local profile, whose DevLocalSecurityConfig:35 is `anyRequest().permitAll()`.
 * So a 403 cannot come from the security CHAIN here. That does not make cases 3 and 7 untestable
 * against the enforcing profile either, because SecurityConfig.java:192 grants /api/complaints/** to
 * the whole STAFF_ROLES array (line 69-76) — which includes CEPC_CONTACT_PERSON. Editing is not
 * differentiated by role in EITHER profile, and there is no @PreAuthorize on the method. The refusal
 * these tests require does not exist anywhere to be enabled.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/**
 * PUT /api/complaints/{id} without throwing, so a refusal stays assertable.
 *
 * Deliberately NOT added to utils/test-data.ts: five other suites share that file concurrently and
 * this helper is only meaningful for the edit endpoint.
 */
async function editComplaint(
  request: APIRequestContext,
  numericId: number | string,
  body: Record<string, unknown>,
  actor: string
): Promise<{ status: number; body: string }> {
  const response = await request.put(`${API_BASE}/api/complaints/${numericId}`, {
    data: body,
    headers: { 'Content-Type': 'application/json', ...identityHeadersFor(actor) },
  });
  return { status: response.status(), body: await response.text() };
}

/** The canonical history feed: GET /{complaintNumber}/history carries performedBy and performedByRole. */
async function readHistory(
  request: APIRequestContext,
  complaintNumber: string
): Promise<Array<Record<string, any>>> {
  const response = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}/history`, {
    headers: identityHeadersFor('cepc_do_001'),
  });
  expect(response.status(), 'GET /history must be available to read the audit trail back').toBe(200);
  const json = await response.json();
  return json.data || [];
}

test.describe('CEPC complaint edit', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  // ── Case 1 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 1 — a CLOSED complaint cannot be edited: the SERVER refuses', async ({ request }) => {
    const created = await createTestComplaint(request, { subject: 'QA-EDIT-1 closed is not editable' });
    await advanceToStatus(request, created.complaintNumber, 'closed');

    const before = await getComplaint(request, created.complaintNumber);
    expect(String(before['status']).toLowerCase()).toBe('closed');

    const result = await editComplaint(
      request,
      created.complaintId,
      { priority: 'CRITICAL', remarks: 'QA-EDIT-1 attempt to edit a closed complaint' },
      'cepc_do_001'
    );

    // A closed complaint is a settled record. Editing it must be refused, not merely hidden.
    expect(
      result.status,
      'editing a closed complaint must be refused by the server, ' +
        `but PUT /api/complaints/${created.complaintId} answered ${result.status}`
    ).toBeGreaterThanOrEqual(400);

    // And the refusal must be real: the stored record must be untouched.
    const after = await getComplaint(request, created.complaintNumber);
    expect(String(after['priority']).toUpperCase(), 'a refused edit must not change the record')
      .toBe(String(before['priority']).toUpperCase());
  });

  // ── Case 2 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 2 — mandatory fields cannot be blanked while editing', async ({ request }) => {
    const created = await createTestComplaint(request, { subject: 'QA-EDIT-2 blank mandatory fields' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');
    const before = await getComplaint(request, created.complaintNumber);

    const result = await editComplaint(
      request,
      created.complaintId,
      { status: '', priority: '', remarks: '' },
      'cepc_do_001'
    );

    expect(
      result.status,
      'submitting blank mandatory fields must be rejected, ' +
        `but the server answered ${result.status}`
    ).toBeGreaterThanOrEqual(400);

    // The user-visible half of the case: the exact message the test case names.
    expect(result.body.toLowerCase()).toContain('mandatory');

    // Server truth: whatever the response said, status and priority must survive a blank submission.
    const after = await getComplaint(request, created.complaintNumber);
    expect(String(after['status']), 'status must not be blanked by an edit').not.toBe('');
    expect(String(after['priority']), 'priority must not be blanked by an edit').not.toBe('');
    expect(String(after['status']).toLowerCase()).toBe(String(before['status']).toLowerCase());

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 3 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 3 — edit actions are not offered to a role without edit rights', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-EDIT-3 no edit rights' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    // CEPC_CONTACT_PERSON is a responder, not an editor of the complaint record.
    await loginAsCepcRole(page, 'CP', `/staff/cepc/task/${created.complaintNumber}`);
    await expect(page.locator('.task-action-page')).toBeVisible({ timeout: 20000 });

    const editControls = page.locator(
      'button:has-text("Edit"), .btn-edit, [data-testid="edit-complaint"], [title="Edit complaint"]'
    );
    expect(await editControls.count(), 'a role without edit rights must see no edit control').toBe(0);
    await logout(page);

    // The control being absent proves nothing on its own — the endpoint is reachable directly, so the
    // server has to be the one refusing.
    const result = await editComplaint(
      request,
      created.complaintId,
      { priority: 'CRITICAL', remarks: 'QA-EDIT-3 edit attempted by a role with no edit rights' },
      'contact_person1'
    );
    expect(
      result.status,
      'a role with no edit rights must be refused by the server, not merely shown no button; ' +
        `PUT /api/complaints/${created.complaintId} answered ${result.status}`
    ).toBeGreaterThanOrEqual(400);

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 4 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 4 — an edit is recorded in the audit trail, naming the editor', async ({ request }) => {
    const created = await createTestComplaint(request, { subject: 'QA-EDIT-4 audit trail' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    const result = await editComplaint(
      request,
      created.complaintId,
      { priority: 'HIGH', remarks: 'QA-EDIT-4 priority raised to HIGH' },
      'cepc_do_001'
    );
    expect(result.status, 'a permitted edit must succeed so its audit row can be asserted').toBe(200);

    // The timeline write is @Async (ComplaintService.addTimelineAsync), so it lands shortly after.
    await expect
      .poll(async () => (await readHistory(request, created.complaintNumber))
        .filter(e => e.action === 'update' || e.action === 'status_change').length, { timeout: 15000 })
      .toBeGreaterThan(0);

    const history = await readHistory(request, created.complaintNumber);
    const editRow = history.find(e => e.action === 'update' || e.action === 'status_change');
    expect(editRow, 'the edit must appear on the audit trail').toBeTruthy();

    // An audit row that does not say WHO edited is not an audit trail.
    expect(
      editRow!['performedBy'],
      `the audit row must name the editor, but performedBy is "${editRow!['performedBy']}"`
    ).toBe('cepc_do_001');

    // And it must say WHAT changed.
    expect(editRow!['remarks']).toContain('QA-EDIT-4');

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 5 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 5 — a CEPC user with rights can save edited details, and they persist', async ({ request }) => {
    const created = await createTestComplaint(request, {
      subject: 'QA-EDIT-5 persistence',
      priority: 'LOW',
    });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    const result = await editComplaint(
      request,
      created.complaintId,
      { priority: 'HIGH', remarks: 'QA-EDIT-5 saved by a permitted CEPC user' },
      'cepc_do_001'
    );
    expect(result.status).toBe(200);

    // Read the record back rather than trusting the write response.
    const after = await getComplaint(request, created.complaintNumber);
    expect(String(after['priority']).toUpperCase()).toBe('HIGH');

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 6 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 6 — an edit control is present and clickable on the complaint-details screen', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-EDIT-6 edit button present' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${created.complaintNumber}`);
    await expect(page.locator('.task-action-page')).toBeVisible({ timeout: 20000 });

    const staffEdit = page.locator(
      'button:has-text("Edit"), .btn-edit, [data-testid="edit-complaint"], [title="Edit complaint"]'
    );

    // Also check the other CEPC complaint-details route, in case the control lives there instead.
    await page.goto(`${APP_BASE}/cepc/complaint/${created.complaintNumber}`, { waitUntil: 'networkidle' });
    await expect(page.locator('.cepc-detail .detail-layout')).toBeVisible({ timeout: 20000 });
    const detailEdit = page.locator(
      '.cepc-detail button:has-text("Edit"), .cepc-detail .btn-edit, .cepc-detail [title="Edit"]'
    );

    const total = (await staffEdit.count()) + (await detailEdit.count());
    expect(
      total,
      'neither /staff/cepc/task/:id nor /cepc/complaint/:id renders an edit control'
    ).toBeGreaterThan(0);

    const control = (await detailEdit.count()) > 0 ? detailEdit.first() : staffEdit.first();
    await expect(control).toBeEnabled();
    await control.click();

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 7 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 7 — edit access depends on role: a DO may edit, a contact person may not', async ({ request }) => {
    const created = await createTestComplaint(request, { subject: 'QA-EDIT-7 role-dependent edit' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    // Permitted role.
    const asDo = await editComplaint(
      request,
      created.complaintId,
      { priority: 'HIGH', remarks: 'QA-EDIT-7 edit by CEPC_DO' },
      'cepc_do_001'
    );
    expect(asDo.status, 'a CEPC Dealing Officer must be able to edit').toBe(200);
    const afterDo = await getComplaint(request, created.complaintNumber);
    expect(String(afterDo['priority']).toUpperCase()).toBe('HIGH');

    // Role without edit rights.
    const asCp = await editComplaint(
      request,
      created.complaintId,
      { priority: 'LOW', remarks: 'QA-EDIT-7 edit by CEPC_CONTACT_PERSON' },
      'contact_person1'
    );
    expect(
      asCp.status,
      'a contact person must NOT be able to edit the complaint record; ' +
        `the server answered ${asCp.status}`
    ).toBeGreaterThanOrEqual(400);

    const afterCp = await getComplaint(request, created.complaintNumber);
    expect(String(afterCp['priority']).toUpperCase(), 'the refused edit must not have persisted')
      .toBe('HIGH');

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 8 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 8 — a CEPC user can log in to CMS and lands on the staff portal', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsCepcRole(page, 'DO', '/staff/dashboard');

    // Still on the app, not bounced back to Keycloak or to the unauthorized page.
    expect(page.url()).not.toContain('/realms/');
    expect(page.url()).not.toContain('/staff/login');
    expect(page.url()).not.toContain('/staff/unauthorized');

    // /staff/dashboard deliberately forwards each user to their own office's workspace
    // (staff-dashboard.component.ts:204 sends a CEPC user to /cepc/dashboard), so the landing URL is
    // asserted as "a CEPC staff workspace" rather than the literal /staff/dashboard.
    expect(page.url()).toMatch(/\/(staff\/dashboard|staff\/cepc\/tasks|cepc\/dashboard)/);

    // A real session: the app knows who the user is.
    const identity = page.locator('.user-name, .user-details, .role-badge, .user-avatar');
    await expect(identity.first()).toBeVisible({ timeout: 20000 });

    await logout(page);
  });

  // ── Case 9 ──────────────────────────────────────────────────────────────────────────────────────
  test('case 9 — the history section is read-only and cannot be edited', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-EDIT-9 history is read-only' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    // Server side: every mutating verb on the history resource must be refused.
    for (const method of ['put', 'patch', 'delete'] as const) {
      const response = await request[method](
        `${API_BASE}/api/v1/complaints/${created.complaintNumber}/history`,
        {
          data: { remarks: 'QA-EDIT-9 tampering with the audit trail' },
          headers: { 'Content-Type': 'application/json', ...identityHeadersFor('cepc_do_001') },
        }
      );
      expect(
        response.status(),
        `${method.toUpperCase()} on the history resource must be refused`
      ).toBe(403);
    }

    // The history rows themselves must be unchanged by those attempts.
    const history = await readHistory(request, created.complaintNumber);
    expect(history.length).toBeGreaterThan(0);
    expect(history.some(e => (e.remarks || '').includes('tampering'))).toBe(false);

    // UI side: the history panel offers no editing affordance.
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${created.complaintNumber}`);
    await expect(page.locator('.task-action-page')).toBeVisible({ timeout: 20000 });
    await page.locator('.icon-sidebar-btn[title="History"]').click();
    const panel = page.locator('.history-panel').first();
    await expect(panel).toBeVisible({ timeout: 10000 });

    // "Read-only" means no field that can CHANGE a history record. The hide-comments checkbox that
    // cases 17-19 require is a view control: it alters what is displayed, never what is stored. It is
    // excluded by type rather than by weakening the rule — any text/date/number input, textarea,
    // select or contenteditable region in the panel still fails this, and the loop below proves the
    // only checkbox present is that display toggle.
    expect(
      await panel
        .locator(
          'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]), ' +
            'textarea, select, [contenteditable="true"]'
        )
        .count(),
      'the history panel must not expose any field capable of altering a history record'
    ).toBe(0);
    const checkboxes = panel.locator('input[type="checkbox"]');
    for (let i = 0; i < (await checkboxes.count()); i++) {
      expect(
        await checkboxes.nth(i).evaluate(el => !!el.closest('.history-comments-toggle')),
        'the only checkbox permitted in the history panel is the hide-comments display toggle'
      ).toBe(true);
    }
    expect(
      await panel.locator('button:has-text("Edit"), button:has-text("Delete"), .btn-edit').count(),
      'the history panel must not offer edit or delete controls'
    ).toBe(0);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 10 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 10 — a CEPC user can record and edit Legal case details', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-EDIT-10 legal case details' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    // Server side, as a CEPC role: read, then write, then read back.
    const read = await request.get(
      `${API_BASE}/api/v1/complaints/${created.complaintNumber}/legal-case`,
      { headers: identityHeadersFor('cepc_do_001') }
    );
    expect(
      read.status(),
      'a CEPC user must be able to read the legal case details of a CEPC complaint; ' +
        `GET /legal-case answered ${read.status()}`
    ).toBe(200);

    const write = await request.post(
      `${API_BASE}/api/v1/complaints/${created.complaintNumber}/legal-case`,
      {
        data: {
          caseNumber: 'WP/QA-EDIT-10/2026',
          courtName: 'Bombay High Court',
          caseStatus: 'PENDING',
          remarks: 'QA-EDIT-10 legal case recorded by a CEPC user',
        },
        headers: { 'Content-Type': 'application/json', ...identityHeadersFor('cepc_do_001') },
      }
    );
    expect(
      write.status(),
      `a CEPC user must be able to edit legal case details; POST /legal-case answered ${write.status()}`
    ).toBe(200);

    const reread = await request.get(
      `${API_BASE}/api/v1/complaints/${created.complaintNumber}/legal-case`,
      { headers: identityHeadersFor('cepc_do_001') }
    );
    const saved = (await reread.json()).data;
    expect(saved?.caseNumber, 'the legal case details must persist').toBe('WP/QA-EDIT-10/2026');

    // UI side: the Legal case area must be reachable on a CEPC complaint.
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${created.complaintNumber}`);
    await expect(page.locator('.task-action-page')).toBeVisible({ timeout: 20000 });
    await expect(
      page.locator('app-rbio-legal-case'),
      'the Legal case panel must render on a CEPC complaint'
    ).toHaveCount(1);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });
});
