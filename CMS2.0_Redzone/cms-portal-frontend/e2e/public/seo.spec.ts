// From '../fixtures', NOT '@playwright/test': the fixture installs redirectBrowserApiCalls, which
// points the PAGE's own fetches at API_BASE_URL. Without it the app keeps using its compiled
// environment.apiBaseUrl (8082), a DIFFERENT backend from the one this run seeds and asserts
// against (API_BASE_URL, e.g. 8092), so the i18n bundle and the FAQ rows are read from the wrong
// database — or from nothing, if no JVM happens to be listening there. The title then stays at its
// untranslated placeholder and the FAQPage structured-data block is built from an empty list,
// which read as three product defects.
import { test, expect } from '../fixtures';
import { readFileSync } from 'fs';
import { join } from 'path';

/**
 * SEO: per-route metadata, hreflang, the generated sitemap, and robots.txt coverage.
 *
 * <h2>The baseline these lock down</h2>
 *
 * <p>Before this work the portal had: ZERO calls to Title.setTitle or Meta.updateTag (all 84 routes
 * shared one static English title), zero JSON-LD, a hand-maintained 6-URL sitemap that advertised an
 * auth-guarded page while omitting the FAQ, and a robots.txt missing /cepc/, /rbio/, /aa/,
 * /email-syndication/ and — most seriously — /public/upload/, whose URL token IS the credential.
 *
 * <h2>Why robots.txt is checked against app.routes.ts</h2>
 *
 * <p>Asserting robots.txt against a hardcoded list in this test would only prove the file matches the
 * test. The real requirement is that it matches the ROUTER, so the route table is parsed and every
 * staff prefix is required to be disallowed. That is what catches the next staff module someone adds.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const SRC = join(__dirname, '..', '..', 'src');

/** Prefixes that are deliberately public. Everything else must be disallowed. */
const PUBLIC_PREFIXES = ['public', 'track', 'login', ''];

