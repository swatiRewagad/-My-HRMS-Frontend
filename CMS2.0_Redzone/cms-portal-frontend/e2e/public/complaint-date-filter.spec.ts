import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * Selecting a date in the registration-date column must narrow the table to that day.
 *
 * The registered `dateEquals` predicate ran `new Date(row.complaintDate)`, but /complaints sends
 * dd-MM-yyyy, which is not a format new Date() can parse. Every submitted row therefore produced
 * Invalid Date and failed the comparison, so picking any date emptied the table. Typing a date was
 * separately dead: the handler was bound only to (onSelect)/(onClear), so keyboard entry updated
 * the model without ever calling Table.filter.
 *
 * Both routes render the same table, so both are asserted.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const PHONE = '9597854554';

const ON_24TH = 'CMP-ON-24TH';
const ON_23RD = 'CMP-ON-23RD';
const ON_23RD_TOO = 'CMP-ON-23RD-TOO';

async function stubLists(page: any) {
  await page.route('**/api/v1/complaints?phone=*', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'Complaints retrieved',
        data: [
          { complaintId: ON_24TH, entityName: 'State Bank of India', complaintDate: '24-09-2026', status: 'PENDING' },
          { complaintId: ON_23RD, entityName: 'HDFC Bank', complaintDate: '23-09-2026', status: 'IN_PROGRESS' },
          { complaintId: ON_23RD_TOO, entityName: 'ICICI Bank', complaintDate: '23-09-2026', status: 'CLOSED' }
        ]
      })
    });
  });

  await page.route('**/api/v1/complaints/drafts?phone=*', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, message: 'Drafts retrieved', data: [] })
    });
  });
}

async function typeDate(page: any, input: any, text: string) {
  await input.click();
  await input.press('Control+a');
  await input.pressSequentially(text, { delay: 30 });
  await page.keyboard.press('Escape');
}

async function clearDate(page: any, root: any) {
  await root.locator('[data-p-icon="times"]').click();
  await page.keyboard.press('Escape');
}

for (const [label, path] of [['My Complaints', '/public/history'], ['Home', '/public']]) {

  test(`${label}: picking a registration date filters the table to that day`, async ({ page }) => {
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, PHONE, 'e2e-seeded-token.sig');
    await stubLists(page);
    await page.goto(`${APP_BASE}${path}`);

    await expect(page.getByText(ON_24TH).first()).toBeVisible({ timeout: 15000 });

    const dateInput = page.locator('.date-filter-picker input').first();

    // Real keystrokes, in the dd/mm/yyyy format the placeholder advertises. PrimeNG's datepicker
    // only parses typed text when a keydown preceded it, so fill() would not exercise this path.
    await typeDate(page, dateInput, '23/09/2026');

    await expect(page.getByText(ON_23RD).first()).toBeVisible();
    await expect(page.getByText(ON_23RD_TOO).first()).toBeVisible();
    await expect(page.getByText(ON_24TH)).toHaveCount(0);

    // Clearing restores every row.
    await clearDate(page, page.locator('.date-filter-picker').first());

    await expect(page.getByText(ON_24TH).first()).toBeVisible();
    await expect(page.getByText(ON_23RD).first()).toBeVisible();
  });

  test(`${label}: picking a day from the calendar filters the table`, async ({ page }) => {
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, PHONE, 'e2e-seeded-token.sig');
    await stubLists(page);
    await page.goto(`${APP_BASE}${path}`);

    await expect(page.getByText(ON_24TH).first()).toBeVisible({ timeout: 15000 });

    await page.locator('.date-filter-picker button').first().click();
    await page.getByRole('gridcell', { name: '23', exact: true }).first().click();

    await expect(page.getByText(ON_23RD).first()).toBeVisible();
    await expect(page.getByText(ON_24TH)).toHaveCount(0);
  });

  test(`${label}: a date with no complaints yields an empty table`, async ({ page }) => {
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, PHONE, 'e2e-seeded-token.sig');
    await stubLists(page);
    await page.goto(`${APP_BASE}${path}`);

    await expect(page.getByText(ON_24TH).first()).toBeVisible({ timeout: 15000 });

    const dateInput = page.locator('.date-filter-picker input').first();
    await typeDate(page, dateInput, '01/01/2020');

    await expect(page.getByText(ON_24TH)).toHaveCount(0);
    await expect(page.getByText(ON_23RD)).toHaveCount(0);
  });

  test(`${label}: registration dates render as dd/mm/yyyy`, async ({ page }) => {
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, PHONE, 'e2e-seeded-token.sig');
    await stubLists(page);
    await page.goto(`${APP_BASE}${path}`);

    await expect(page.getByText(ON_24TH).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('24/09/2026').first()).toBeVisible();
  });
}
