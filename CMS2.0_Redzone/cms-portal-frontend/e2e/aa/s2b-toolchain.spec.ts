import { test, expect } from '../fixtures';
import { sql, purgeByPrefix, assertBackendReady, fetchTranslations } from './aa-shared-fixtures';

const DO = { 'X-User-Roles': 'AA_DO', 'X-User-Id': 'aa_do_001' };
const PREFIX = 'S2B-TOOLCHAIN';

test.describe('S2B toolchain proof', () => {
  test.afterAll(() => purgeByPrefix(PREFIX));

  test('backend on the S2B port answers, MySQL is reachable, i18n serves a locale', async ({ request }) => {
    await assertBackendReady(request, DO);

    const stats = await request.get('/api/v1/email-syndication/stats', { headers: DO });
    expect(stats.ok(), 'email-syndication stats must be reachable').toBeTruthy();

    const tableCount = sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='cms_db' AND table_name='email_ignore_list'");
    expect(tableCount.trim(), 'email_ignore_list table must exist').toBe('1');

    const hi = await fetchTranslations(request, 'hi');
    expect(Object.keys(hi).length, 'the hi locale must return translations').toBeGreaterThan(0);
  });
});