function topLevelRoutePaths(): string[] {
  const src = readFileSync(join(SRC, 'app', 'app.routes.ts'), 'utf8');
  // Top-level entries only: two-space indented objects in the exported array.
  const blocks = src.match(/^ {2}\{[\s\S]*?^ {2}\},/gm) ?? [];
  return blocks
    .map(b => (b.match(/path:\s*'([^']*)'/) ?? [])[1])
    .filter((p): p is string => typeof p === 'string');
}

test.describe('SEO — robots.txt matches the router', () => {

  test('every non-public top-level route prefix is disallowed', () => {
    const robots = readFileSync(join(SRC, 'robots.txt'), 'utf8');
    const disallowed = robots
      .split('\n')
      .filter(l => l.startsWith('Disallow:'))
      .map(l => l.replace('Disallow:', '').trim());

    const uncovered: string[] = [];
    for (const path of topLevelRoutePaths()) {
      if (path === '' || path === '**' || path.startsWith(':')) continue;
      const prefix = path.split('/')[0];
      if (PUBLIC_PREFIXES.includes(prefix)) continue;
      const covered = disallowed.some(d => {
        const bare = d.replace(/^\//, '').replace(/\/$/, '');
        return prefix === bare || path.startsWith(bare);
      });
      if (!covered) uncovered.push(path);
    }

    expect(uncovered, `these routes are crawlable but should not be:\n${uncovered.join('\n')}`)
      .toEqual([]);
  });

  test('the tokenised upload link and both complaint forms are disallowed', () => {
    const robots = readFileSync(join(SRC, 'robots.txt'), 'utf8');

    // The upload token IS the credential — an indexed URL is a working upload link for someone
    // else's complaint, which is why this is asserted by name rather than left to the prefix scan.
    expect(robots).toContain('Disallow: /public/upload/');
    expect(robots).toContain('Disallow: /public/file-complaint');
    // A second, older top-level route to a complaint form that the previous robots.txt missed.
    expect(robots).toContain('Disallow: /file-complaint');
  });

  test('robots.txt declares the sitemap', () => {
    expect(readFileSync(join(SRC, 'robots.txt'), 'utf8')).toMatch(/^Sitemap: https?:\/\/.+\/sitemap\.xml$/m);
  });
});

test.describe('SEO — the generated sitemap', () => {

  test('lists only indexable routes, and no auth-guarded page', () => {
    const sitemap = readFileSync(join(SRC, 'sitemap.xml'), 'utf8');
    const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]);

    expect(locs.length).toBeGreaterThan(0);

    // The previous hand-written sitemap advertised /public/file-complaint at priority 0.9 despite it
    // sitting behind publicAuthGuard.
    for (const guarded of ['/public/file-complaint', '/public/history', '/public/appeal',
                           '/public/feedback', '/public/withdraw', '/public/upload']) {
      expect(locs.filter(l => l.includes(guarded)),
        `${guarded} is auth-guarded and must not be in the sitemap`).toEqual([]);
    }

    // The FAQ is genuinely useful public content and was missing entirely before.
    expect(locs.some(l => l.endsWith('/public/faq'))).toBe(true);
  });

  test('every URL declares all eleven hreflang alternates plus x-default', () => {
    const sitemap = readFileSync(join(SRC, 'sitemap.xml'), 'utf8');
    const blocks = sitemap.match(/<url>[\s\S]*?<\/url>/g) ?? [];
    expect(blocks.length).toBeGreaterThan(0);

    for (const block of blocks) {
      const langs = [...block.matchAll(/hreflang="([^"]+)"/g)].map(m => m[1]);
      const loc = (block.match(/<loc>([^<]+)<\/loc>/) ?? [])[1];
      // 11 locales + x-default. An Indian-language government service that omits these is competing
      // against its own translations as duplicate content.
      expect(langs.length, `${loc} has ${langs.length} hreflang tags`).toBe(12);
      expect(langs).toContain('x-default');
      expect(langs).toContain('pa');
    }
  });

  test('it is generated, not hand-maintained', () => {
    // A hand-edited sitemap drifts from the router. The banner is the signal that it is derived.
    expect(readFileSync(join(SRC, 'sitemap.xml'), 'utf8')).toContain('GENERATED by');
  });
});

