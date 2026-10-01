import { test, expect } from '../fixtures';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createForwardedComplaint } from '../utils/test-data';
import { installCorsShim } from '../public/browser-api';

/**
 * The RE complaint screen on the canonical three-region layout.
 *
 * <h2>Why this spec exists</h2>
 * The screen was a single 900px column of six stacked blocks. Every RBI-side detail screen (RBIO,
 * CEPC, AA) had already moved to summary-strip + facts-left + work-centre + consult-rail-right, so
 * an entity's nodal officer met a different shape than the officer they correspond with. The move is
 * now made, and the things it is easy to regress are not visible in a screenshot:
 *
 *  1. The RAIL MUST NOT REFLOW THE WORKING REGIONS. The response textarea holds unsaved ngModel
 *     text. A rail whose column widened on open would shift that textarea under the officer's
 *     cursor mid-sentence. Asserted on the centre panel's width, as rbio-three-panel.spec.ts
 *     asserts it on the assessment panel.
 *
 *  2. THE CORRESPONDENCE PANELS MUST STAY OUT OF THE RAIL. The rail destroys its body on collapse
 *     (`@if`, not `[hidden]`), so a half-written query placed inside it would be thrown away by a
 *     click on the rail icon. They belong in the centre region, and this asserts they are there.
 *
 *  3. NO app-comment-thread, EVER. ComplaintCommentService.isStaff() is a staff allowlist and
 *     refuses RE_PNO / RE_NODAL_OFFICER with 403. A well-meant "make it like the officer screen"
 *     change that added the thread here would render a panel that can only show an error. The
 *     entity's surfaces are app-query-thread (with the RBI side) and app-internal-notes.
 *
 *  4. NO app-workflow-action-bar. An RE is a RESPONDENT, not a driver of the state machine:
 *     PUT /re-portal/complaints/{n}/activity-status exists solely to answer 403 ("RE Activity
 *     Status is derived from your actions and cannot be set directly"). An action bar would offer
 *     transitions the server has no handler for.
 *
 * The seeded complaint belongs to RE_ENTITY, which must match the entity_code on the RE account
 * these tests sign in as — the portal is entity-scoped and correctly refuses anyone else's
 * complaint, which presents as "Complaint not found" rather than as an auth error.
 */
const RE_ENTITY = process.env['RE_ENTITY_CODE'] || 'HDFC Bank';

const RAIL = '.context-rail';

