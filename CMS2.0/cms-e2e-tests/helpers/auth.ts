import { Page } from '@playwright/test';

export interface UserCredentials {
  username: string;
  password: string;
  role: string;
}

export const USERS: Record<string, UserCredentials> = {
  rbioOfficer: {
    username: 'officer.rbio1',
    password: 'Test@1234',
    role: 'RBIO_OFFICER',
  },
  rbioSupervisor: {
    username: 'supervisor.rbio1',
    password: 'Test@1234',
    role: 'RBIO_SUPERVISOR',
  },
  cepcOfficer: {
    username: 'officer.cepc1',
    password: 'Test@1234',
    role: 'CEPC_DO',
  },
  crpcDeo: {
    username: 'officer.crpc1',
    password: 'Test@1234',
    role: 'CRPC_DEO',
  },
  admin: {
    username: 'admin',
    password: 'Admin@1234',
    role: 'ADMIN',
  },
};

export async function loginViaKeycloak(page: Page, user: UserCredentials): Promise<void> {
  await page.goto('/');
  await page.getByRole('link', { name: /login|sign in/i }).click();
  await page.waitForURL(/.*keycloak.*/);
  await page.getByLabel('Username').fill(user.username);
  await page.getByLabel('Password').fill(user.password);
  await page.getByRole('button', { name: /sign in|log in/i }).click();
  await page.waitForURL(/.*localhost:4200.*/);
}

export async function loginWithToken(page: Page, user: UserCredentials): Promise<void> {
  const keycloakUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180';
  const realm = process.env.KEYCLOAK_REALM || 'cms';
  const clientId = process.env.KEYCLOAK_CLIENT || 'cms-portal';

  const tokenResponse = await page.request.post(
    `${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: clientId,
        username: user.username,
        password: user.password,
      },
    }
  );

  const { access_token } = await tokenResponse.json();

  await page.addInitScript((token: string) => {
    localStorage.setItem('access_token', token);
  }, access_token);
}