test.describe('SEO — per-route metadata in the browser', () => {

  /**
   * Waits until SeoService.apply has run for the current route.
   *
   * Polling page.title() directly races the dev server's first-hit compile and Angular's bootstrap: the
   * tags ARE applied, just later than a 15s poll that starts at domcontentloaded. The canonical link is
   * the last element apply() writes, so its presence means the whole tag set is in place.
   */
  async function waitForSeoApplied(page: import('@playwright/test').Page) {
    await expect.poll(async () => page.locator('link[rel="canonical"]').count(), { timeout: 45000 })
      .toBe(1);
  }

  test('the public home page has a unique, non-default title and a description', async ({ page }) => {
    await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });
    await waitForSeoApplied(page);

    const title = await page.title();
    expect(title.length).toBeGreaterThan(20);
    expect(title).toContain('|');

    const description = await page.locator('meta[name="description"]').getAttribute('content');
    expect(description, 'a page with no description gets no useful search snippet').toBeTruthy();
    expect(description!.length).toBeGreaterThan(50);
  });

  test('the FAQ page title DIFFERS from the home page title', async ({ page }) => {
    // The whole point: one shared title across 84 routes meant a crawler could not tell them apart.
    await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });
    await waitForSeoApplied(page);
    const home = await page.title();

    await page.goto(`${APP_BASE}/public/faq`, { waitUntil: 'domcontentloaded' });
    await expect.poll(async () => page.title(), { timeout: 15000 }).not.toBe(home);

    expect(await page.title()).not.toBe(home);
  });

  test('a canonical link and eleven hreflang alternates are emitted', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/faq`, { waitUntil: 'domcontentloaded' });

    // Poll the canonical's VALUE, not merely its presence. SeoService writes a canonical for whichever
    // route resolved first, so asserting immediately after the element appears can read the previous
    // route's URL while the router is still settling on this one.
    await expect.poll(
      async () => page.locator('link[rel="canonical"]').getAttribute('href'),
      { timeout: 45000 },
    ).toMatch(/\/public\/faq$/);

    // 11 locales + x-default.
    await expect.poll(async () => page.locator('link[rel="alternate"][hreflang]').count(), { timeout: 15000 })
      .toBe(12);
  });

  test('an unlisted per-record URL emits noindex (fail closed)', async ({ page }) => {
    // NOT /public/history: publicAuthGuard redirects that to /public/login, which IS legitimately
    // indexable, so it proves nothing. A per-complaint URL is the real risk — an indexed one confirms
    // that a given reference number exists — and it is absent from the SEO table, so it must fall
    // through to the noindex default.
    await page.goto(`${APP_BASE}/public/complaint/CMP-NO-SUCH-RECORD`, { waitUntil: 'domcontentloaded' });

    // Wait for the ROUTE to settle, not merely for a canonical to exist. publicAuthGuard redirects this
    // URL, and SeoService writes tags for the pre-redirect route first — so a bare
    // waitForSeoApplied() returns on the per-complaint tags and then the assertion races the redirect.
    // Polling the pathname until it stops changing is what makes this deterministic rather than flaky.
    await expect.poll(async () => new URL(page.url()).pathname, { timeout: 45000 })
      .not.toBe('/public/complaint/CMP-NO-SUCH-RECORD');
    await waitForSeoApplied(page);

    const path = new URL(page.url()).pathname;
    const robots = await page.locator('meta[name="robots"]').getAttribute('content');

    if (path.startsWith('/public/complaint/')) {
      expect(robots, 'an unlisted per-record route must default to noindex').toContain('noindex');
    } else {
      // publicAuthGuard redirected. That is the stronger outcome — the per-complaint page never
      // rendered at all — but assert it explicitly so this cannot pass by accident.
      expect(path).toBe('/public/login');
      // And /public/login IS legitimately indexable: it is the page telling people how to sign in.
      expect(robots).toContain('index');
    }
  });

  test('the sitemap and the noindex set agree: no indexable page is disallowed in robots.txt', () => {
    // Cross-check between the two generated artefacts. A URL that is advertised in the sitemap AND
    // blocked by robots.txt is a contradiction that quietly wastes the crawl budget.
    const sitemap = readFileSync(join(SRC, 'sitemap.xml'), 'utf8');
    const robots = readFileSync(join(SRC, 'robots.txt'), 'utf8');
    const disallowed = robots.split('\n')
      .filter(l => l.startsWith('Disallow:'))
      .map(l => l.replace('Disallow:', '').trim());

    for (const loc of [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1])) {
      const path = new URL(loc).pathname;
      const blocked = disallowed.find(d => path === d || path.startsWith(d));
      expect(blocked, `${path} is in the sitemap but Disallow: ${blocked} blocks it`).toBeUndefined();
    }
  });

  test('html lang reflects the active locale', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('cms_locale', 'pa'));
    await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });

    // Screen readers choose a voice from this attribute, and crawlers use it to confirm the language.
    await waitForSeoApplied(page);
    await expect.poll(
      async () => page.locator('html').getAttribute('lang'),
      { timeout: 15000 },
    ).toBe('pa');
  });
});

test.describe('SEO — JSON-LD structured data', () => {

  /** Parses every JSON-LD block on the page, failing loudly on malformed JSON. */
  async function jsonLdBlocks(page: import('@playwright/test').Page) {
    await expect.poll(
      async () => page.locator('script[type="application/ld+json"]').count(),
      { timeout: 45000 },
    ).toBeGreaterThan(0);

    const raw = await page.locator('script[type="application/ld+json"]').allTextContents();
    return raw.map(text => {
      try {
        return JSON.parse(text);
      } catch (e) {
        throw new Error(`invalid JSON-LD emitted: ${String(e)}
${text.slice(0, 200)}`);
      }
    });
  }

  test('the home page emits valid GovernmentOrganization and WebSite+SearchAction', async ({ page }) => {
    await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });
    const blocks = await jsonLdBlocks(page);
    const types = blocks.map(b => b['@type']);

    expect(types).toContain('GovernmentOrganization');
    expect(types).toContain('WebSite');

    const org = blocks.find(b => b['@type'] === 'GovernmentOrganization');
    expect(org['@context']).toBe('https://schema.org');
    expect(org.legalName).toBe('Reserve Bank of India');
    // All eleven locales the portal actually serves. Claiming more is a false signal.
    expect(org.contactPoint.availableLanguage).toHaveLength(11);
    expect(org.contactPoint.availableLanguage).toContain('pa');

    const site = blocks.find(b => b['@type'] === 'WebSite');
    // The SearchAction must target the TRACKER — the portal's only real public search. Pointing it at
    // a page that cannot search produces a search box that silently does nothing.
    expect(site.potentialAction.target.urlTemplate).toContain('/public/track?ref={search_term_string}');
    expect(site.potentialAction['query-input']).toBe('required name=search_term_string');
  });

  test('the home page emits NO breadcrumb, but an inner page does', async ({ page }) => {
    await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });
    let blocks = await jsonLdBlocks(page);
    // A one-item trail tells a crawler nothing it does not already know.
    expect(blocks.map(b => b['@type'])).not.toContain('BreadcrumbList');

    await page.goto(`${APP_BASE}/public/faq`, { waitUntil: 'domcontentloaded' });
    await expect.poll(
      async () => (await page.locator('script[type="application/ld+json"]').allTextContents())
        .some(t => t.includes('BreadcrumbList')),
      { timeout: 45000 },
    ).toBe(true);

    blocks = await jsonLdBlocks(page);
    const crumb = blocks.find(b => b['@type'] === 'BreadcrumbList');
    expect(crumb.itemListElement).toHaveLength(2);
    expect(crumb.itemListElement[0].position).toBe(1);
    expect(crumb.itemListElement[1].item).toMatch(/\/public\/faq$/);
  });

  test('the FAQ page emits a FAQPage built from the live rows, with no raw keys', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/faq`, { waitUntil: 'domcontentloaded' });

    await expect.poll(
      async () => (await page.locator('script[type="application/ld+json"]').allTextContents())
        .some(t => t.includes('FAQPage')),
      { timeout: 45000 },
    ).toBe(true);

    const faq = (await jsonLdBlocks(page)).find(b => b['@type'] === 'FAQPage');
    expect(faq.mainEntity.length).toBeGreaterThan(0);

    for (const q of faq.mainEntity) {
      expect(q['@type']).toBe('Question');
      expect(q.acceptedAnswer['@type']).toBe('Answer');
      // Publishing "faq.q_how_long" to a search engine as though it were a question is the defect the
      // raw-key filter exists to prevent.
      expect(q.name, 'a raw translation key was marked up as a question').not.toMatch(/^faq\./);
      expect(q.acceptedAnswer.text).not.toMatch(/^faq\./);
      expect(q.name.length).toBeGreaterThan(5);
    }

    // Every marked-up question must be VISIBLE on the page — Google treats hidden FAQ markup as a
    // violation, so this asserts the markup describes what the component actually rendered.
    const visible = await page.locator('body').innerText();
    expect(visible).toContain(faq.mainEntity[0].name);
  });

  test('structured data does NOT leak onto a noindex page', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/faq`, { waitUntil: 'domcontentloaded' });
    await expect.poll(
      async () => (await page.locator('script[type="application/ld+json"]').allTextContents())
        .some(t => t.includes('FAQPage')),
      { timeout: 45000 },
    ).toBe(true);

    // These blocks live in <head>, so without explicit clearing they would survive the navigation and
    // describe FAQ content on a page that has none.
    await page.goto(`${APP_BASE}/public/login`, { waitUntil: 'domcontentloaded' });
    await expect.poll(
      async () => (await page.locator('script[type="application/ld+json"]').allTextContents())
        .some(t => t.includes('FAQPage')),
      { timeout: 20000 },
    ).toBe(false);
  });
});
