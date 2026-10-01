import { test } from '@playwright/test';
import { sql, API_BASE } from '../aa/aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';
import { loginAsRbioRole } from '../utils/auth';
const UI = process.env['APP_BASE_URL']!;
const PREFIX='S2CB'; const ROLE_GROUP=`${PREFIX}_AA_DO`;
const OFFICER = process.env['RBIO_OFFICER_USER'] || 'rbio.officer';
const ADMIN = identityHeadersFor('aa_admin_001','AA');
test('probe5', async ({ page, request }) => {
  sql(`DELETE FROM wf_officer_pool WHERE role_group LIKE '${PREFIX}%'`);
  sql(`DELETE FROM wf_assignment_counter WHERE role_group LIKE '${PREFIX}%'`);
  sql(`DELETE FROM aa_assignment_record WHERE appeal_number LIKE '${PREFIX}-%'`);
  sql(`INSERT INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload, skill_languages) VALUES ('${OFFICER}','${OFFICER} n','${ROLE_GROUP}',NULL,1,0,0,5,NULL)`);
  await page.addInitScript(([from, to]) => {
    const Orig = window.WebSocket;
    // @ts-ignore
    window.WebSocket = function (url: any, p?: any) { const u=String(url).replace(from,to); console.log('WS-OPEN '+u); return new Orig(u,p); } as any;
    window.WebSocket.prototype = Orig.prototype;
  }, ['ws://localhost:8082', API_BASE.replace(/^http/,'ws')]);
  page.on('console', m => { const t=m.text(); if(/WS-OPEN|notifications|STOMP/.test(t)) console.log('C:',t.slice(0,180)); });
  await page.route('http://localhost:8082/**', r => r.continue({ url: r.request().url().replace('http://localhost:8082', API_BASE) }));
  await loginAsRbioRole(page, 'RBIO_OFFICER', `${UI}/rbio`);
  await page.waitForTimeout(4000);
  const bell = page.getByTestId('notification-bell');
  const before = Number(await bell.getAttribute('data-unread-count'));
  console.log('url=', new URL(page.url()).pathname, 'connected=', await bell.getAttribute('data-connected'), 'before=', before);
  const a = await request.post(`${API_BASE}/api/v1/aa/assignment/assign`, { headers: ADMIN, data:{ appealNumber:`${PREFIX}-B-${Date.now()}`, roleGroup: ROLE_GROUP }});
  console.log('assign=', a.status(), (await a.text()).slice(0,140));
  await page.waitForTimeout(5000);
  console.log('after=', await bell.getAttribute('data-unread-count'));
});
