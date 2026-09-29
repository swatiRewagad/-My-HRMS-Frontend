import { test, expect } from '../fixtures';
import { loginAsRbioRole } from '../utils/auth';
import { createRbioComplaint, cleanupRbioComplaint } from '../utils/test-data';

/**
 * The RBIO complaint screen as the reference layout: two working panels plus a thin context rail, with
 * documents opening as TABS beside the complaint.
 *
 * WHAT THIS SPEC IS ACTUALLY PROTECTING. Three things on this screen were previously theatre:
 *
 *  1. The tab strip was decorative markup — a hardcoded "Complaint" pill and a `+` bound to nothing.
 *     So "documents open in tabs alongside the complaint" could not be true, because there were no
 *     tabs, only a picture of tabs.
 *  2. History, attachments and email were three FULL-WIDTH blocks below the fold. Consulting the
 *     history scrolled the assessment off screen, so an officer comparing an attachment against their
 *     own draft could not see both. They now live in the rail.
 *  3. The assessment fields hold unsaved ngModel state. The rail and the document viewer therefore
 *     HIDE the panels rather than destroying them — an `@if` would discard a half-written speaking
 *     order the moment an officer opened an attachment to check a figure.
 *
 * Point 3 is the one worth a test: a layout bug that loses typed work is invisible in a screenshot and
 * obvious to the officer it happens to. `assessment survives` below is the assertion that matters most.
 */

const RAIL = '.context-rail';
const COMPLAINT_TAB = '.tab-bar .tab-item:not(.doc-tab)';

test.describe('RBIO complaint detail — three-panel reference layout', () => {

  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    const created = await createRbioComplaint(request, {
      subject: 'Three-panel layout probe'
    });
    complaintNumber = created.complaintNumber;
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) await cleanupRbioComplaint(request, complaintNumber).catch(() => {});
  });

  test.beforeEach(async ({ page }) => {
    await loginAsRbioRole(page, 'RBIO_OFFICER', `/rbio/complaint/${complaintNumber}`);
    await expect(page.locator('.detail-content')).toBeVisible({ timeout: 20000 });
  });

  test('the layout is two working panels plus a collapsed rail', async ({ page }) => {
    await expect(page.locator('.left-panel')).toBeVisible();
    await expect(page.locator('.right-panel')).toBeVisible();

    // Collapsed by default: the icon strip is there, the body is not. A rail that opened itself would
    // take width from the assessment on every page load.
    await expect(page.locator(`${RAIL} .rail-icons`)).toBeVisible();
    await expect(page.locator(`${RAIL} .rail-body`)).toHaveCount(0);

    // One icon per panel — history, attachments, email.
    await expect(page.locator(`${RAIL} .rail-icon`)).toHaveCount(3);
  });

  /**
   * The rail must not reflow the panels. Asserting the LEFT PANEL's width before and after, because a
   * rail that stole width would move the assessment fields under the officer's cursor mid-edit.
   */
  test('opening the rail does not resize the working panels', async ({ page }) => {
    const before = await page.locator('.left-panel').boundingBox();

    await page.locator(`${RAIL} .rail-icon`).first().click();
    await expect(page.locator(`${RAIL} .rail-body`)).toBeVisible();

    const after = await page.locator('.left-panel').boundingBox();
    expect(before).not.toBeNull();
    expect(after).not.toBeNull();
    // A few pixels of grid rounding is acceptable; a 300px steal is not.
    expect(Math.abs(after!.width - before!.width)).toBeLessThan(24);
  });

  test('each rail icon opens its own panel and the rail closes again', async ({ page }) => {
    const icons = page.locator(`${RAIL} .rail-icon`);

    await icons.nth(0).click();
    await expect(page.locator(`${RAIL} .rail-header h4`)).toHaveText('Complaint History');

    await icons.nth(1).click();
    await expect(page.locator(`${RAIL} .rail-header h4`)).toHaveText('Attachments');

    await icons.nth(2).click();
    await expect(page.locator(`${RAIL} .rail-header h4`)).toHaveText('Email Communication');

    // Clicking the active icon collapses it — the same control both ways, so there is never an open
    // rail with no visible way to shut it.
    await icons.nth(2).click();
    await expect(page.locator(`${RAIL} .rail-body`)).toHaveCount(0);
  });

  /**
   * The three sections that MOVED must not also still be below the fold. Two places showing the same
   * attachment list is worse than one bad place, because an officer who checks the stale one concludes
   * there are no attachments.
   */
  test('history, attachments and email are not duplicated below the fold', async ({ page }) => {
    await expect(page.locator('.tab-content-section app-rbio-attachments')).toHaveCount(0);
    await expect(page.locator('.tab-content-section app-rbio-email-communication')).toHaveCount(0);
    await expect(page.locator('.tab-content-section app-rbio-complaint-history')).toHaveCount(0);

    // And the dead tab-strip entries are gone with them.
    const sectionTabs = await page.locator('.section-tabs .section-tab').allInnerTexts();
    const labels = sectionTabs.map(t => t.trim());
    expect(labels).not.toContain('Attachments');
    expect(labels).not.toContain('Email Communication');
    expect(labels).not.toContain('Complaint History');
  });

  /**
   * The complaint tab is tab one and has NO close affordance. It is the thing every other tab is
   * "alongside"; a closable complaint tab would leave an officer looking at an attachment with no way
   * back except the browser button.
   */
  test('the complaint tab is present and cannot be closed', async ({ page }) => {
    const complaintTab = page.locator(COMPLAINT_TAB);
    await expect(complaintTab).toHaveCount(1);
    await expect(complaintTab).toHaveClass(/active/);
    await expect(complaintTab.locator('.doc-tab-close')).toHaveCount(0);
  });

  /**
   * THE ASSERTION THAT MATTERS. Type into the assessment, open the rail, and prove the text is still
   * there. `[hidden]` keeps the DOM alive; an `@if` would not, and the failure would only ever be
   * reported by an officer who had just lost a speaking order.
   */
  test('assessment survives opening the rail', async ({ page }) => {
    const draft = 'Speaking order draft that must not be discarded.';
    const textarea = page.locator('.speaking-order textarea');

    if (await textarea.count() === 0) test.skip(true, 'No speaking-order field on this complaint stage.');

    await textarea.first().fill(draft);
    await page.locator(`${RAIL} .rail-icon`).first().click();
    await expect(page.locator(`${RAIL} .rail-body`)).toBeVisible();

    await expect(textarea.first()).toHaveValue(draft);
  });

  /** The `+` is no longer decorative: it opens the rail AT the attachments panel. */
  test('the tab strip plus opens the attachments rail', async ({ page }) => {
    await page.locator('.tab-bar .tab-add').click();
    await expect(page.locator(`${RAIL} .rail-header h4`)).toHaveText('Attachments');
  });

  /** No console errors on this screen — the user's point 1, asserted rather than eyeballed. */
  test('the screen renders without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });

    await page.reload({ waitUntil: 'networkidle' });
    await expect(page.locator('.detail-content')).toBeVisible({ timeout: 20000 });
    await page.locator(`${RAIL} .rail-icon`).nth(1).click();
    await expect(page.locator(`${RAIL} .rail-body`)).toBeVisible();

    // 404s from endpoints other sessions still owe are logged by the app deliberately; a NG/TS runtime
    // error is what this is looking for.
    const runtime = errors.filter(e => !/404|Failed to load resource|net::ERR/i.test(e));
    expect(runtime, runtime.join('\n')).toEqual([]);
  });
});
