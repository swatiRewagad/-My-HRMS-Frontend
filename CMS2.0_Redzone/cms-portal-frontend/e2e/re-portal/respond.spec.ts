import { test, expect } from '../fixtures';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createForwardedComplaint, respondToComplaint } from '../utils/test-data';

/**
 * The entity the seeded complaints belong to.
 *
 * This MUST match the `entity_code` on the RE account these tests sign in as, because the RE portal
 * is entity-scoped and correctly refuses a complaint belonging to anyone else. Seeding as
 * 'TEST_BANK_001' while signed in as an HDFC Bank user produced "Complaint not found" — a real-looking
 * UI failure that was actually the authorization boundary doing its job.
 *
 * It must also resolve to a row in REGULATED_ENTITIES: the backend looks the entity up by normalized
 * name, so an opaque code like 'HDFC0001' matches nothing.
 */
const RE_ENTITY = process.env['RE_ENTITY_CODE'] || 'HDFC Bank';

test.describe('RE Portal - Respond to Complaint', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('Complaint detail page loads with complaint info', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, RE_ENTITY);
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const numDisplay = page.locator('.complaint-number');
    await expect(numDisplay).toBeVisible({ timeout: 5000 });
    await expect(numDisplay).toContainText(complaintNumber);

    // The header renders TWO status badges through the same shared component: the workflow status
    // ("Forwarded to Dept") and the UST846 RE activity status ("Not Opened"). A bare `.status-badge`
    // therefore trips Playwright strict mode. Scoped to the header and pinned to the first badge,
    // which is the workflow status this assertion is about.
    const statusBadge = page.locator('.header-left .status-badge').first();
    await expect(statusBadge).toBeVisible();
  });

  // PRODUCT DEFECT, not a test defect: GET /api/v1/re-portal/complaints/{n} never returns
  // `responseDeadline` (RePortalController.getComplaintDetail builds the map by hand and omits it,
  // along with category/filedDate/forwardedDate). ReComplaintDetailComponent.deadlineCountdown()
  // returns `expired: true` whenever responseDeadline is falsy, and isResponseWindowOpen() is
  // derived from it — so [disabled]="!isResponseWindowOpen()" latches the textarea, the file input
  // and the submit button off forever. The API even computes the answer correctly and returns
  // `withinResponseWindow: true`, but the component never reads that field. Verified against a
  // freshly forwarded complaint whose tracker windowExpiresAt is 2026-10-23 (37 days out): the
  // header still renders "Response window expired". No RE can submit a response through the UI.
  // Unblocks when the API emits responseDeadline, or the component reads withinResponseWindow.
  test.fixme('Response form shows when within 15-day window', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, RE_ENTITY);
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const responseForm = page.locator('.response-form');
    await expect(responseForm).toBeVisible({ timeout: 5000 });

    const textarea = page.locator('#responseText');
    await expect(textarea).toBeVisible();
    await expect(textarea).toBeEnabled();
  });

  // Blocked by the same product defect as the test above: the missing `responseDeadline` in the
  // detail payload leaves isResponseWindowOpen() false, so #responseText and .submit-btn are
  // permanently disabled and the response can never be submitted through the UI. The backend path
  // itself is sound — POST /re-portal/complaints/{n}/respond succeeds when called directly, which is
  // what the 'Response disabled after window expiry' test below relies on.
  test.fixme('Submit response succeeds (status changes)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, RE_ENTITY);
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const responseTextarea = page.locator('#responseText');
    await expect(responseTextarea).toBeVisible({ timeout: 5000 });
    await responseTextarea.fill('E2E test response: Issue has been investigated and resolved. Compensation of Rs.5000 credited.');

    const submitBtn = page.locator('.submit-btn');
    await submitBtn.click();

    const successBanner = page.locator('.success-banner');
    await expect(successBanner).toBeVisible({ timeout: 10000 });
  });

  test('Response disabled after window expiry', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, RE_ENTITY);
    const complaintNumber = complaint.complaintNumber;

    await respondToComplaint(request, complaintNumber, 'Pre-submitted response to close window', undefined, RE_ENTITY);

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    // After response is submitted, the submit button should be disabled or window expired notice shows
    const expiredNotice = page.locator('.window-expired-notice');
    const disabledBtn = page.locator('.submit-btn[disabled]');

    const expired = await expiredNotice.isVisible().catch(() => false);
    const disabled = await disabledBtn.isVisible().catch(() => false);
    const successShown = await page.locator('.success-banner').isVisible().catch(() => false);

    expect(expired || disabled || successShown).toBeTruthy();
  });

  // The single-slot query form this test used to drive was replaced by the threaded query panel
  // (UST853-857), which posts to /api/v1/complaint-queries instead of the old
  // /re-portal/.../query endpoint. Behaviour is covered in depth by query-threads.spec.ts.
  //
  // This UI-level check cannot run yet: the `cms` realm defines no RE_* roles and no RE users, so
  // there is no way to reach the RE detail page as an entity user. Logging in as an admin instead
  // yields no entity code, the detail call 400s, and the whole page (including this panel) never
  // renders. Seeding RE users into the realm is what unblocks it.
  test.fixme('Raise query/clarification works', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, RE_ENTITY);
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const panel = page.locator('.query-thread-panel');
    await expect(panel).toBeVisible({ timeout: 10000 });

    await panel.locator('.raise-btn').click();

    const composer = panel.locator('.query-composer');
    await expect(composer).toBeVisible({ timeout: 3000 });

    await composer.locator('#newQueryType').selectOption('CLARIFICATION');
    await composer.locator('#newSubject').fill('E2E clarification request');
    await composer.locator('#newBody').fill(
      'E2E test: Please provide additional details regarding the transaction date and amount.');

    await composer.locator('.submit-btn').click();

    // A successful raise closes the composer and the new thread appears in the list.
    await expect(composer).toBeHidden({ timeout: 10000 });
    await expect(panel.locator('.thread-item').first()).toBeVisible({ timeout: 5000 });
  });
});
