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
    defaults: { username: 'cepc_do', password: 'cepc_do' },
  },
  REVIEWER: {
    userEnv: 'CEPC_REVIEWER_USER',
    passEnv: 'CEPC_REVIEWER_PASS',
    defaults: { username: 'cepc_reviewer', password: 'cepc_reviewer' },
  },
  INCHARGE: {
    userEnv: 'CEPC_INCHARGE_USER',
    passEnv: 'CEPC_INCHARGE_PASS',
    defaults: { username: 'cepc_incharge', password: 'cepc_incharge' },
  },
  CA: {
    userEnv: 'CEPC_CA_USER',
    passEnv: 'CEPC_CA_PASS',
    defaults: { username: 'cepc_ca', password: 'cepc_ca' },
  },
  ADMIN: {
    userEnv: 'CEPC_ADMIN_USER',
    passEnv: 'CEPC_ADMIN_PASS',
    defaults: { username: 'cepc_admin', password: 'cepc_admin' },
  },
  CP: {
    userEnv: 'CEPC_CP_USER',
    passEnv: 'CEPC_CP_PASS',
    defaults: { username: 'cepc_cp', password: 'cepc_cp' },
  },
};

function getCredentials(role: CepcRoleKey): Credentials {
  const cfg = ENV_MAP[role];
  return {
    username: process.env[cfg.userEnv] || cfg.defaults.username,
    password: process.env[cfg.passEnv] || cfg.defaults.password,
  };
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
    // Fill Keycloak login form
    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');
    const loginButton = page.locator('#kc-login');

    await usernameInput.waitFor({ state: 'visible', timeout: 10000 });
    await usernameInput.fill(creds.username);
    await passwordInput.fill(creds.password);
    await loginButton.click();

    // Wait for redirect back to app
    await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
  } else if (currentUrl.includes('/staff/login')) {
    // The app's own staff login page may trigger Keycloak
    // Click the login button which initiates Keycloak redirect
    const loginBtn = page.locator('button:has-text("Login"), a:has-text("Login")');
    if (await loginBtn.isVisible()) {
      await loginBtn.click();
      await page.waitForTimeout(1000);

      // Now handle Keycloak form if redirected
      const afterUrl = page.url();
      if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
        await page.locator('#username').fill(creds.username);
        await page.locator('#password').fill(creds.password);
        await page.locator('#kc-login').click();
        await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
      }
    }
  }

  // At this point we should be on the target page
  // Wait for the page to stabilize (signal-based rendering)
  await page.waitForLoadState('networkidle');
}

// ────────────────────────────────────────────────────────────────────────────
// RBIO Role Login
// ────────────────────────────────────────────────────────────────────────────

const RBIO_ENV_MAP: Record<RbioRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  RBIO_OFFICER: {
    userEnv: 'RBIO_OFFICER_USER',
    passEnv: 'RBIO_OFFICER_PASS',
    defaults: { username: 'rbio.officer', password: 'test123' },
  },
  RBIO_SUPERVISOR: {
    userEnv: 'RBIO_SUPERVISOR_USER',
    passEnv: 'RBIO_SUPERVISOR_PASS',
    defaults: { username: 'rbio.supervisor', password: 'test123' },
  },
  RBIO_CONCILIATOR: {
    userEnv: 'RBIO_CONCILIATOR_USER',
    passEnv: 'RBIO_CONCILIATOR_PASS',
    defaults: { username: 'rbio.conciliator', password: 'test123' },
  },
  RBIO_ADJUDICATOR: {
    userEnv: 'RBIO_ADJUDICATOR_USER',
    passEnv: 'RBIO_ADJUDICATOR_PASS',
    defaults: { username: 'rbio.adjudicator', password: 'test123' },
  },
  RBIO_ADMIN: {
    userEnv: 'RBIO_ADMIN_USER',
    passEnv: 'RBIO_ADMIN_PASS',
    defaults: { username: 'rbio.admin', password: 'test123' },
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

  // Check if we were redirected to Keycloak login page
  if (currentUrl.includes('/realms/') || currentUrl.includes('/auth/')) {
    // Fill Keycloak login form
    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');
    const loginButton = page.locator('#kc-login');

    await usernameInput.waitFor({ state: 'visible', timeout: 10000 });
    await usernameInput.fill(creds.username);
    await passwordInput.fill(creds.password);
    await loginButton.click();

    // Wait for redirect back to app
    await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
  } else if (currentUrl.includes('/staff/login')) {
    // The app's own staff login page may trigger Keycloak
    const loginBtn = page.locator('button:has-text("Login"), a:has-text("Login")');
    if (await loginBtn.isVisible()) {
      await loginBtn.click();
      await page.waitForTimeout(1000);

      const afterUrl = page.url();
      if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
        await page.locator('#username').fill(creds.username);
        await page.locator('#password').fill(creds.password);
        await page.locator('#kc-login').click();
        await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
      }
    }
  }

  // Wait for the page to stabilize
  await page.waitForLoadState('networkidle');
}

