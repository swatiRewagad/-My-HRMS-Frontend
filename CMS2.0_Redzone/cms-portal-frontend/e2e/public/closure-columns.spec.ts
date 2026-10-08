import { test, expect } from '../fixtures';
import { loginCitizen, seedCitizenSession } from '../utils/test-data';

/**
 * The closure columns are driven entirely by the API response — there is no client-side fallback.
 * GET /api/v1/complaints?phone= does not yet return closureClause, closureDate or the letter URLs
 * (the backend change that adds them is blocked on the cms-backend tree not compiling), so the
 * response is stubbed here to assert the rendering contract the UI must honour once it does.
 */
const CITIZEN_MOBILE = '9597854554';

const CLOSED = 'CMP-STUB-CLOSED';
const OPEN = 'CMP-STUB-OPEN';

const STUB = {
  success: true,
  message: 'Complaints retrieved',
  data: [
    {
      complaintId: CLOSED,
      entityName: 'Stub Bank Ltd',
      complaintDate: '20-09-2026',
      status: 'CLOSED',
      closureClause: '15(1)(a)',
      closureDate: '25-09-2026',
      acknowledgementLetterUrl: '/assets/documents/ComplaintForm.pdf',
      closureLetterUrl: '/assets/documents/Ombudsman_Scheme-2026.pdf',
    },
    {
      complaintId: OPEN,
      entityName: 'Stub Bank Ltd',
      complaintDate: '22-09-2026',
      status: 'PENDING',
      closureClause: '',
      closureDate: '',
      acknowledgementLetterUrl: '',
      closureLetterUrl: '',
    },
  ],
};

for (const [name, url] of [['home', '/public'], ['history', '/public/history']] as const) {
  test(`${name}: closure columns render API data and stay blank without it`, async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    test.skip(!token, 'Citizen OTP login unavailable');

    await page.route('**/api/v1/complaints?phone=*', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(STUB) }));
    await page.route('**/api/v1/complaints/drafts*', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: [] }) }));

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Closed row: every closure field the API supplied must be shown.
    const closedRow = page.locator('tr', { hasText: CLOSED });
    await expect(closedRow).toBeVisible({ timeout: 15000 });
    await expect(closedRow.locator('td.col-clause')).toHaveText('15(1)(a)');
    await expect(closedRow.locator('td.col-closure-date')).toHaveText('25/09/2026');
    await expect(closedRow.locator('td.col-letter a.btn-download')).toHaveCount(2);

    // Open row: the API sent nothing, so all four cells stay empty placeholders.
    const openRow = page.locator('tr', { hasText: OPEN });
    await expect(openRow.locator('td.col-clause')).toHaveText('—');
    await expect(openRow.locator('td.col-closure-date')).toHaveText('—');
    await expect(openRow.locator('td.col-letter a.btn-download')).toHaveCount(0);
    await expect(openRow.locator('td.col-letter .no-document')).toHaveCount(2);

    // The closure-date filter must narrow on the API-supplied date.
    await page.locator('.date-filter-picker input').nth(1).pressSequentially('25/09/2026');
    await page.waitForTimeout(600);
    await expect(page.locator('tr', { hasText: CLOSED })).toBeVisible();
    await expect(page.locator('tr', { hasText: OPEN })).toHaveCount(0);
  });
}
