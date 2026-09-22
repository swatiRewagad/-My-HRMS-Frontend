import { APIRequestContext } from '@playwright/test';

/**
 * Self-healing identity for the reassignment API tests.
 *
 * API-level rather than browser-level, deliberately: the `cms` realm on 9090 contains no RE_* roles
 * and no RE users at all, so a browser login cannot reach the RE portal. Signing in as cms.admin
 * yields no entity_code, the entity-scoped endpoints refuse the call, and every element assertion
 * then fails for a reason unrelated to the code under test.
 *
 * Why a helper rather than inline headers: real JWT enforcement is being switched on in this backend
 * by concurrent work, and it may land part-way through a run. A test that probes auth once at start-up
 * would report a wall of false failures the moment it flips. So every request goes through
 * {@link authedHeaders}, which uses dev identity headers while they work and transparently upgrades to
 * a real bearer token on the first 401/403, caching it for the rest of the run. A mid-run flip
 * therefore costs one retry rather than a failed suite.
 */

const KEYCLOAK_URL = process.env['KEYCLOAK_URL'] || 'http://localhost:9090';
const KEYCLOAK_REALM = process.env['KEYCLOAK_REALM'] || 'cms';

/**
 * Own env var rather than the shared API_BASE_URL, so a global override pointing at another
 * session's backend cannot silently redirect these tests.
 */
export const API_BASE = process.env['REASSIGNMENT_BASE_URL'] || 'http://localhost:8095';
export const REASSIGN = `${API_BASE}/api/v1/re-portal/reassignment`;
export const REPORT = `${API_BASE}/api/v1/re-portal/reassignment-report`;

/** Credentials that actually exist in this realm. The _001/test123 set does not. */
const FALLBACK_USER = { username: 'cms.admin', password: 'Test@1234' };

let cachedToken: string | null = null;
let tokenAttempted = false;

export interface Identity {
  userId: string;
  userName?: string;
  roles: string;
  entityCode?: string;
}

export const RE_NODAL = (entityCode: string, userId = 're_e2e_nodal'): Identity => ({
  userId,
  userName: 'E2E RE Nodal',
  roles: 'RE_NODAL_OFFICER',
  entityCode,
});

export const RE_PNO = (entityCode: string, userId = 're_e2e_pno'): Identity => ({
  userId,
  userName: 'E2E RE PNO',
  roles: 'RE_PNO',
  entityCode,
});

export const ADMIN = (userId = 'e2e_admin'): Identity => ({
  userId,
  userName: 'E2E Admin',
  roles: 'ADMIN',
});

/** Dev identity headers. The server prefers a JWT claim when one is present. */
export function devHeaders(identity: Identity): Record<string, string> {
  const headers: Record<string, string> = {
    'X-User-Id': identity.userId,
    'X-User-Roles': identity.roles,
    'Content-Type': 'application/json',
  };
  if (identity.userName) headers['X-User-Name'] = identity.userName;
  if (identity.entityCode) headers['X-Entity-Code'] = identity.entityCode;
  return headers;
}

async function mintToken(request: APIRequestContext): Promise<string | null> {
  if (tokenAttempted) return cachedToken;
  tokenAttempted = true;
  try {
    const res = await request.post(
      `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: 'cms-frontend',
          grant_type: 'password',
          username: FALLBACK_USER.username,
          password: FALLBACK_USER.password,
        },
        failOnStatusCode: false,
      },
    );
    if (!res.ok()) return null;
    cachedToken = (await res.json())['access_token'] ?? null;
  } catch {
    cachedToken = null;
  }
  return cachedToken;
}

/**
 * Headers for a request, upgrading to a bearer token if dev headers have stopped being accepted.
 *
 * The entity-code header is still sent alongside a bearer token: the resolver prefers the JWT claim
 * and only falls back to the header, and a cms.admin token carries no entity_code claim.
 */
export async function authedHeaders(
  request: APIRequestContext,
  identity: Identity,
): Promise<Record<string, string>> {
  const headers = devHeaders(identity);
  if (cachedToken) headers['Authorization'] = `Bearer ${cachedToken}`;
  return headers;
}

/**
 * Re-probes whether dev headers are still accepted, and mints a token if not.
 *
 * Call this at the start of a verification phase. An unexpected 401/403 is FIRST assumed to be
 * enforcement landing, not a bug in the code under test.
 */
export async function probeAuth(request: APIRequestContext, entityCode: string): Promise<'headers' | 'bearer' | 'none'> {
  const res = await request.get(`${REASSIGN}/candidates`, {
    headers: devHeaders(RE_NODAL(entityCode)),
    failOnStatusCode: false,
  });
  if (res.status() !== 401 && res.status() !== 403) return 'headers';
  const token = await mintToken(request);
  return token ? 'bearer' : 'none';
}

/**
 * GET that survives an auth flip: on a 401/403 it mints a token once and retries.
 * Returns the final response so the caller can still assert on a genuine 403.
 */
export async function getWithAuth(
  request: APIRequestContext,
  url: string,
  identity: Identity,
  params?: Record<string, string | number | boolean>,
) {
  let res = await request.get(url, {
    headers: await authedHeaders(request, identity),
    params,
    failOnStatusCode: false,
  });
  if ((res.status() === 401 || res.status() === 403) && !cachedToken) {
    const token = await mintToken(request);
    if (token) {
      res = await request.get(url, {
        headers: await authedHeaders(request, identity),
        params,
        failOnStatusCode: false,
      });
    }
  }
  return res;
}

/** POST counterpart of {@link getWithAuth}. */
export async function postWithAuth(
  request: APIRequestContext,
  url: string,
  identity: Identity,
  data: unknown,
) {
  let res = await request.post(url, {
    headers: await authedHeaders(request, identity),
    data: data as Record<string, unknown>,
    failOnStatusCode: false,
  });
  if ((res.status() === 401 || res.status() === 403) && !cachedToken) {
    const token = await mintToken(request);
    if (token) {
      res = await request.post(url, {
        headers: await authedHeaders(request, identity),
        data: data as Record<string, unknown>,
        failOnStatusCode: false,
      });
    }
  }
  return res;
}