// ────────────────────────────────────────────────────────────────────────────
// RE (Regulated Entity) Role Login
// ────────────────────────────────────────────────────────────────────────────

const RE_ENV_MAP: Record<ReRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  RE_NODAL_OFFICER: {
    userEnv: 'RE_NODAL_USER',
    passEnv: 'RE_NODAL_PASS',
    defaults: { username: 're.nodal', password: 'test123' },
  },
  RE_PNO: {
    userEnv: 'RE_PNO_USER',
    passEnv: 'RE_PNO_PASS',
    defaults: { username: 're.pno', password: 'test123' },
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
  targetUrl = '/re/dashboard'
): Promise<void> {
  const creds = getReCredentials(role);

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  const currentUrl = page.url();

  if (currentUrl.includes('/realms/') || currentUrl.includes('/auth/')) {
    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');
    const loginButton = page.locator('#kc-login');

    await usernameInput.waitFor({ state: 'visible', timeout: 10000 });
    await usernameInput.fill(creds.username);
    await passwordInput.fill(creds.password);
    await loginButton.click();

    await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
  } else if (currentUrl.includes('/staff/login') || currentUrl.includes('/re/login')) {
    const loginBtn = page.locator('button:has-text("Login"), a:has-text("Login")');
    if (await loginBtn.isVisible()) {
      await loginBtn.click();
      await page.waitForTimeout(1000);

      const afterUrl = page.url();
      if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
        await page.locator('#username').fill(creds.username);
        await page.locator('#password').fill(creds.password);
        await page.locator('#kc-login').click();
        await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
      }
    }
  }

  await page.waitForLoadState('networkidle');
}

// ────────────────────────────────────────────────────────────────────────────
// AA (Appellate Authority) Role Login
// ────────────────────────────────────────────────────────────────────────────

const AA_ENV_MAP: Record<AaRoleKey, { userEnv: string; passEnv: string; defaults: Credentials }> = {
  AA_REGISTRAR: {
    userEnv: 'AA_REGISTRAR_USER',
    passEnv: 'AA_REGISTRAR_PASS',
    defaults: { username: 'aa.registrar', password: 'test123' },
  },
  AA_BENCH_OFFICER: {
    userEnv: 'AA_BENCH_USER',
    passEnv: 'AA_BENCH_PASS',
    defaults: { username: 'aa.bench', password: 'test123' },
  },
  AA_AUTHORITY: {
    userEnv: 'AA_AUTHORITY_USER',
    passEnv: 'AA_AUTHORITY_PASS',
    defaults: { username: 'aa.authority', password: 'test123' },
  },
  AA_ADMIN: {
    userEnv: 'AA_ADMIN_USER',
    passEnv: 'AA_ADMIN_PASS',
    defaults: { username: 'aa.admin', password: 'test123' },
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
    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');
    const loginButton = page.locator('#kc-login');

    await usernameInput.waitFor({ state: 'visible', timeout: 10000 });
    await usernameInput.fill(creds.username);
    await passwordInput.fill(creds.password);
    await loginButton.click();

    await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
  } else if (currentUrl.includes('/staff/login') || currentUrl.includes('/aa/login')) {
    const loginBtn = page.locator('button:has-text("Login"), a:has-text("Login")');
    if (await loginBtn.isVisible()) {
      await loginBtn.click();
      await page.waitForTimeout(1000);

      const afterUrl = page.url();
      if (afterUrl.includes('/realms/') || afterUrl.includes('/auth/')) {
        await page.locator('#username').fill(creds.username);
        await page.locator('#password').fill(creds.password);
        await page.locator('#kc-login').click();
        await page.waitForURL(`**${targetUrl}*`, { timeout: 15000 });
      }
    }
  }

  await page.waitForLoadState('networkidle');
}

/**
 * Logs out the current user via the UI logout button.
 */
export async function logout(page: Page): Promise<void> {
  const logoutBtn = page.locator('button:has-text("Logout")');
  if (await logoutBtn.isVisible()) {
    await logoutBtn.click();
    await page.waitForLoadState('networkidle');
  }
}
