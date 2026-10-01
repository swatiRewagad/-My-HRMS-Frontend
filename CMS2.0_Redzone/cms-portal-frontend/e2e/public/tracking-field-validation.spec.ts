import { test, expect } from '../fixtures';

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA PASS 4 — SESSION A — manual blocks 60-62 (lines 4177-4266, 25 cases)
 * GAP-ONLY spec. Per §6 of the brief these blocks were a VERIFICATION task, not an automation task:
 * the tracking surface is already owned by `tracking-table.spec.ts` (UST100/FR-G-031, 9 tests) and
 * `tracking-authz.spec.ts` (UST98/UST99/UST105, 19 tests). 22 of the 25 manual cases map onto those —
 * the mapping table is in `docs/prompts/findings-QA-A.md` §7.
 *
 * Only the three cases below have no existing counterpart. They are the reference-number SEARCH BOX on
 * `/public/track` — the anonymous path — which the two existing specs exercise for authorization and
 * masking but never for field validation.
 *
 * DELIBERATELY NOT DUPLICATED HERE: the complaint list columns, row-click-to-detail, the empty state,
 * login-free access for a session holder, invalid-OTP wording, and every authz case. Re-asserting them
 * would double the runtime of a shared suite and create two places to update when the table changes.
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 */

const SEARCH = '.search-box input';
// NOT `button:has-text("Track")` — that also matches the "Track by Complaint ID" MODE TAB, and `.first()`
// picks the tab, so every click silently switches mode instead of searching. Scope to the search box.
const TRACK_BTN = '.search-box button';

test.describe('QA-A60 — the anonymous reference-number search box (manual 4177-4266)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/public/track', { waitUntil: 'domcontentloaded' });
    await expect(page.locator(SEARCH)).toBeVisible({ timeout: 20000 });
  });

  // 4249-4252 — "the user is not able to track the complaint using invalid complaint number".
  test('an unknown complaint number is refused with the product\'s own message', async ({ page }) => {
    await page.locator(SEARCH).fill('CMS-20260101-NOPE99');
    await page.locator(TRACK_BTN).click();

    // component.ts:183 — 404 maps to this exact wording.
    await expect(page.locator('text=/not found, please check and try again/i'),
      'an unknown reference number produced no message').toBeVisible({ timeout: 20000 });
  });

  // ═══ A-C18, now fixed: a BLANK complaint number is refused WITH A MESSAGE ═══
  // 4253-4256 expects "the user is not able to track ... using blank complaint number". That always held,
  // but silently: `track()` opened with a bare `if (!id) return;` and the button was additionally
  // `[disabled]="!searchId() || loading()"`, so nothing was searched AND nothing was said. Standardised
  // with the login screen (A-C8): only `loading()` disables, and the emptiness is explained.
  test('a blank complaint number is refused with a message, and nothing is searched', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', (r) => {
      if (/\/api\/v1\/complaints\//.test(r.url())) requests.push(r.url());
    });

    const input = page.locator(SEARCH);
    await expect(input).toHaveValue('');
    await expect(page.locator(TRACK_BTN),
      'the Track button is still disabled on a blank value rather than explaining it').toBeEnabled();
    await page.locator(TRACK_BTN).click();
    await expect(page.locator('.error-message')).toContainText(/complaint number is required/i);

    // Whitespace only is equivalent: `track()` trims before the emptiness check.
    await input.fill('   ');
    await page.locator(TRACK_BTN).click();
    await expect(page.locator('.error-message')).toContainText(/complaint number is required/i);

    expect(requests, 'a blank complaint number was sent to the server').toHaveLength(0);
  });

  // ═══ A-C19: no client-side format check on the reference number ═══
  // 4232-4235 and 4241-4248 together say a CASE ID alone must not be trackable, only a complaint number.
  // The box applies NO client-side format check: whatever is typed goes straight to the server and the
  // distinction rests entirely on whether a row comes back. That is an acceptable design — the server is
  // the authority on which numbers exist — so this test pins the behaviour rather than failing it.
  //
  // A-D6 IS RETRACTED. An earlier reading of this file claimed the tracker called a by-ID handler taking
  // a numeric Long and therefore 404'd for every input. That was wrong, and the mistake is worth naming
  // because the repo has TWO complaint controllers on confusingly similar paths:
  //   ComplaintController        → /api/complaints      (has getById(Long) and track/{complaintNumber})
  //   ComplaintApiV1Controller   → /api/v1/complaints   (has @GetMapping("/{complaintNumber}"), a String)
  // The frontend uses the v1 one, whose handler resolves via getByComplaintNumber, masks PII for a
  // non-owner and audits TRACK_VIEWED. There is no /api/v1/complaints/track/{n} at all. The 404s that
  // prompted the false diagnosis came from a stale JVM on 8092 predating the seeded rows; a restarted
  // backend returns 200 with masked PII for N202627013001025.
  test('any string is accepted into the box and sent to the server — no FR-G-024 format check',
    async ({ page }) => {
      const requests: string[] = [];
      page.on('request', (r) => {
        if (/\/api\/v1\/complaints\//.test(r.url())) requests.push(r.url());
      });

      // A plausible CASE ID rather than a complaint number — the 4241-4248 distinction.
      await page.locator(SEARCH).fill('CASE-00012345');
      await page.locator(TRACK_BTN).click();

      await expect.poll(() => requests.length, { timeout: 20000 }).toBeGreaterThan(0);
      expect(requests[0],
        'the value was normalised or rejected before the request — a format check may have been added')
        .toContain('CASE-00012345');

      // The v1 detail handler, which is the correct one: it takes a String complaint number.
      expect(requests[0], 'the tracker no longer calls the v1 complaint-number handler')
        .toContain('/api/v1/complaints/CASE-00012345');

      // And the citizen is told only that it was not found.
      await expect(page.locator('text=/not found, please check and try again/i')).toBeVisible();
    });
});
