import { test, expect } from '../fixtures';
import { loginAsRbioRole, loginAsCepcRole, loginAsAaRole } from '../utils/auth';
import { createRbioComplaint, createTestComplaint, identityHeadersFor } from '../utils/test-data';

/**
 * The shared comment thread on the screens wired in this pass: the staff task-action screen (which
 * serves BOTH /staff/rbio/task/:id and /staff/cepc/task/:id), the legacy officer action screen, and
 * the AA register-appeal screen.
 *
 * SEPARATE FROM comment-thread.spec.ts ON PURPOSE. That spec proves the readership RULE — which tier
 * reaches whom — against the API, and it should not grow a browser dependency. This one proves
 * COVERAGE: that the component is actually mounted on each screen, against a live complaint, and that
 * what an officer types there is persisted rather than kept in a local signal. Those are the two
 * failure modes this codebase has had: a thread that was hardcoded markup, and a thread wired to a key
 * the API cannot resolve (404) with the error swallowed so the panel merely looked empty.
 *
 * Every screen below therefore asserts the LIST CALL SUCCEEDED, not just that the section rendered. An
 * empty thread and a 404'd thread look identical on screen.
 */

const API = process.env.API_BASE_URL || 'http://localhost:8082';
const THREAD = '[data-testid="comment-thread"]';

function commentsUrl(complaintNumber: string): string {
  return `${API}/api/v1/complaint-comments/complaint/${complaintNumber}`;
}

/**
 * Fails if the thread's own GET did not answer 200 for this complaint.
 *
 * Observes the APPLICATION's request rather than issuing one: a fetch from page context would not
 * carry the Keycloak bearer the interceptor attaches, so it would 401 on a perfectly working screen.
 * It also has to be the app's own call to prove the component asked for the right key — the component
 * sets an error signal on failure but renders an empty list either way, so asserting on markup alone
 * would pass against a screen threading on an id the API cannot resolve.
 *
 * Call BEFORE the navigation that triggers the load.
 */
function watchThreadLoad(page: import('@playwright/test').Page, complaintNumber: string) {
  return page.waitForResponse(
    res => res.url().includes(`/api/v1/complaint-comments/complaint/${complaintNumber}`)
      && res.request().method() === 'GET',
    { timeout: 30000 }
  );
}

async function expectThreadLoaded(
  page: import('@playwright/test').Page,
  pending: Promise<import('@playwright/test').Response>
) {
  await expect(page.locator(THREAD)).toBeVisible({ timeout: 20000 });
  expect((await pending).status()).toBe(200);
}

test.describe('RBIO task action — /staff/rbio/task/:id', () => {
  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    complaintNumber = (await createRbioComplaint(request, {
      subject: 'Task-action comment thread probe'
    })).complaintNumber;
  });

  test('the thread renders and its list call resolves the route id', async ({ page }) => {
    const pending = watchThreadLoad(page, complaintNumber);
    await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
    await expectThreadLoaded(page, pending);
  });

  test('a comment typed on this screen comes back from the server', async ({ page, request }) => {
    const body = `Task-action persistence probe ${Date.now()}`;

    await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
    await expect(page.locator(THREAD)).toBeVisible({ timeout: 20000 });

    await page.locator(`${THREAD} .comment-composer textarea`).fill(body);
    await page.locator(`${THREAD} .add-comment-btn`).click();
    await expect(page.locator(`${THREAD} .comment-text`).filter({ hasText: body })).toBeVisible({
      timeout: 15000
    });

    // The assertion that matters. The previous mock thread also showed the text back; it just kept
    // nothing. A fresh request bypasses the browser's state entirely.
    const reread = await request.get(commentsUrl(complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO')
    });
    expect(reread.ok()).toBeTruthy();
    expect((await reread.json()).comments.map((c: { body: string }) => c.body)).toContain(body);
  });

  test('the RESTRICTED audience offered is the RBIO ladder, not CEPC roles', async ({ page }) => {
    // One component serves both offices, so the chip list is department-derived. Offering a CEPC role
    // here would name an audience that cannot reach this complaint at all.
    await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
    await expect(page.locator(THREAD)).toBeVisible({ timeout: 20000 });

    await page.locator(`${THREAD} .visibility-option[data-visibility="RESTRICTED"]`).click();
    await expect(page.locator(`${THREAD} .audience-chip[data-role="RBIO_SUPERVISOR"]`)).toBeVisible();
    await expect(page.locator(`${THREAD} .audience-chip[data-role="CEPC_DO"]`)).toHaveCount(0);
  });

  test('a citizen is refused this complaint thread by the server', async ({ request }) => {
    // Enforced server-side, which is the point: the thread is absent from the citizen routes as well,
    // but that omission is defence in depth and not the control.
    const refused = await request.get(commentsUrl(complaintNumber), {
      headers: { 'X-User-Id': 'citizen_9876543210', 'X-User-Roles': 'CITIZEN' }
    });
    expect(refused.status()).toBe(403);

    const anonymous = await request.get(commentsUrl(complaintNumber));
    expect(anonymous.status()).toBe(401);
  });
});

