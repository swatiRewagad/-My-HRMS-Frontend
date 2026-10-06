import { test, expect } from '../fixtures';
import { loginAsRbioRole } from '../utils/auth';
import { createRbioComplaint, cleanupRbioComplaint, identityHeadersFor } from '../utils/test-data';

/**
 * The assistance rail (Brief 21 §5.1) — the bulb, and the six states behind it.
 *
 * WHAT THIS SPEC IS ACTUALLY PROTECTING. The rail's whole value is that an officer can TRUST it, and
 * three distinct ways of breaking that trust are invisible in a screenshot:
 *
 *  1. **A bulb that glows with nothing behind it.** The brief names this as the one failure mode that
 *     makes the feature worthless rather than merely imperfect — officers stop looking. `glow` is
 *     therefore derived server-side and ANDed client-side with "something survived dismissal"; it may
 *     never be recomputed from a list length. Asserted below as: dismiss everything, bulb goes dark.
 *  2. **Empty, unavailable and FAILED collapsed into one "nothing found".** That exact collapse hid a
 *     404 endpoint for months on the sibling similar-cases panel. So the states carry distinct
 *     testids and distinct ARIA roles, and this spec asserts the ROLE as well as the text: `empty` is
 *     `role=status` (the rail ran and has nothing to say), `failed` is `role=alert` (the rail did not
 *     run, so nothing is known). A test that only proves signals APPEAR would miss this entirely.
 *  3. **Assistance shouting about its own failure.** §5.1 requires the rail to fail SILENTLY — the
 *     deliberate opposite of the user-initiated search path, which must say so. A rail that threw a
 *     toast or blocked the screen would be a defect, so `a rail failure leaves the screen usable`
 *     asserts the screen still works with the endpoint dead.
 *
 * WHY THE ROUTE-LEVEL ABORT IS THE MECHANISM. Both rail endpoints always answer HTTP 200 on purpose
 * (GlobalExceptionHandler maps a bare RuntimeException to 400, which would present a server failure as
 * a client error on a valid request). So the server CANNOT produce the `failed` state, and the only
 * honest way to reach it is to break the transport — hence `page.route(...).abort()` rather than
 * trying to coax a 500 out of the backend.
 *
 * SEEDING NOTE. A freshly created complaint legitimately has an EMPTY rail: tier 0 is this officer's
 * own continuity (nothing yet) and the tier-1 priors are gated — `category-closure-time` needs
 * MIN_CLOSURE_SAMPLE=5 closed cases in the category. That is why the signal-bearing tests seed a
 * tier-0 memory row through the API first rather than expecting signals to exist by magic. The
 * `complainantEmail` is reused deliberately in one test so `complainant-history` can fire: it gates on
 * an exact email match with `> 0` others, no sample floor, because it is a verifiable COUNT and not a
 * statistical inference.
 */

const RAIL = '[data-testid="assistance-rail"]';
const BULB = '[data-testid="assistance-bulb"]';

/** Distinct per spec run: AntiAutomationFilter throttles at 100 req/60s per client IP. */
const XFF = { 'X-Forwarded-For': `10.33.${Math.floor(Math.random() * 250) + 1}.${Math.floor(Math.random() * 250) + 1}` };

const OFFICER = 'rbio.officer';

