/**
 * S2C — story 13: the assigned officer's bell updates WITHOUT a page refresh.
 *
 * This has to be a browser test. The requirement is specifically that a STOMP frame pushed by the
 * server changes the badge with no reload, and that cannot be asserted over the API: an API test only
 * proves the notification row was written, which was already true before the client was fixed.
 *
 * The dev server is NOT started by this suite. Port 4200 belongs to the developer, so the suite is
 * skipped unless a server is already reachable at BELL_UI_BASE_URL. A skipped run is reported as
 * skipped rather than passing vacuously.
 */
import { test, expect } from '@playwright/test';
import { sql, API_BASE } from '../aa/aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S2CB';
const ROLE_GROUP = `${PREFIX}_AA_DO`;
const OFFICER = `${PREFIX}-do1`;

/** Absolute, because the configured baseURL is the developer's port and must not be assumed. */
const UI_BASE = process.env['BELL_UI_BASE_URL'] || '';

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
    'Set BELL_UI_BASE_URL to a running dev server. Port 4200 is the developer\'s and is never started here.');

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
    await page.addInitScript(id => {
      window.sessionStorage.setItem('cms_dev_user_id', id);
    }, OFFICER);

    await page.route('http://localhost:8082/**', async route => {
      await route.continue({ url: route.request().url().replace('http://localhost:8082', API_BASE) });
    });

    await page.goto(`${UI_BASE}/aa/admin`);

    const badge = page.getByTestId('notification-badge');
    const before = (await badge.count()) > 0 ? Number((await badge.textContent())?.trim() || '0') : 0;

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
