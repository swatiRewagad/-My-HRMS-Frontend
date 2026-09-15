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
 */

export type CepcRoleKey = 'DO' | 'REVIEWER' | 'INCHARGE' | 'CA' | 'ADMIN' | 'CP';
export type RbioRoleKey = 'RBIO_OFFICER' | 'RBIO_SUPERVISOR' | 'RBIO_CONCILIATOR' | 'RBIO_ADJUDICATOR' | 'RBIO_ADMIN';
export type ReRoleKey = 'RE_NODAL_OFFICER' | 'RE_PNO';
export type AaRoleKey = 'AA_REGISTRAR' | 'AA_BENCH_OFFICER' | 'AA_AUTHORITY' | 'AA_ADMIN';

interface Credentials {
  username: string;
  password: string;
}

const ENV_MAP: Record<CepcRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  DO: {
    userEnv: 'CEPC_DO_USER',
    passEnv: 'CEPC_DO_PASS',
    defaults: { username: 'cepc_do_001', password: 'test123' },
  },
  REVIEWER: {
    userEnv: 'CEPC_REVIEWER_USER',
    passEnv: 'CEPC_REVIEWER_PASS',
    defaults: { username: 'cepc_reviewer_001', password: 'test123' },
  },
  INCHARGE: {
    userEnv: 'CEPC_INCHARGE_USER',
    passEnv: 'CEPC_INCHARGE_PASS',
    defaults: { username: 'cepc_incharge_001', password: 'test123' },
  },
  CA: {
    userEnv: 'CEPC_CA_USER',
    passEnv: 'CEPC_CA_PASS',
    defaults: { username: 'cepc_closing_001', password: 'test123' },
  },
  ADMIN: {
    userEnv: 'CEPC_ADMIN_USER',
    passEnv: 'CEPC_ADMIN_PASS',
    defaults: { username: 'cepc_admin_001', password: 'test123' },
  },
  CP: {
    userEnv: 'CEPC_CP_USER',
    passEnv: 'CEPC_CP_PASS',
    defaults: { username: 'cepc_contact_001', password: 'test123' },
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
 */
export async function isKeycloakAvailable(page: Page): Promise<boolean> {
  try {
    const response = await page.request.get('http://localhost:8180/realms/cms', {
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
    defaults: { username: 'rbio_officer_001', password: 'test123' },
  },
  RBIO_SUPERVISOR: {
    userEnv: 'RBIO_SUPERVISOR_USER',
    passEnv: 'RBIO_SUPERVISOR_PASS',
    defaults: { username: 'rbio_supervisor_001', password: 'test123' },
  },
  RBIO_CONCILIATOR: {
    userEnv: 'RBIO_CONCILIATOR_USER',
    passEnv: 'RBIO_CONCILIATOR_PASS',
    defaults: { username: 'rbio_conciliator_001', password: 'test123' },
  },
  RBIO_ADJUDICATOR: {
    userEnv: 'RBIO_ADJUDICATOR_USER',
    passEnv: 'RBIO_ADJUDICATOR_PASS',
    defaults: { username: 'rbio_adjudicator_001', password: 'test123' },
  },
  RBIO_ADMIN: {
    userEnv: 'RBIO_ADMIN_USER',
    passEnv: 'RBIO_ADMIN_PASS',
    defaults: { username: 'admin_001', password: 'test123' },
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
  RE_NODAL_OFFICER: {
    userEnv: 'RE_NODAL_USER',
    passEnv: 'RE_NODAL_PASS',
    defaults: { username: 're_nodal_001', password: 'test123' },
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

const AA_ENV_MAP: Record<AaRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  AA_REGISTRAR: {
    userEnv: 'AA_REGISTRAR_USER',
    passEnv: 'AA_REGISTRAR_PASS',
    defaults: { username: 'aa_registrar_001', password: 'test123' },
  },
  AA_BENCH_OFFICER: {
    userEnv: 'AA_BENCH_USER',
    passEnv: 'AA_BENCH_PASS',
    defaults: { username: 'aa_bench_001', password: 'test123' },
  },
  AA_AUTHORITY: {
    userEnv: 'AA_AUTHORITY_USER',
    passEnv: 'AA_AUTHORITY_PASS',
    defaults: { username: 'aa_authority_001', password: 'test123' },
  },
  AA_ADMIN: {
    userEnv: 'AA_ADMIN_USER',
    passEnv: 'AA_ADMIN_PASS',
    defaults: { username: 'aa_admin_001', password: 'test123' },
  },
};

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