test.describe('Assistance rail — the bulb and its states', () => {

  let complaintNumber: string;

  test.beforeAll(async ({ request }) => {
    const created = await createRbioComplaint(request, {
      subject: 'Assistance rail probe'
    });
    complaintNumber = created.complaintNumber;
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) await cleanupRbioComplaint(request, complaintNumber).catch(() => {});
  });

  /**
   * Writes a tier-0 memory row AS THE OFFICER THE BROWSER WILL LOG IN AS.
   *
   * The identity match is the whole point and the easiest thing to get wrong: tier 0 is keyed on the
   * server-resolved principal, so a row written as any other user yields a rail that is correctly
   * empty — which reads exactly like a broken feature. `X-User-*` are honoured here only because the
   * harness backend runs dev-local; the enforcing profile ignores them and takes identity from the JWT.
   */
  async function rememberVisit(
    request: Parameters<typeof createRbioComplaint>[0],
    section: string,
    draftText: string | null
  ) {
    const res = await request.put('/api/v1/assistance/rail/memory', {
      headers: {
        'Content-Type': 'application/json',
        ...identityHeadersFor(OFFICER, 'RBIO'),
        'X-User-Id': OFFICER,
        ...XFF
      },
      data: { complaintNumber, section, draftText }
    });
    expect(res.status()).toBe(200);
    return res;
  }

  test('the rail answers 200 and reports its own glow verdict', async ({ request }) => {
    const res = await request.get('/api/v1/assistance/rail', {
      params: { complaintId: complaintNumber },
      headers: { ...identityHeadersFor(OFFICER, 'RBIO'), 'X-User-Id': OFFICER, ...XFF }
    });

    expect(res.status()).toBe(200);
    const body = await res.json();

    // glow is true IFF signals is non-empty. Asserting the INVARIANT rather than a fixed value,
    // because a shared dev DB makes the specific signal set unstable while this rule must always hold:
    // it is what stops "glowing" and "has something to say" from drifting apart.
    expect(body).toHaveProperty('glow');
    expect(body).toHaveProperty('signals');
    expect(body.glow).toBe(body.signals.length > 0);
    expect(body.count).toBe(body.signals.length);
  });

  test('a tier-0 memory write is visible to its author and to nobody else', async ({ request }) => {
    await rememberVisit(request, 'Take Action — FORWARD_TO_RE', 'half-written remarks for the rail');

    const mine = await request.get('/api/v1/assistance/rail', {
      params: { complaintId: complaintNumber },
      headers: { ...identityHeadersFor(OFFICER, 'RBIO'), 'X-User-Id': OFFICER, ...XFF }
    });
    const mineBody = await mine.json();
    const myKinds = mineBody.signals.map((s: { kind: string }) => s.kind);

    // The officer's own unsaved text comes back. This is the signal the feature exists for.
    expect(myKinds).toContain('unsaved-draft');
    expect(mineBody.glow).toBe(true);

    // ── Tier 0 ISOLATION ──
    // A different officer must NOT see the first officer's unsaved draft. This is the negative half,
    // and it is the assertion that matters: the rail is a read-amplification surface over text an
    // officer has not saved, and a feature that leaked it would be a PII defect, not a UX bug.
    const theirs = await request.get('/api/v1/assistance/rail', {
      params: { complaintId: complaintNumber },
      headers: {
        ...identityHeadersFor('rbio.supervisor', 'RBIO'),
        'X-User-Id': 'rbio.supervisor',
        ...XFF
      }
    });
    const theirKinds = (await theirs.json()).signals.map((s: { kind: string }) => s.kind);
    expect(theirKinds).not.toContain('unsaved-draft');
    expect(theirKinds).not.toContain('last-section');
  });

  test.describe('in the browser', () => {

    test.beforeEach(async ({ request, page }) => {
      // Seed before login so the rail has something to say on first read.
      await rememberVisit(request, 'Take Action — FORWARD_TO_RE', 'half-written remarks for the rail');
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
    });

    /**
     * The bulb glows BEFORE anything is opened — which is the entire interaction. It is also the part
     * that was missing for longest: the panel used to read only on open, so glow could not be known
     * while closed and the bulb was decorative.
     */
    test('the bulb glows without the panel being opened', async ({ page }) => {
      const bulb = page.locator(BULB);
      await expect(bulb).toBeVisible({ timeout: 20000 });

      // Lit, per the server's verdict.
      await expect(bulb).toHaveClass(/assistance-bulb--glow/, { timeout: 20000 });

      // Glow is NOT the only signal — §5.1 requires a non-colour cue for WCAG and reduced-motion.
      await expect(bulb).toHaveAttribute('data-signal-count', /[1-9]/);

      // The panel itself is still closed: a host that only probes pays no DOM.
      await expect(page.locator(RAIL)).toHaveCount(0);
    });

    test('opening the bulb shows the officer their own unsaved text', async ({ page }) => {
      await page.locator(BULB).click();

      const rail = page.locator(RAIL);
      await expect(rail).toBeVisible();
      await expect(page.locator('[data-testid="assistance-signals"]')).toBeVisible();

      // Rendered verbatim and never translated: it is the officer's own words, not server prose.
      await expect(rail).toContainText('half-written remarks for the rail');

      // Escape closes it, and focus was moved into the panel on open so Escape has somewhere to
      // bubble from.
      await page.keyboard.press('Escape');
      await expect(page.locator(RAIL)).toHaveCount(0);
    });

    /**
     * ── THE ASSERTION THAT MATTERS MOST ──
     *
     * Dismiss every signal and the bulb must go DARK. A bulb still glowing over a rail whose every row
     * the officer has already dismissed is the "trains officers to ignore it" failure the brief names,
     * and it is invisible in a screenshot. §5.1 also requires that it never re-glow for the same
     * payload, which is asserted by closing and re-probing.
     */
    test('dismissing every signal puts the bulb out, and it does not come back', async ({ page }) => {
      await page.locator(BULB).click();
      await expect(page.locator(RAIL)).toBeVisible();

      const dismissals = page.locator('[data-testid^="assistance-dismiss-"]');
      const total = await dismissals.count();
      expect(total).toBeGreaterThan(0);

      // Always dismiss the FIRST remaining one: each click removes a row, so indexing into a stale
      // list would go out of bounds.
      for (let i = 0; i < total; i++) {
        await dismissals.first().click();
      }

      // A fourth state, distinct from `empty` on the same reasoning that keeps `empty` distinct from
      // `failed`: the rail DID run and did have something to say, and the officer silenced it.
      await expect(page.locator('[data-testid="assistance-dismissed"]')).toBeVisible();

      const bulb = page.locator(BULB);
      await expect(bulb).not.toHaveClass(/assistance-bulb--glow/);

      // Closing and reopening must not resurrect them — dismissal lasts the session on this screen.
      await page.keyboard.press('Escape');
      await expect(page.locator(RAIL)).toHaveCount(0);
      await expect(bulb).not.toHaveClass(/assistance-bulb--glow/);

      await bulb.click();
      await expect(page.locator('[data-testid="assistance-dismissed"]')).toBeVisible();
    });

    /**
     * §5.1: assistance failing must be SILENT and must leave the screen fully usable. The route abort
     * is the only honest way to produce this, since the endpoints always answer 200 by design.
     */
    test('a rail failure is silent and leaves the screen fully usable', async ({ page, context }) => {
      await context.route('**/api/v1/assistance/rail?*', route => route.abort());

      await page.reload({ waitUntil: 'domcontentloaded' });

      // The screen itself still works. This is the real requirement — assistance is off the critical
      // path, so a dead rail may not take the task screen with it.
      await expect(page.locator('app-workflow-action-bar, .action-bar, .task-action')).toBeVisible({ timeout: 20000 });

      // Nothing shouts. No toast, no error banner, and the bulb does not claim to have content.
      await expect(page.locator('.toast, .p-toast, [role="alertdialog"]')).toHaveCount(0);
      await expect(page.locator(BULB)).not.toHaveClass(/assistance-bulb--glow/);

      // But if the officer DOES open it, the panel is honest about not having run — role=alert, and
      // distinct from "nothing to report". Collapsing these two is the defect this spec exists for.
      await page.locator(BULB).click();
      const failed = page.locator('[data-testid="assistance-error"]');
      await expect(failed).toBeVisible();
      await expect(failed).toHaveAttribute('role', 'alert');
      await expect(page.locator('[data-testid="assistance-empty"]')).toHaveCount(0);
    });
  });

  /**
   * The empty state, on a complaint nobody has touched: `role=status`, not `role=alert`. This is the
   * common and CORRECT answer — most complaints legitimately have nothing to say — and it must not be
   * dressed up as a problem.
   */
  test('an untouched complaint reports empty as a status, not an error', async ({ request, page }) => {
    const fresh = await createRbioComplaint(request, { subject: 'Assistance rail empty-state probe' });

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${fresh.complaintNumber}`);

      const bulb = page.locator(BULB);
      await expect(bulb).toBeVisible({ timeout: 20000 });
      // Dark, because there is genuinely nothing to say. A bulb that glowed here would be the defect.
      await expect(bulb).not.toHaveClass(/assistance-bulb--glow/);

      await bulb.click();
      const empty = page.locator('[data-testid="assistance-empty"]');
      await expect(empty).toBeVisible();
      await expect(empty).toHaveAttribute('role', 'status');
      await expect(page.locator('[data-testid="assistance-error"]')).toHaveCount(0);
    } finally {
      await cleanupRbioComplaint(request, fresh.complaintNumber).catch(() => {});
    }
  });
});
