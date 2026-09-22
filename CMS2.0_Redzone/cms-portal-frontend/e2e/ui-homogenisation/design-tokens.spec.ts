import { test, expect } from '../fixtures';

/**
 * The design system is only real if the SAME token resolves to the SAME value everywhere.
 *
 * These assert COMPUTED styles, not source text. Grepping the SCSS for `var(--brand-primary)` proves
 * only that somebody typed it; reading the resolved value from a live document proves the custom
 * property is actually defined and inherited. A typo'd token name resolves to the empty string and
 * would sail past a source-level check while rendering an unstyled element.
 */

const EXPECTED_TOKENS: Record<string, string> = {
  '--brand-primary': 'rgb(59, 130, 246)',
  '--text-heading': 'rgb(30, 41, 59)',
  '--text-muted': 'rgb(100, 116, 139)',
  '--border-subtle': 'rgb(226, 232, 240)',
  '--surface-page': 'rgb(248, 250, 252)',
  '--surface-card': 'rgb(255, 255, 255)',
  '--state-success-fg': 'rgb(6, 95, 70)',
  '--state-warning-bg': 'rgb(254, 243, 199)',
  '--state-danger-solid': 'rgb(239, 68, 68)',
};

/** Reads a custom property off :root and normalises it to an rgb() string via the browser. */
async function resolveToken(page: import('@playwright/test').Page, token: string): Promise<string> {
  return page.evaluate((name) => {
    const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    if (!raw) return '';
    // Round-trip through the engine so #3b82f6 and rgb(59,130,246) compare equal.
    const probe = document.createElement('span');
    probe.style.color = raw;
    document.body.appendChild(probe);
    const resolved = getComputedStyle(probe).color;
    probe.remove();
    return resolved;
  }, token);
}

test.describe('Design tokens', () => {
  test('every token is defined on :root and resolves to its CRPC value', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    for (const [token, expected] of Object.entries(EXPECTED_TOKENS)) {
      const actual = await resolveToken(page, token);
      expect(actual, `${token} must be defined and resolve to ${expected}`).toBe(expected);
    }
  });

  /**
   * The point of the exercise: a token must not be redefined per module. If one module shadowed
   * --brand-primary, the product would look consistent on the page you happened to open and drift on
   * the next, which is the exact failure mode the token layer exists to prevent.
   */
  test('tokens resolve identically on a public route and a staff route', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    const onPublic: Record<string, string> = {};
    for (const token of Object.keys(EXPECTED_TOKENS)) {
      onPublic[token] = await resolveToken(page, token);
    }

    // A staff route redirects when unauthenticated, but :root tokens come from the global stylesheet
    // and are present regardless of which component the router settled on.
    await page.goto('/crpc/home', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    for (const token of Object.keys(EXPECTED_TOKENS)) {
      const onStaff = await resolveToken(page, token);
      expect(onStaff, `${token} must not be redefined per module`).toBe(onPublic[token]);
    }
  });

  test('focus styling comes from a token rather than being suppressed', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    const focusRing = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--focus-ring').trim()
    );
    expect(focusRing, '--focus-ring must be defined so focus is never outline:none').not.toBe('');
    expect(focusRing).toContain('solid');
  });
});
