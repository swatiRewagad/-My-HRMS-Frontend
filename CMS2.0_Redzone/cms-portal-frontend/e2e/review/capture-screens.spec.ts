/**
 * Walks every screen in screen-registry.ts as the right role and captures a full-page screenshot,
 * writing a manifest the HTML report is built from.
 *
 * This is a REVIEW tool, not a test: it asserts nothing about correctness. It records, per screen,
 * where the browser actually ended up and what the page contained, so that a screen which silently
 * redirected to a login page is visibly reported as such rather than reviewed as if it were the
 * feature. Failing to capture is recorded, never thrown — one bad screen must not end the crawl.
 *
 * Run: e2e/review/capture-screens.sh
 */
import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';
import {
  loginAsCepcRole,
  loginAsRbioRole,
  loginAsAaRole,
  loginAsReRole,
  isKeycloakAvailable,
} from '../utils/auth';
import { SCREENS, type Actor, type ScreenDef } from './screen-registry';
import * as fs from 'fs';
import * as path from 'path';

const OUT_DIR = process.env['REVIEW_DIR'] || 'review-screens';
const SHOTS_DIR = path.join(OUT_DIR, 'shots');
const APP_BASE = (process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4202').replace(/\/+$/, '');
const API_BASE = (process.env['API_BASE_URL'] || 'http://localhost:8092').replace(/\/+$/, '');
const CITIZEN_MOBILE = process.env['REVIEW_CITIZEN_MOBILE'] || '9876512345';

interface Capture {
  id: string;
  area: string;
  name: string;
  actor: Actor;
  routeTemplate: string;
  resolvedRoute: string | null;
  url: string;
  finalUrl: string;
  shot: string | null;
  status: 'CAPTURED' | 'REDIRECTED' | 'BLOCKED' | 'ERROR';
  detail: string;
  title: string;
  headings: string[];
  visibleText: number;
  consoleErrors: string[];
  capturedAt: string;
  /** Set only for `openFirst` screens: whether the panel was actually opened before the shot. */
  interaction?: string;
}

const captures: Capture[] = [];

/** Resolves :params from live backend data once, so every screen uses real records. */
let resolved: {
  complaintNumber?: string;
  appealNumber?: string;
  draftId?: string;
  ruleId?: string;
  closedComplaintNumber?: string;
  token?: string;
} = {};

// NOT 'serial': under serial mode the first screen to time out marks every later screen as
// "did not run", so a single slow page silently truncates the review. A previous crawl lost 47 of
// 73 screens that way. These tests share no state — each one authenticates its own page — so they
// are independent and a failure must stay local to its own screen.
test.describe.configure({ mode: 'default', retries: 0 });

test.beforeAll(async ({ request }) => {
  fs.mkdirSync(SHOTS_DIR, { recursive: true });

  const recent = await request.get(`${API_BASE}/api/v1/complaints/recent?limit=5`).catch(() => null);
  if (recent?.ok()) {
    const body = await recent.json().catch(() => null);
    resolved.complaintNumber = body?.data?.[0]?.complaintNumber;
  }
  const appeals = await request
    .get(`${API_BASE}/api/v1/appeals?page=0&size=1`, {
      headers: { 'X-User-Roles': 'AA_DO', 'X-User-Id': 'aa_do_001' },
    })
    .catch(() => null);
  if (appeals?.ok()) {
    const body = await appeals.json().catch(() => null);
    const list = body?.data?.content || body?.data || body?.content;
    resolved.appealNumber = Array.isArray(list) ? list[0]?.appealNumber : undefined;
  }

  // The draft queue is the only list endpoint for drafts; there is no GET /drafts.
  const queue = await request
    .get(`${API_BASE}/api/v1/email-syndication/queue?page=0&size=5`, {
      headers: { 'X-User-Roles': 'ADMIN', 'X-User-Id': 'cms.admin' },
    })
    .catch(() => null);
  if (queue?.ok()) {
    const body = await queue.json().catch(() => null);
    const list = body?.data?.content || body?.data;
    resolved.draftId = Array.isArray(list) ? list[0]?.draftId : undefined;
  }

  const rules = await request
    .get(`${API_BASE}/api/v1/rules?page=0&size=1`, {
      headers: { 'X-User-Roles': 'ADMIN', 'X-User-Id': 'cms.admin' },
    })
    .catch(() => null);
  if (rules?.ok()) {
    const body = await rules.json().catch(() => null);
    const list = body?.data?.content || body?.data;
    const id = Array.isArray(list) ? list[0]?.id : undefined;
    resolved.ruleId = id === undefined || id === null ? undefined : String(id);
  }

  // A CLOSED complaint is reviewed separately because CitizenStageMapper is lossy: 13 internal
  // statuses, two of them closed, collapse to "Stage 1 — Registered". That is only visible on a
  // complaint that has actually reached a terminal status.
  // /api/v1/complaints is the CITIZEN endpoint and answers SESSION_EXPIRED to a staff identity;
  // the staff view of terminal-status complaints is the workflow service's completed list.
  const closed = await request
    .get(`${API_BASE}/api/v1/workflow/rbio/completed?page=0&size=1`, {
      headers: { 'X-User-Roles': 'RBIO_ADMIN', 'X-User-Id': 'cms.admin' },
    })
    .catch(() => null);
  if (closed?.ok()) {
    const body = await closed.json().catch(() => null);
    const list = body?.data?.content || body?.data;
    resolved.closedComplaintNumber = Array.isArray(list) ? list[0]?.complaintNumber : undefined;
  }

  console.log(`[review] resolved params: ${JSON.stringify(resolved)}`);
});

function resolveRoute(screen: ScreenDef): { route: string | null; missing?: string } {
  let route = screen.route;
  for (const need of screen.needs || []) {
    const value = (resolved as Record<string, string | undefined>)[need];
    if (!value) return { route: null, missing: need };
    route = route.replace(`:${need}`, encodeURIComponent(value));
  }
  return { route };
}

async function authenticate(page: import('@playwright/test').Page, screen: ScreenDef, route: string) {
  switch (screen.actor) {
    case 'PUBLIC':
      await page.goto(`${APP_BASE}${route}`, { waitUntil: 'domcontentloaded' });
      return;
    case 'CITIZEN':
      // sessionStorage is origin-scoped, so the origin must be loaded before seeding.
      await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });
      await seedCitizenSession(page, CITIZEN_MOBILE, 'e2e-review-token.sig');
      await page.goto(`${APP_BASE}${route}`, { waitUntil: 'domcontentloaded' });
      return;
    case 'CEPC_DO':
      return loginAsCepcRole(page, 'DO', route);
    case 'CEPC_REVIEWER':
      return loginAsCepcRole(page, 'REVIEWER', route);
    case 'CEPC_INCHARGE':
      return loginAsCepcRole(page, 'INCHARGE', route);
    case 'CEPC_CA':
      return loginAsCepcRole(page, 'CA', route);
    case 'ADMIN':
      return loginAsCepcRole(page, 'ADMIN', route);
    case 'RBIO_OFFICER':
      return loginAsRbioRole(page, 'RBIO_OFFICER', route);
    case 'RBIO_SUPERVISOR':
      return loginAsRbioRole(page, 'RBIO_SUPERVISOR', route);
    case 'RBIO_ADMIN':
      return loginAsRbioRole(page, 'RBIO_ADMIN', route);
    case 'AA_DO':
      return loginAsAaRole(page, 'AA_DO', route);
    case 'AA_ADMIN':
      return loginAsAaRole(page, 'AA_ADMIN', route);
    case 'RE_PNO':
      return loginAsReRole(page, 'RE_PNO', route);
  }
}

