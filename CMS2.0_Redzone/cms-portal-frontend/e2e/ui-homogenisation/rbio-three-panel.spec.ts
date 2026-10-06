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

    // One icon per panel. Asserted by TITLE rather than by a count: this spec protects the
    // three-panel LAYOUT, not the size of the rail's inventory, and a bare `toHaveCount(3)` made
    // every future panel a failure here — it broke when the assistance bulb was added, which is a
    // feature landing, not a regression in this screen. Naming the three says what actually matters
    // (these consultation surfaces are in the rail and not full-width below the fold) while leaving
    // the rail extensible.
    const RAIL_ICON = `${RAIL} .rail-icon`;
    for (const title of ['Complaint History', 'Attachments', 'Email Communication']) {
      await expect(page.locator(`${RAIL_ICON}[title="${title}"]`)).toHaveCount(1);
    }

    // Still collapsed: no panel body is open regardless of how many icons the rail offers.
    await expect(page.locator(`${RAIL} .rail-body`)).toHaveCount(0);
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

  // Addressed by TITLE, not by index. The rail's inventory is host-supplied and the assistance panel
  // is appended to it conditionally, so an `nth(1)` here means "whatever is second today" — it would
  // start clicking the wrong panel the moment a panel is inserted ahead of these three, and the
  // failure would read as a broken rail rather than as a stale selector.
  test('each rail icon opens its own panel and the rail closes again', async ({ page }) => {
    const header = page.locator(`${RAIL} .rail-header h4`);
    const icon = (title: string) => page.locator(`${RAIL} .rail-icon[title="${title}"]`);

    for (const title of ['Complaint History', 'Attachments', 'Email Communication']) {
      await icon(title).click();
      await expect(header).toHaveText(title);
    }

    // Clicking the active icon collapses it — the same control both ways, so there is never an open
    // rail with no visible way to shut it.
    await icon('Email Communication').click();
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

  /**
   * The attachments panel must actually LOAD, not merely render.
   *
   * This is the test whose absence let a real defect live: `complaintId` in a complaint payload is the
   * business key (CMP-…), while `/api/files/complaint/{id}` is `@PathVariable Long`. Every caller passed
   * the number, the endpoint answered 400, and the panel showed "could not be loaded" on every complaint
   * since the day it was written. Nothing caught it because the layout tests only asked whether the panel
   * was VISIBLE, and the console-error test filters "Failed to load resource" so that other sessions'
   * missing endpoints do not fail this spec — which is exactly the filter this 400 hid behind.
   *
   * So the assertion is on the error state being ABSENT, not on the request. An empty list is a fine
   * outcome for a fresh complaint; a load failure is not.
   */
  test('the attachments panel loads rather than reporting a load failure', async ({ page }) => {
    const responses: number[] = [];
    page.on('response', r => { if (/\/api\/files\/complaint\/\d+(\?|$)/.test(r.url())) responses.push(r.status()); });

    await page.locator('.tab-bar .tab-add').click();
    await expect(page.locator('app-rbio-attachments .attachments-table')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('app-rbio-attachments .error-state')).toHaveCount(0);

    // A request keyed on the complaint NUMBER never matches the numeric-id pattern above, so an empty
    // list here means the panel called the wrong URL shape rather than that it loaded cleanly.
    expect(responses.length, 'attachments were never requested with a numeric id').toBeGreaterThan(0);
    expect(responses.every(s => s === 200), `statuses: ${responses.join(',')}`).toBe(true);
  });

  /** No console errors on this screen — the user's point 1, asserted rather than eyeballed. */
  test('the screen renders without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });

    await page.reload({ waitUntil: 'networkidle' });
    await expect(page.locator('.detail-content')).toBeVisible({ timeout: 20000 });
    await page.locator(`${RAIL} .rail-icon[title="Attachments"]`).click();
    await expect(page.locator(`${RAIL} .rail-body`)).toBeVisible();

    // 404s from endpoints other sessions still owe are logged by the app deliberately; a NG/TS runtime
    // error is what this is looking for.
    const runtime = errors.filter(e => !/404|Failed to load resource|net::ERR/i.test(e));
    expect(runtime, runtime.join('\n')).toEqual([]);
  });
});