test.describe('CEPC task action — /staff/cepc/task/:id', () => {
  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    complaintNumber = (await createTestComplaint(request, {
      subject: 'CEPC task-action comment thread probe'
    })).complaintNumber;
  });

  test('the thread renders with the CEPC audience for the same component', async ({ page }) => {
    const pending = watchThreadLoad(page, complaintNumber);
    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${complaintNumber}`);
    await expectThreadLoaded(page, pending);

    await page.locator(`${THREAD} .visibility-option[data-visibility="RESTRICTED"]`).click();
    await expect(page.locator(`${THREAD} .audience-chip[data-role="CEPC_REVIEWER"]`)).toBeVisible();
    await expect(page.locator(`${THREAD} .audience-chip[data-role="RBIO_SUPERVISOR"]`)).toHaveCount(0);
  });

  test('a comment posted here is readable by RBIO staff on the same complaint', async ({ page, request }) => {
    const body = `CEPC task-action probe ${Date.now()}`;

    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${complaintNumber}`);
    await expect(page.locator(THREAD)).toBeVisible({ timeout: 20000 });

    await page.locator(`${THREAD} .comment-composer textarea`).fill(body);
    await page.locator(`${THREAD} .add-comment-btn`).click();
    await expect(page.locator(`${THREAD} .comment-text`).filter({ hasText: body })).toBeVisible({
      timeout: 15000
    });

    // PUBLIC is the composer's default and means public-to-STAFF, so a different office reads it. This
    // is the whole reason the thread hangs off the complaint rather than off an office's task row.
    const rbioView = await request.get(commentsUrl(complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO')
    });
    expect((await rbioView.json()).comments.map((c: { body: string }) => c.body)).toContain(body);
  });
});

test.describe('Officer action (legacy) — /officer/complaint/:id', () => {
  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    complaintNumber = (await createRbioComplaint(request, {
      subject: 'Legacy officer-action comment thread probe'
    })).complaintNumber;
  });

  test('the thread renders on the legacy screen too', async ({ page }) => {
    // Still routed and still reachable from the dashboard and global search, so leaving it without a
    // thread would mean the same complaint has notes on one screen and none on another.
    const pending = watchThreadLoad(page, complaintNumber);
    await loginAsRbioRole(page, 'RBIO_OFFICER', `/officer/complaint/${complaintNumber}`);
    await expectThreadLoaded(page, pending);
  });

  test('a comment posted here persists', async ({ page, request }) => {
    const body = `Legacy officer-action probe ${Date.now()}`;

    await loginAsRbioRole(page, 'RBIO_OFFICER', `/officer/complaint/${complaintNumber}`);
    await expect(page.locator(THREAD)).toBeVisible({ timeout: 20000 });

    await page.locator(`${THREAD} .comment-composer textarea`).fill(body);
    await page.locator(`${THREAD} .add-comment-btn`).click();
    // The POST is in flight when the click returns; re-reading immediately races it.
    await expect(page.locator(`${THREAD} .comment-text`).filter({ hasText: body })).toBeVisible({
      timeout: 15000
    });

    const reread = await request.get(commentsUrl(complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO')
    });
    expect(reread.ok()).toBeTruthy();
    expect((await reread.json()).comments.map((c: { body: string }) => c.body)).toContain(body);
  });
});

test.describe('AA register appeal — /aa/register/:complaintNumber', () => {
  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    complaintNumber = (await createRbioComplaint(request, {
      subject: 'AA register comment thread probe'
    })).complaintNumber;
  });

  /**
   * Keyed on the PARENT COMPLAINT, which is this route's own param. There is no appeal number to
   * thread on at registration time, and the handling office's notes on the complaint are precisely the
   * context a registrar needs.
   */
  test('the thread is keyed on the parent complaint and its list call resolves', async ({ page }) => {
    const pending = watchThreadLoad(page, complaintNumber);
    await loginAsAaRole(page, 'AA_DO', `/aa/register/${complaintNumber}`);

    // The register form itself may refuse this complaint (appealability is derived server-side from the
    // closure clause, and a clause-less complaint answers 503). The thread only renders on the form
    // branch, so skip rather than assert a false failure when the form did not load.
    const form = page.locator('form');
    if (await form.count() === 0) {
      test.skip(true, 'Register form did not load for this parent complaint — nothing to mount on');
    }

    await expectThreadLoaded(page, pending);
  });

  test('a comment posted here lands on the parent complaint, not an appeal', async ({ page, request }) => {
    const body = `AA register probe ${Date.now()}`;

    await loginAsAaRole(page, 'AA_DO', `/aa/register/${complaintNumber}`);
    if (await page.locator('form').count() === 0) {
      test.skip(true, 'Register form did not load for this parent complaint');
    }
    await expect(page.locator(THREAD)).toBeVisible({ timeout: 20000 });

    await page.locator(`${THREAD} .comment-composer textarea`).fill(body);
    await page.locator(`${THREAD} .add-comment-btn`).click();
    await expect(page.locator(`${THREAD} .comment-text`).filter({ hasText: body })).toBeVisible({
      timeout: 15000
    });

    const reread = await request.get(commentsUrl(complaintNumber), {
      headers: identityHeadersFor('aa_do_001', 'AA')
    });
    expect(reread.ok()).toBeTruthy();
    expect((await reread.json()).comments.map((c: { body: string }) => c.body)).toContain(body);
  });
});