for (const screen of SCREENS) {
  test(`${screen.id} — ${screen.area} — ${screen.name}`, async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 300));
    });
    page.on('pageerror', (err) => consoleErrors.push(`pageerror: ${String(err).slice(0, 300)}`));

    const { route, missing } = resolveRoute(screen);
    const record: Capture = {
      id: screen.id,
      area: screen.area,
      name: screen.name,
      actor: screen.actor,
      routeTemplate: screen.route,
      resolvedRoute: route,
      url: route ? `${APP_BASE}${route}` : '',
      finalUrl: '',
      shot: null,
      status: 'ERROR',
      detail: '',
      title: '',
      headings: [],
      visibleText: 0,
      consoleErrors: [],
      capturedAt: new Date().toISOString(),
    };

    if (!route) {
      record.status = 'BLOCKED';
      record.detail = `No live data to fill :${missing} — screen not reachable in this environment.`;
      captures.push(record);
      writeManifest();
      return;
    }

    if (screen.actor !== 'PUBLIC' && screen.actor !== 'CITIZEN') {
      const kcUp = await isKeycloakAvailable(page);
      if (!kcUp) {
        record.status = 'BLOCKED';
        record.detail = 'Keycloak unreachable — staff screens cannot be authenticated.';
        captures.push(record);
        writeManifest();
        return;
      }
    }

    try {
      await authenticate(page, screen, route);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(screen.settle ?? 1500);

      // Collapsed-by-default panels: click them open so the deck shows their CONTENT. A miss is
      // recorded on the capture, not thrown — a selector that stopped matching is exactly the kind of
      // drift the review should surface, and failing here would lose the screenshot entirely.
      if (screen.openFirst) {
        const target = page.locator(screen.openFirst).first();
        try {
          await target.waitFor({ state: 'visible', timeout: 5000 });
          await target.click();
          // These panels fetch their contents on open, so the shot must wait for that fetch — an
          // earlier version paused 600ms and captured "Loading attachments…", which a BA would
          // reasonably read as a broken panel rather than as a screenshot taken too early.
          await page.waitForLoadState('networkidle').catch(() => {});
          await page.waitForTimeout(1200);
          record.interaction = `opened: ${screen.openFirst}`;
        } catch {
          record.interaction = `NOT OPENED — selector did not match: ${screen.openFirst}`;
        }
      }

      record.finalUrl = page.url();
      record.title = await page.title().catch(() => '');
      record.headings = await page
        .locator('h1, h2')
        .allInnerTexts()
        .then((h) => h.map((t) => t.trim()).filter(Boolean).slice(0, 6))
        .catch(() => []);
      const bodyText = await page.locator('body').innerText().catch(() => '');
      record.visibleText = bodyText.trim().length;

      const shotName = `${screen.id}.png`;
      await page.screenshot({ path: path.join(SHOTS_DIR, shotName), fullPage: true });
      record.shot = `shots/${shotName}`;

      // A screen that bounced to a login/unauthorized page must not be reviewed as the feature.
      const landedElsewhere =
        !record.finalUrl.includes(route.split('?')[0]) &&
        /\/staff\/login|\/public\/login|\/crpc\/login|\/re-portal\/login|\/staff\/unauthorized|\/realms\//.test(
          record.finalUrl
        );
      if (landedElsewhere) {
        record.status = 'REDIRECTED';
        record.detail = `Requested ${route} but landed on ${new URL(record.finalUrl).pathname} — not authenticated or not authorised.`;
      } else if (record.visibleText < 40) {
        record.status = 'REDIRECTED';
        record.detail = `Rendered almost nothing (${record.visibleText} chars of text) — likely a failed guard or a load error.`;
      } else if (record.interaction?.startsWith('NOT OPENED')) {
        // The name promises an open panel. Captured-but-closed would be a deck that lies, so it is
        // reported as a problem rather than filed as a good capture.
        record.status = 'REDIRECTED';
        record.detail = `Panel could not be opened, so this shot is the RESTING state, not the state this entry names. ${record.interaction}`;
      } else {
        record.status = 'CAPTURED';
        record.detail = '';
      }
    } catch (err) {
      record.status = 'ERROR';
      record.detail = String(err).split('\n')[0].slice(0, 400);
      record.finalUrl = page.url();
      try {
        const shotName = `${screen.id}.png`;
        await page.screenshot({ path: path.join(SHOTS_DIR, shotName), fullPage: true });
        record.shot = `shots/${shotName}`;
      } catch {
        /* a screenshot of a dead page is not always possible */
      }
    }

    record.consoleErrors = consoleErrors.slice(0, 5);
    captures.push(record);
    writeManifest();

    // Record, never fail: the crawl must complete so the review covers every screen.
    expect(true).toBe(true);
  });
}

function writeManifest() {
  fs.writeFileSync(
    path.join(OUT_DIR, 'manifest.json'),
    JSON.stringify({ generatedAt: new Date().toISOString(), appBase: APP_BASE, apiBase: API_BASE, resolved, captures }, null, 2)
  );
}

test.afterAll(() => {
  writeManifest();
  const by = (s: string) => captures.filter((c) => c.status === s).length;
  console.log(
    `[review] ${captures.length} screens: ${by('CAPTURED')} captured, ${by('REDIRECTED')} redirected, ${by('BLOCKED')} blocked, ${by('ERROR')} error`
  );
});
