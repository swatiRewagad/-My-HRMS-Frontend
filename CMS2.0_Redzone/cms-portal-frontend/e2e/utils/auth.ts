import { Page, expect } from '@playwright/test';

/**
 * Keycloak authentication helper for CEPC & RBIO E2E tests.
 *
 * Credentials are read from environment variables:
 *   CEPC_DO_USER / CEPC_DO_PASS            — Dealing Officer
 *   CEPC_REVIEWER_USER / CEPC_REVIEWER_PASS — Reviewer
 *   CEPC_INCHARGE_USER / CEPC_INCHARGE_PASS — In-Charge
 *   CEPC_CA_USER / CEPC_CA_PASS             — Closing Authority
 *   CEPC_ADMIN_USER / CEPC_ADMIN_PASS       — Admin
 *   CEPC_CP_USER / CEPC_CP_PASS             — Contact Person
 *
 *   RBIO_OFFICER_USER / RBIO_OFFICER_PASS         — RBIO Officer
 *   RBIO_SUPERVISOR_USER / RBIO_SUPERVISOR_PASS   — RBIO Supervisor
 *   RBIO_CONCILIATOR_USER / RBIO_CONCILIATOR_PASS — RBIO Conciliator
 *   RBIO_ADJUDICATOR_USER / RBIO_ADJUDICATOR_PASS — RBIO Adjudicator
 *   RBIO_ADMIN_USER / RBIO_ADMIN_PASS             — RBIO Admin
 *
 * Keycloak location:
 *   KEYCLOAK_URL   — default http://localhost:9090
 *   KEYCLOAK_REALM — default cms
 */

/**
 * Single source of truth for the Keycloak base URL and realm.
 *
 * Keycloak for this project runs on port 9090 (see src/environments/environment.ts,
 * which sets keycloakUrl: 'http://localhost:9090' and realm: 'cms').
 * Override with KEYCLOAK_URL / KEYCLOAK_REALM in CI.
 */
