/**
 * S2C — story 13: the assigned officer's bell updates WITHOUT a page refresh.
 *
 * This has to be a browser test. The requirement is specifically that a STOMP frame pushed by the
 * server changes the badge with no reload, and that cannot be asserted over the API: an API test only
 * proves the notification row was written, which was already true before the client was fixed.
 *
 * The dev server is NOT started by this suite. Port 4200 belongs to the developer, so the suite needs
 * a server that is ALREADY running. It reads the same APP_BASE_URL / UI_BASE_URL the rest of the
 * harness uses, so a normal harness invocation runs it instead of skipping; BELL_UI_BASE_URL still
 * overrides for a one-off port. With no base at all it skips rather than passing vacuously.
 *
 * NOTE on the import: this file deliberately does NOT use '../fixtures'. The shared fixture installs
 * redirectBrowserApiCalls on the context, and this test needs its own narrower page.route so that the
 * STOMP/SockJS handshake is left alone — rerouting it would break the very push being asserted.
 */
import { test, expect } from '@playwright/test';
import { sql, API_BASE } from '../aa/aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S2CB';
const ROLE_GROUP = `${PREFIX}_AA_DO`;
const OFFICER = `${PREFIX}-do1`;

/** Absolute, because the configured baseURL is the developer's port and must not be assumed. */
const UI_BASE =
  process.env['BELL_UI_BASE_URL'] ||
  process.env['APP_BASE_URL'] ||
  process.env['UI_BASE_URL'] ||
  '';

const ADMIN = identityHeadersFor('aa_admin_001', 'AA');

function purge(): void {
  sql(`DELETE FROM aa_assignment_record WHERE appeal_number LIKE '${PREFIX}-%'
        OR assigned_user_id LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM aa_assignment_audit WHERE role_group LIKE '${PREFIX}-%'
        OR subject_user_id LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM wf_officer_pool WHERE user_id LIKE '${PREFIX}-%' OR role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM wf_assignment_counter WHERE role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM in_app_notifications WHERE target_user_id LIKE '${PREFIX}-%'
        OR related_entity_id LIKE '${PREFIX}-%'`);
}

test.describe('S2C notification bell — real-time push', () => {
  test.skip(!UI_BASE,
    'No running dev server: set APP_BASE_URL / UI_BASE_URL (or BELL_UI_BASE_URL). ' +
    'Port 4200 is the developer\'s and is never started here.');

  test.beforeAll(() => purge());
  test.afterAll(() => purge());

  test('the badge increments on assignment with NO page reload', async ({ page, request }) => {
    purge();
    sql(`INSERT INTO wf_officer_pool
           (user_id, display_name, role_group, regional_office, is_active, is_on_leave,
            current_workload, max_workload, skill_languages)
         VALUES ('${OFFICER}', '${OFFICER} name', '${ROLE_GROUP}', NULL, 1, 0, 0, 5, NULL)`);

    // The browser must talk to the same backend the test seeds, and must identify as the officer so
    // the STOMP CONNECT resolves to a principal that matches targetUserId.
    //
    // The WebSocket constructor is patched as well, and that is NOT redundant with page.route below.
    // NotificationService.brokerUrl() derives the socket URL from the compiled
    // environment.apiBaseUrl (ws://localhost:8082/ws/notifications), and Playwright's page.route
    // intercepts HTTP only — a websocket handshake passes straight through it. Without this patch
    // the bell opens a socket against the DEVELOPER's backend on 8082, CONNECTs as this officer
    // there, and then waits forever for a push that this test delivered to 8092 instead. That reads
    // exactly like "the server never pushed".
    await page.addInitScript(([id, compiledWs, targetWs]) => {
      window.sessionStorage.setItem('cms_dev_user_id', id);
      const Original = window.WebSocket;
      const Patched = function (url: string | URL, protocols?: string | string[]) {
        return new Original(String(url).replace(compiledWs, targetWs), protocols);
      } as unknown as typeof WebSocket;
      Patched.prototype = Original.prototype;
      window.WebSocket = Patched;
    }, [OFFICER, 'ws://localhost:8082', API_BASE.replace(/^http/, 'ws')] as const);

    await page.route('http://localhost:8082/**', async route => {
      await route.continue({ url: route.request().url().replace('http://localhost:8082', API_BASE) });
    });

    // /rbio, not /aa/admin. The bell is mounted in exactly one shell — rbio-home.component.html's
    // <app-notification-bell> — so on /aa/admin the badge locator can never resolve and the test
    // would report a missing push when nothing was listening. Checked by grep for the element, not
    // assumed: cepc-dashboard IMPORTS NotificationBellComponent but never places it in its template.
    //
    // /rbio is guarded by staffRoleGuard(RBIO_ROLES), so a bare goto lands on /staff/login; the real
    // Keycloak login is required to reach the shell at all.
    await loginAsRbioRole(page, 'RBIO_OFFICER', `${UI_BASE}/rbio`);

    // The live channel has to be UP before a push can be asserted. Without this the test races the
    // STOMP handshake and a connection failure is indistinguishable from a server that never pushed.
    const bell = page.getByTestId('notification-bell');
    await expect(bell).toBeVisible({ timeout: 30_000 });
    await expect
      .poll(() => bell.getAttribute('data-connected'), {
        timeout: 30_000,
        message:
          'the STOMP channel never came up, so no push can arrive. CONNECT is rejected with "no ' +
          'verifiable user identity on the frame" when the client sends only a bearer token: the ' +
          'dev-local profile excludes OAuth2ResourceServerAutoConfiguration and so has no ' +
          'JwtDecoder to verify it with.',
      })
      .toBe('true');

    // Read from the attribute, not the badge text: the badge element is rendered only while
    // hasUnread() is true, so a starting count of zero gives the locator nothing to read and the
    // "did it increase" comparison would start from a fabricated 0.
    const before = Number((await bell.getAttribute('data-unread-count')) || '0');

    // Capture the navigation count so the assertion can prove no reload happened. A test that merely
    // sees the badge change cannot distinguish a push from an incidental refresh.
    let navigations = 0;
    page.on('framenavigated', frame => {
      if (frame === page.mainFrame()) {
        navigations++;
      }
    });

    const assigned = await request.post(`${API_BASE}/api/v1/aa/assignment/assign`, {
      headers: ADMIN,
      data: { appealNumber: `${PREFIX}-B-1`, roleGroup: ROLE_GROUP },
    });
    expect((await assigned.json()).assignedUserId).toBe(OFFICER);

    // The badge must change on its own, driven by the STOMP frame.
    await expect.poll(async () => {
      const count = await badge.count();
      return count > 0 ? Number((await badge.textContent())?.trim() || '0') : 0;
    }, { timeout: 20_000, message: 'the bell must update from a STOMP push, without a reload' })
      .toBeGreaterThan(before);

    expect(navigations, 'the page must not have reloaded').toBe(0);
  });
});
