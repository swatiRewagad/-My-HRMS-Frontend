/**
 * Shared E2E fixtures for the parallel AA sessions (S2A / S2B / S2C) and for S3.
 *
 * WHY THIS EXISTS: these helpers were written and proven in aa-foundation-security.spec.ts during
 * S1. Three sessions now need the same primitives — mint a real token, seed a closed parent
 * complaint, run SQL, clean up. Re-deriving them per session guarantees three subtly different
 * versions, and the differences are exactly where silent test bugs live: a wrong entity_code shape
 * or a missing lowercase status makes a query match nothing, so the assertion passes while testing
 * nothing at all.
 *
 * OWNERSHIP: this file is SHARED. Treat it as read-only unless you are fixing an outright bug in it.
 * Session-specific helpers belong in your own spec file. If you need a change here, make it additive
 * (a new exported function), never a change to an existing signature — another session is running
 * against it right now.
 *
 * SEED ISOLATION: every session must pass its OWN prefix ('S2A-', 'S2B-', 'S2C-'). All seeding and
 * all cleanup is scoped by it, so the three sessions share cms_db without deleting each other's
 * fixtures. cms_db is PERSISTENT and holds ~1,300 real complaints — never truncate a table.
 */
import { APIRequestContext, expect } from '@playwright/test';
import { execFileSync } from 'child_process';

export const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
export const KEYCLOAK_BASE = process.env['KEYCLOAK_BASE_URL'] || 'http://localhost:9090';
export const MYSQL_CLI =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

/**
 * Entity fixtures.
 *
 * COMPLAINTS.entity_code stores the NORMALISED ENTITY NAME ('HDFC Bank'), NOT a code like
 * 'HDFC0001' — RePortalService resolves entities by normalised name, and the live table holds
 * values of that shape. re_pno_001's JWT claim is 'HDFC Bank' to match. A code-shaped fixture
 * matches no row, so an entity-scoping test would return an empty list and PASS while proving
 * nothing. This cost real debugging time in S1; do not "tidy" these into codes.
 */
export const RE_OWN_ENTITY = 'HDFC Bank';
export const RE_OTHER_ENTITY = 'State Bank of India';

/** Clause codes seeded in CLOSURE_CLAUSE_MASTER for RBIOS_2021, by appealability. */
export const CLAUSE = {
  /** Appealable by a COMPLAINANT only — an entity may not appeal this. */
  complainantOnly: '15(1)(a)',
  /** Appealable by BOTH a complainant and a regulated entity. */
  bothParties: '15(1)(b)',
  /** Appealable by neither — any escalation is a REPRESENTATION. */
  neither: '16(2)(a)',
  /** Deliberately absent from CLOSURE_CLAUSE_MASTER, to exercise the fail-closed path. */
  unmapped: '99(9)(z)',
} as const;

/** Runs a statement against cms_db. utf8mb4 is mandatory or native scripts come back as "?". */
export function sql(statement: string): string {
  return execFileSync(
    MYSQL_CLI,
    ['-u', 'cms_user', '-pcms_pass', 'cms_db', '--default-character-set=utf8mb4', '-N', '-B', '-e', statement],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  ).trim();
}