export const KEYCLOAK_URL = (process.env['KEYCLOAK_URL'] || 'http://localhost:9090').replace(/\/+$/, '');
export const KEYCLOAK_REALM = process.env['KEYCLOAK_REALM'] || 'cms';
export const KEYCLOAK_REALM_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`;

export type CepcRoleKey = 'DO' | 'REVIEWER' | 'INCHARGE' | 'CA' | 'ADMIN' | 'CP';
export type RbioRoleKey = 'RBIO_OFFICER' | 'RBIO_SUPERVISOR' | 'RBIO_CONCILIATOR' | 'RBIO_ADJUDICATOR' | 'RBIO_ADMIN';
export type ReRoleKey = 'RE_NODAL_OFFICER' | 'RE_PNO';
export type AaRoleKey = 'AA_DO' | 'AA_REVIEWER_1' | 'AA_REVIEWER_2' | 'AA_SECRETARIAT' | 'AA_ADMIN';
export type OrbioRoleKey = 'ORBIO_ADMIN' | 'ORBIO_OFFICER';

interface Credentials {
  username: string;
  password: string;
}

const ENV_MAP: Record<CepcRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  DO: {
    userEnv: 'CEPC_DO_USER',
    passEnv: 'CEPC_DO_PASS',
    defaults: { username: 'cepc_do1', password: 'password' },
  },
  REVIEWER: {
    userEnv: 'CEPC_REVIEWER_USER',
    passEnv: 'CEPC_REVIEWER_PASS',
    defaults: { username: 'cepc_reviewer1', password: 'password' },
  },
  INCHARGE: {
    userEnv: 'CEPC_INCHARGE_USER',
    passEnv: 'CEPC_INCHARGE_PASS',
    defaults: { username: 'cepc_incharge1', password: 'password' },
  },
  CA: {
    userEnv: 'CEPC_CA_USER',
    passEnv: 'CEPC_CA_PASS',
    defaults: { username: 'cepc_closing1', password: 'password' },
  },
  ADMIN: {
    userEnv: 'CEPC_ADMIN_USER',
    passEnv: 'CEPC_ADMIN_PASS',
    defaults: { username: 'cepc_admin1', password: 'password' },
  },
  CP: {
    userEnv: 'CEPC_CP_USER',
    passEnv: 'CEPC_CP_PASS',
    defaults: { username: 'contact_person1', password: 'password' },
  },
};

function getCredentials(role: CepcRoleKey): Credentials {
  const cfg = ENV_MAP[role];
  return {
    username: process.env[cfg.userEnv] || cfg.defaults.username,
    password: process.env[cfg.passEnv] || cfg.defaults.password,
  };
}

async function fillKeycloakForm(page: Page, creds: Credentials): Promise<void> {
  await page.locator('#username').waitFor({ state: 'visible', timeout: 10000 });
  await page.locator('#username').fill(creds.username);
  await page.locator('#password').fill(creds.password);
  await page.locator('#kc-login').click();
}

async function waitForAuthAndNavigate(page: Page, targetUrl: string): Promise<void> {
  // Wait for redirect back to app (no longer on Keycloak)
  await page.waitForFunction(() => !window.location.href.includes('/realms/'), { timeout: 15000 });
  await page.waitForLoadState('networkidle');

  // Give keycloak-js time to process the auth callback (code in hash → token exchange)
  await page.waitForTimeout(3000);

  // Navigate to clean target URL.
  // With silentCheckSsoRedirectUri configured, check-sso uses a hidden iframe
  // instead of a full page redirect, so this navigation is safe.
  await page.goto(targetUrl, { waitUntil: 'networkidle', timeout: 30000 });

  // Wait for the component to finish loading (auth + data)
  await page.waitForTimeout(2000);
}

/**
 * Checks whether Keycloak is reachable.
 * Returns false if the server does not respond within 5 seconds.
 *
 * Probes KEYCLOAK_REALM_URL (default http://localhost:9090/realms/cms).
 * Previously this hardcoded port 8180, which nothing in this project listens on,
 * so every staff suite silently reported "skipped but passing".
 */
export async function isKeycloakAvailable(page: Page): Promise<boolean> {
  try {
    const response = await page.request.get(KEYCLOAK_REALM_URL, {
      timeout: 5000,
    });
    return response.ok();
  } catch {
    return false;
  }
}

/**
 * Logs in via Keycloak SSO redirect flow.
 *
 * 1. Navigate to the target URL (defaults to /cepc/dashboard).
 * 2. If Keycloak redirects to its login page, fill the form and submit.
 * 3. Wait until the app is loaded after redirect back.
 */
export async function loginAsCepcRole(
  page: Page,
  role: CepcRoleKey,
  targetUrl = '/cepc/dashboard'
): Promise<void> {
  const creds = getCredentials(role);

  // Navigate to target — may redirect to Keycloak
  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });

  // Wait a moment for potential redirect to Keycloak
  await page.waitForTimeout(1000);

  const currentUrl = page.url();

  // Check if we were redirected to Keycloak login page
  if (currentUrl.includes('/realms/') || currentUrl.includes('/auth/')) {
    await fillKeycloakForm(page, creds);
  } else if (currentUrl.includes('/staff/login') || currentUrl.includes('/staff')) {
    // The app's own staff login page — click SSO button to redirect to Keycloak
    const loginBtn = page.locator('button:has-text("Sign in"), button:has-text("Login"), a:has-text("Sign in"), a:has-text("Login")');
    await loginBtn.first().waitFor({ state: 'visible', timeout: 10000 });
    await loginBtn.first().click();
    await page.waitForTimeout(2000);

    // Now handle Keycloak form if redirected
    const afterUrl = page.url();
    if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
      await fillKeycloakForm(page, creds);
    }
  }

  await waitForAuthAndNavigate(page, targetUrl);
}

// ────────────────────────────────────────────────────────────────────────────
// RBIO Role Login
// ────────────────────────────────────────────────────────────────────────────

const RBIO_ENV_MAP: Record<RbioRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  RBIO_OFFICER: {
    userEnv: 'RBIO_OFFICER_USER',
    passEnv: 'RBIO_OFFICER_PASS',
    defaults: { username: 'rbio.officer', password: 'Test@1234' },
  },
  RBIO_SUPERVISOR: {
    userEnv: 'RBIO_SUPERVISOR_USER',
    passEnv: 'RBIO_SUPERVISOR_PASS',
    defaults: { username: 'rbio.supervisor', password: 'Test@1234' },
  },
  // NOTE: rbio.conciliator / rbio.adjudicator exist in the realm but return
  // "Account is not fully set up" — they have no usable password credential.
  // CEPC equivalents hold the same conciliator/adjudicator realm roles and do work.
  RBIO_CONCILIATOR: {
    userEnv: 'RBIO_CONCILIATOR_USER',
    passEnv: 'RBIO_CONCILIATOR_PASS',
    defaults: { username: 'cepc.conciliator', password: 'password' },
  },
  RBIO_ADJUDICATOR: {
    userEnv: 'RBIO_ADJUDICATOR_USER',
    passEnv: 'RBIO_ADJUDICATOR_PASS',
    defaults: { username: 'cepc.adjudicator', password: 'password' },
  },
  RBIO_ADMIN: {
    userEnv: 'RBIO_ADMIN_USER',
    passEnv: 'RBIO_ADMIN_PASS',
    defaults: { username: 'cms.admin', password: 'Test@1234' },
  },
};

function getRbioCredentials(role: RbioRoleKey): Credentials {
  const cfg = RBIO_ENV_MAP[role];
  return {
    username: process.env[cfg.userEnv] || cfg.defaults.username,
    password: process.env[cfg.passEnv] || cfg.defaults.password,
  };
}

/**
 * Logs in via Keycloak SSO redirect flow for RBIO roles.
 *
 * 1. Navigate to the target URL (defaults to /staff/rbio/tasks).
 * 2. If Keycloak redirects to its login page, fill the form and submit.
 * 3. Wait until the app is loaded after redirect back.
 */
export async function loginAsRbioRole(
  page: Page,
  role: RbioRoleKey,
  targetUrl = '/staff/rbio/tasks'
): Promise<void> {
  const creds = getRbioCredentials(role);

  // Navigate to target — may redirect to Keycloak
  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });

  // Wait a moment for potential redirect to Keycloak
  await page.waitForTimeout(1000);

  const currentUrl = page.url();

  if (currentUrl.includes('/realms/') || currentUrl.includes('/auth/')) {
    await fillKeycloakForm(page, creds);
  } else if (currentUrl.includes('/staff/login') || currentUrl.includes('/staff')) {
    const loginBtn = page.locator('button:has-text("Sign in"), button:has-text("Login"), a:has-text("Sign in"), a:has-text("Login")');
    await loginBtn.first().waitFor({ state: 'visible', timeout: 10000 });
    await loginBtn.first().click();
    await page.waitForTimeout(2000);

    const afterUrl = page.url();
    if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
      await fillKeycloakForm(page, creds);
    }
  }

  await waitForAuthAndNavigate(page, targetUrl);
}

// ────────────────────────────────────────────────────────────────────────────
// RE (Regulated Entity) Role Login
// ────────────────────────────────────────────────────────────────────────────

const RE_ENV_MAP: Record<ReRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  // A real RE account now exists: `re_pno_001`, seeded by deployment/provision-aa-roles.sh with the
  // RE_PNO realm role and attribute entity_code=HDFC0001.
  //
  // These previously defaulted to `cms.admin`, on the reasoning that /re-portal is guarded only by
  // staffAuthGuard so any authenticated staff account would do. That is no longer true: the RE portal
  // endpoints are entity-scoped, and `cms.admin` carries no RE role and no entity code, so the API
  // returns nothing and the page renders "Complaint not found" — a real-looking UI failure caused
  // purely by signing in as the wrong person.
  //
  // The realm defines RE_PNO but NOT RE_NODAL_OFFICER, so both keys map to the one real RE account.
  // Override per-role once a nodal-officer account is seeded.
  RE_NODAL_OFFICER: {
    userEnv: 'RE_NODAL_USER',
    passEnv: 'RE_NODAL_PASS',
    defaults: { username: 're_pno_001', password: 'test123' },
  },
  RE_PNO: {
    userEnv: 'RE_PNO_USER',
    passEnv: 'RE_PNO_PASS',
    defaults: { username: 're_pno_001', password: 'test123' },
  },
};

function getReCredentials(role: ReRoleKey): Credentials {
  const cfg = RE_ENV_MAP[role];
  return {
    username: process.env[cfg.userEnv] || cfg.defaults.username,
    password: process.env[cfg.passEnv] || cfg.defaults.password,
  };
}

/**
 * Logs in via Keycloak SSO redirect flow for RE (Regulated Entity) roles.
 *
 * 1. Navigate to the target URL (defaults to /re/dashboard).
 * 2. If Keycloak redirects to its login page, fill the form and submit.
 * 3. Wait until the app is loaded after redirect back.
 */
export async function loginAsReRole(
  page: Page,
  role: ReRoleKey,
  targetUrl = '/re-portal/dashboard'
): Promise<void> {
  const creds = getReCredentials(role);

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  const currentUrl = page.url();

  if (currentUrl.includes('/realms/') || currentUrl.includes('/auth/')) {
    await fillKeycloakForm(page, creds);
  } else if (currentUrl.includes('/staff/login') || currentUrl.includes('/re/login') || currentUrl.includes('/staff')) {
    const loginBtn = page.locator('button:has-text("Sign in"), button:has-text("Login"), a:has-text("Sign in"), a:has-text("Login")');
    await loginBtn.first().waitFor({ state: 'visible', timeout: 10000 });
    await loginBtn.first().click();
    await page.waitForTimeout(2000);

    const afterUrl = page.url();
    if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
      await fillKeycloakForm(page, creds);
    }
  }

  await waitForAuthAndNavigate(page, targetUrl);
}

// ────────────────────────────────────────────────────────────────────────────
// AA (Appellate Authority) Role Login
// ────────────────────────────────────────────────────────────────────────────

/**
 * Real AA accounts, provisioned by deployment/provision-aa-roles.sh.
 *
 * These previously all defaulted to `cms.admin` because the realm had no AA_* roles, which meant
 * every AA test authenticated as the same generic staff account and no role-restriction could
 * possibly be exercised. Run the provisioning script if these logins fail.
 *
 * Reviewer 1 vs Reviewer 2 are the SAME role (AA_REVIEWER) distinguished by a `reviewer_tier`
 * claim, because routing has to name a specific reviewer and a role cannot express that.
 */
const AA_ENV_MAP: Record<AaRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  AA_DO: {
    userEnv: 'AA_DO_USER',
    passEnv: 'AA_DO_PASS',
    defaults: { username: 'aa_do_001', password: 'test123' },
  },
  AA_REVIEWER_1: {
    userEnv: 'AA_REVIEWER_1_USER',
    passEnv: 'AA_REVIEWER_1_PASS',
    defaults: { username: 'aa_reviewer_001', password: 'test123' },
  },
  AA_REVIEWER_2: {
    userEnv: 'AA_REVIEWER_2_USER',
    passEnv: 'AA_REVIEWER_2_PASS',
    defaults: { username: 'aa_reviewer_002', password: 'test123' },
  },
  AA_SECRETARIAT: {
    userEnv: 'AA_SECRETARIAT_USER',
    passEnv: 'AA_SECRETARIAT_PASS',
    defaults: { username: 'aa_secretariat_001', password: 'test123' },
  },
  AA_ADMIN: {
    userEnv: 'AA_ADMIN_USER',
    passEnv: 'AA_ADMIN_PASS',
    defaults: { username: 'aa_admin_001', password: 'test123' },
  },
};

/**
 * ORBIO means the Ombudsman from the RBIO module — not a separate office. ORBIO Admin is
 * RBIO_ADMIN and ORBIO officer is RBIO_OFFICER; there is deliberately no ORBIO_* role.
 */
const ORBIO_ENV_MAP: Record<OrbioRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  ORBIO_ADMIN: {
    userEnv: 'ORBIO_ADMIN_USER',
    passEnv: 'ORBIO_ADMIN_PASS',
    defaults: { username: 'orbio_admin_001', password: 'test123' },
  },
  ORBIO_OFFICER: {
    userEnv: 'ORBIO_OFFICER_USER',
    passEnv: 'ORBIO_OFFICER_PASS',
    defaults: { username: 'orbio_officer_001', password: 'test123' },
  },
};

export function getOrbioCredentials(role: OrbioRoleKey): Credentials {
  const cfg = ORBIO_ENV_MAP[role];
  return {
    username: process.env[cfg.userEnv] || cfg.defaults.username,
    password: process.env[cfg.passEnv] || cfg.defaults.password,
  };
}

function getAaCredentials(role: AaRoleKey): Credentials {
  const cfg = AA_ENV_MAP[role];
  return {
    username: process.env[cfg.userEnv] || cfg.defaults.username,
    password: process.env[cfg.passEnv] || cfg.defaults.password,
  };
}

/**
 * Logs in via Keycloak SSO redirect flow for AA (Appellate Authority) roles.
 *
 * 1. Navigate to the target URL (defaults to /aa/dashboard).
 * 2. If Keycloak redirects to its login page, fill the form and submit.
 * 3. Wait until the app is loaded after redirect back.
 */
export async function loginAsAaRole(
  page: Page,
  role: AaRoleKey,
  targetUrl = '/aa/dashboard'
): Promise<void> {
  const creds = getAaCredentials(role);

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  const currentUrl = page.url();

  if (currentUrl.includes('/realms/') || currentUrl.includes('/auth/')) {
    await fillKeycloakForm(page, creds);
  } else if (currentUrl.includes('/staff/login') || currentUrl.includes('/aa/login') || currentUrl.includes('/staff')) {
    const loginBtn = page.locator('button:has-text("Sign in"), button:has-text("Login"), a:has-text("Sign in"), a:has-text("Login")');
    await loginBtn.first().waitFor({ state: 'visible', timeout: 10000 });
    await loginBtn.first().click();
    await page.waitForTimeout(2000);

    const afterUrl = page.url();
    if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
      await fillKeycloakForm(page, creds);
    }
  }

  await waitForAuthAndNavigate(page, targetUrl);
}

/**
 * Logs out the current user via the UI logout button.
 */
export async function logout(page: Page): Promise<void> {
  // Close any open modal overlays via keyboard escape
  await page.keyboard.press('Escape');
  await page.waitForTimeout(500);

  const logoutBtn = page.locator('button:has-text("Logout"), .logout-btn');
  if (await logoutBtn.first().isVisible({ timeout: 2000 }).catch(() => false)) {
    await logoutBtn.first().click({ force: true });
    await page.waitForLoadState('networkidle').catch(() => {});
  }
}