test.describe('RE complaint detail — three-region layout', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    const complaint = await createForwardedComplaint(request, RE_ENTITY);
    complaintNumber = complaint.complaintNumber;
  });

  // The page's own fetch is blocked by CORS whenever the dev server runs on a port outside
  // cms-backend's allow-list, which renders "Complaint not found" and fails every assertion below
  // for a reason unrelated to the layout. See ../public/browser-api.ts.
  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await installCorsShim(page);
    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 20000 });
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) await logout(page);
  });

  test('the screen is a summary strip, two working regions and a collapsed rail', async ({ page }) => {
    // The shared strip, the same component RBIO/CEPC/AA/CRPC host.
    await expect(page.locator('app-complaint-summary .meta-row')).toBeVisible();

    await expect(page.locator('.detail-content .left-panel')).toBeVisible();
    await expect(page.locator('.detail-content .right-panel')).toBeVisible();

    // Collapsed by default: the icon strip renders, the body does not. A rail that opened itself
    // would take width from the response form on every page load.
    await expect(page.locator(`${RAIL} .rail-icons`)).toBeVisible();
    await expect(page.locator(`${RAIL} .rail-body`)).toHaveCount(0);

    // ONE icon, not the reference screen's three. Attachments and email have no served source on an
    // RE screen — GET /api/files/complaint/{id} is keyed by the NUMERIC id, which the RE detail
    // payload does not emit, so an attachments panel would show an empty list on every complaint
    // and tell the entity there are no documents when there may be several.
    await expect(page.locator(`${RAIL} .rail-icon`)).toHaveCount(1);
  });

  test('opening the rail does not resize the working regions', async ({ page }) => {
    const before = await page.locator('.right-panel').boundingBox();

    await page.locator(`${RAIL} .rail-icon`).first().click();
    await expect(page.locator(`${RAIL} .rail-body`)).toBeVisible();

    const after = await page.locator('.right-panel').boundingBox();
    expect(before).not.toBeNull();
    expect(after).not.toBeNull();
    // Grid rounding of a few pixels is acceptable; the rail's body overlays leftward and its COLUMN
    // is fixed at 48px, so anything larger means the column itself grew.
    expect(
      Math.abs(after!.width - before!.width),
      'the rail took width from the response form'
    ).toBeLessThan(24);
  });

  test('the rail carries the activity timeline, and closes again', async ({ page }) => {
    const icon = page.locator(`${RAIL} .rail-icon`).first();

    await icon.click();
    const body = page.locator(`${RAIL} .rail-body`);
    await expect(body).toBeVisible();
    // Either rendered entries or the explicit empty line — both are the timeline having LOADED. The
    // detail payload carries no `timeline` key, so this panel read "No activity recorded yet" on
    // every complaint until the separate GET …/timeline fetch was added; and that endpoint emits
    // performedBy/performedAt, which the component now normalises onto actor/timestamp.
    await expect(body.locator('.timeline, .no-timeline')).toHaveCount(1);

    await icon.click();
    await expect(page.locator(`${RAIL} .rail-body`)).toHaveCount(0);
  });

  test('the response form stays in the centre region, beside the facts', async ({ page }) => {
    const form = page.locator('.right-panel .response-form');
    await expect(form).toBeVisible();

    // Side by side, not stacked: the whole point of the move is that an entity can read the
    // complaint while writing the answer to it.
    const facts = await page.locator('.left-panel').boundingBox();
    const work = await page.locator('.right-panel').boundingBox();
    expect(facts).not.toBeNull();
    expect(work).not.toBeNull();
    expect(work!.x, 'the working region is not beside the facts').toBeGreaterThan(facts!.x);
  });

  test('correspondence sits in the centre region, never inside the rail', async ({ page }) => {
    // Both panels are present and both are in the CENTRE. The rail destroys its body on collapse,
    // so a composer inside it would lose a half-written query to a stray icon click.
    await expect(page.locator('.right-panel app-query-thread')).toHaveCount(1);
    await expect(page.locator('.right-panel app-internal-notes')).toHaveCount(1);
    await expect(page.locator(`${RAIL} app-query-thread`)).toHaveCount(0);
    await expect(page.locator(`${RAIL} app-internal-notes`)).toHaveCount(0);
  });

  test('no comment thread and no workflow action bar on an entity screen', async ({ page }) => {
    // Both would be 403s wearing a panel. See the header of this file.
    await expect(page.locator('app-comment-thread')).toHaveCount(0);
    await expect(page.locator('app-workflow-action-bar')).toHaveCount(0);
  });

  test('the strip states the response window and never the full complainant name', async ({ page }) => {
    const strip = page.locator('app-complaint-summary');

    // The window as an SLA chip: it is the one fact that decides whether this entity can still act.
    await expect(strip.locator('.sla-badge')).toBeVisible();

    // A respondent entity must not be shown the complainant's name in full, and the strip must not
    // be the one place that leaks what the left panel masks.
    const owner = strip.locator('.meta-item', { has: page.locator('.pi-user') });
    await expect(owner.locator('.meta-value')).toContainText('*');
  });

  test('no caption is rendered above an empty value', async ({ page }) => {
    // GET /re-portal/complaints/{n} does not emit category, filedDate or forwardedDate — the
    // controller builds its map by hand. Those captions used to render with nothing beside them,
    // which reads as data loss rather than as an absent field.
    const captions = page.locator('.left-panel .field label');
    const count = await captions.count();
    expect(count, 'the facts region rendered no fields at all').toBeGreaterThan(0);

    for (let i = 0; i < count; i++) {
      const field = captions.nth(i).locator('..');
      const label = (await captions.nth(i).innerText()).trim();
      const value = (await field.locator('span').first().innerText()).trim();
      expect(value, `the caption "${label}" has no value beside it`).not.toBe('');
    }
  });

  test('the screen renders without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });

    await page.reload({ waitUntil: 'networkidle' });
    await expect(page.locator('.detail-content')).toBeVisible({ timeout: 20000 });
    await page.locator(`${RAIL} .rail-icon`).first().click();
    await expect(page.locator(`${RAIL} .rail-body`)).toBeVisible();

    // 404s from endpoints other sessions still owe are logged deliberately; an Angular/TS runtime
    // error is what this is looking for.
    const runtime = errors.filter(e => !/404|Failed to load resource|net::ERR/i.test(e));
    expect(runtime, runtime.join('\n')).toEqual([]);
  });
});