/** Escapes a value for inline SQL. Fixture data only — not a general-purpose sanitiser. */
function esc(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

export interface SeedComplaintOptions {
  closureClause?: string | null;
  entityCode?: string;
  /** Lowercase, as persisted in COMPLAINTS: 'closed', 'forwarded', 'in_progress'. */
  status?: string;
  /** Set to 'REOPENED' to make a complaint match the closed-or-reopened search. */
  workflowStage?: string | null;
  complainantName?: string;
  complainantEmail?: string;
  complainantPhone?: string;
  categoryId?: number | null;
}

/**
 * Inserts a parent complaint directly.
 *
 * Direct INSERT rather than driving the CEPC close workflow: the closure clause is the entire input
 * to AA classification, and the CEPC path will not accept an arbitrary clause — least of all one
 * absent from the Clause Master, which is the fail-closed case that most needs testing.
 *
 * NOTE status is stored LOWERCASE in COMPLAINTS ('closed'), while the ComplaintStatus enum is
 * uppercase. Match the data, not the enum.
 */
export function seedComplaint(complaintNumber: string, options: SeedComplaintOptions = {}): void {
  const {
    closureClause = null,
    entityCode = RE_OWN_ENTITY,
    status = 'closed',
    workflowStage = null,
    complainantName = 'AA Fixture Complainant',
    complainantEmail = 'aafixture@example.com',
    complainantPhone = '9876543210',
    categoryId = null,
  } = options;

  const clause = closureClause === null ? 'NULL' : `'${esc(closureClause)}'`;
  const stage = workflowStage === null ? 'NULL' : `'${esc(workflowStage)}'`;
  const category = categoryId === null ? 'NULL' : String(categoryId);
  const closedAt = status === 'closed' ? 'NOW()' : 'NULL';

  sql(`INSERT INTO COMPLAINTS
        (complaint_number, complainant_name, complainant_email, complainant_phone,
         subject, description, status, workflow_stage, closure_clause,
         entity_code, entity_name, category_id,
         priority, filing_type, scheme_version,
         created_at, updated_at, filed_at, closed_at)
       VALUES
        ('${esc(complaintNumber)}', '${esc(complainantName)}', '${esc(complainantEmail)}',
         '${esc(complainantPhone)}',
         'AA fixture complaint', 'Seeded by an AA E2E suite via aa-shared-fixtures',
         '${esc(status)}', ${stage}, ${clause},
         '${esc(entityCode)}', 'Fixture Entity', ${category},
         'MEDIUM', 'CEPC_MANUAL', 'RBIOS_2021',
         NOW(), NOW(), NOW(), ${closedAt})`);
}

/** Convenience: a closed parent with a given clause — the commonest AA fixture. */
export function seedClosedComplaint(
  complaintNumber: string,
  closureClause: string | null,
  entityCode: string = RE_OWN_ENTITY
): void {
  seedComplaint(complaintNumber, { closureClause, entityCode, status: 'closed' });
}

/**
 * A REOPENED parent.
 *
 * Reopen is recorded as workflow_stage='REOPENED' with the status moved back to in_progress — it is
 * NOT a status value, so "closed or reopened" cannot be expressed as status IN (...) alone.
 */
export function seedReopenedComplaint(
  complaintNumber: string,
  closureClause: string,
  entityCode: string = RE_OWN_ENTITY
): void {
  seedComplaint(complaintNumber, {
    closureClause,
    entityCode,
    status: 'in_progress',
    workflowStage: 'REOPENED',
  });
}

/**
 * Deletes everything a session seeded, in FK-safe order.
 *
 * Call in beforeAll (so a crashed previous run cannot poison this one) AND in afterAll. Scoped by
 * prefix so it can never touch another session's fixtures or the real data.
 */
export function purgeByPrefix(prefix: string): void {
  const p = esc(prefix);
  sql(`DELETE FROM appeal_timeline WHERE appeal_number IN
         (SELECT appeal_number FROM appeals WHERE original_complaint_number LIKE '${p}%')`);
  sql(`DELETE FROM appeal_attachments WHERE appeal_number IN
         (SELECT appeal_number FROM appeals WHERE original_complaint_number LIKE '${p}%')`);
  sql(`DELETE FROM appeals WHERE original_complaint_number LIKE '${p}%'`);
  sql(`DELETE FROM COMPLAINTS WHERE complaint_number LIKE '${p}%'`);
}

/** A real Keycloak access token, so the JWT-first resolution paths are exercised for real. */
export async function mintToken(
  request: APIRequestContext,
  username: string,
  password = 'test123'
): Promise<string> {
  const res = await request.post(`${KEYCLOAK_BASE}/realms/cms/protocol/openid-connect/token`, {
    form: { client_id: 'cms-portal', username, password, grant_type: 'password' },
  });
  expect(res.status(), `Keycloak must issue a token for ${username} — is it running on 9090 with provision-aa-roles.sh applied?`).toBe(200);
  const body = await res.json();
  expect(body.access_token, `token payload for ${username}`).toBeTruthy();
  return body.access_token as string;
}

/** Decodes a JWT payload without verifying it — for asserting which claims a token carries. */
export function decodeClaims(token: string): Record<string, unknown> {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf8'));
}

export function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

/**
 * Asserts the backend under test is up AND admits the given identity.
 *
 * Without this a suite whose backend is down reports every test as a broken assertion rather than a
 * missing server, which sends you debugging the wrong thing.
 */
export async function assertBackendReady(
  request: APIRequestContext,
  identity: Record<string, string>
): Promise<void> {
  const probe = await request.get(`${API_BASE}/api/v1/appeals/stats`, {
    headers: identity,
    failOnStatusCode: false,
  });
  expect(
    probe.status(),
    `backend at ${API_BASE} must be up and admit this identity — start it with ./deployment/run-test-backend.sh <port>`
  ).toBe(200);
}

/**
 * Flushes the Hazelcast translation cache.
 *
 * There is no dedicated evict endpoint: POST /api/v1/i18n/translations is upsertTranslation and
 * needs a {code, locale, value} body. Re-upserting an existing key triggers its
 * @CacheEvict(allEntries=true), which clears both translation caches. A bare POST evicts nothing and
 * returns 4xx/500 — the project preamble is wrong on this point.
 */
export async function evictTranslationCache(request: APIRequestContext): Promise<void> {
  const current = await request.get(`${API_BASE}/api/v1/i18n/translations/en`, { failOnStatusCode: false });
  if (!current.ok()) return;
  const body = await current.json();
  const map = (body?.data && typeof body.data === 'object' ? body.data : body) as Record<string, string>;
  const [code, value] = Object.entries(map)[0] ?? [];
  if (!code) return;
  await request.post(`${API_BASE}/api/v1/i18n/translations`, {
    data: { code, locale: 'en', value },
    headers: { 'Content-Type': 'application/json' },
    failOnStatusCode: false,
  });
}

export const TEN_LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'] as const;

/**
 * Fetches one locale's translation map.
 *
 * TranslationController returns the map either bare or under `data` depending on the route, so this
 * normalises both rather than making each caller guess.
 */
export async function fetchTranslations(
  request: APIRequestContext,
  locale: string
): Promise<Record<string, string>> {
  const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`, { failOnStatusCode: false });
  expect(res.status(), `translations for ${locale}`).toBe(200);
  const body = await res.json();
  return (body?.data && typeof body.data === 'object' ? body.data : body) as Record<string, string>;
}

/**
 * Asserts every key resolves in all ten locales AND that the nine non-English values are not just
 * the English string copied through.
 *
 * The second half is the part that matters: an unseeded key falls back to English, so a
 * presence-only check passes on a completely untranslated feature.
 */
export async function assertKeysTranslatedInAllLocales(
  request: APIRequestContext,
  keys: string[]
): Promise<void> {
  const en = await fetchTranslations(request, 'en');
  for (const locale of TEN_LOCALES) {
    const map = await fetchTranslations(request, locale);
    for (const key of keys) {
      expect(map[key], `${key} missing in locale ${locale}`).toBeTruthy();
      if (locale !== 'en') {
        expect(
          map[key],
          `${key} in ${locale} is an English fallback, not a translation`
        ).not.toBe(en[key]);
      }
    }
  }
}
