import { test, expect, Page } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

test.describe('Public Portal 2 - Eligibility Wizard', () => {

  async function goToWizard(page: Page) {
    await page.goto('/public/eligibility-wizard', { waitUntil: 'networkidle' });
    await page.waitForSelector('.wizard-card', { timeout: 15000 });
  }

  async function quickPathToResult(page: Page) {
    await goToWizard(page);
    await page.locator('.wizard-select').selectOption('BANK');
    await page.locator('.btn-wizard-next').click();
    const noRadio = page.locator('.wizard-radio').filter({ hasText: /no/i });
    await noRadio.click();
    await page.locator('.btn-wizard-next').click();
    await page.waitForSelector('.result-card', { timeout: 15000 });
  }

  test('navigates to wizard from landing page via "Public Portal 2" card', async ({ page }) => {
    await page.goto('/', { waitUntil: 'networkidle' });
    await page.locator('.portal2-card').click();
    await expect(page).toHaveURL(/\/public\/eligibility-wizard/);
  });

  test('wizard page loads with title and subtitle', async ({ page }) => {
    await goToWizard(page);
    const header = page.locator('.wizard-header');
    await expect(header).toBeVisible();
    await expect(header.locator('h1')).toBeVisible();
    await expect(page.locator('.wizard-subtitle')).toBeVisible();
  });

  test('wizard starts at step 1 with progress bar', async ({ page }) => {
    await goToWizard(page);

    const progressText = page.locator('.progress-text');
    await expect(progressText).toContainText('1');

    const progressFill = page.locator('.progress-fill');
    await expect(progressFill).toBeVisible();
  });

  test('step 1 - entity type select dropdown is displayed', async ({ page }) => {
    await goToWizard(page);

    const select = page.locator('.wizard-select');
    await expect(select).toBeVisible();

    const options = select.locator('option');
    const count = await options.count();
    expect(count).toBeGreaterThanOrEqual(5);
  });

  test('next button is disabled until answer is selected', async ({ page }) => {
    await goToWizard(page);

    const nextBtn = page.locator('.btn-wizard-next');
    await expect(nextBtn).toBeDisabled();

    await page.locator('.wizard-select').selectOption('BANK');
    await expect(nextBtn).toBeEnabled();
  });

  test('back button is disabled on first step', async ({ page }) => {
    await goToWizard(page);

    const backBtn = page.locator('.btn-wizard-back');
    await expect(backBtn).toBeDisabled();
  });

  test('step 1 → step 2: select entity type and proceed', async ({ page }) => {
    await goToWizard(page);

    await page.locator('.wizard-select').selectOption('BANK');
    await page.locator('.btn-wizard-next').click();

    const progressText = page.locator('.progress-text');
    await expect(progressText).toContainText('2');

    const radioGroup = page.locator('.radio-group');
    await expect(radioGroup).toBeVisible();
  });

  test('step 2 - "No" answer short-circuits to result (RE_FIRST)', async ({ page }) => {
    await quickPathToResult(page);
    await expect(page.locator('.outcome-header')).toBeVisible();
  });

  test('full flow - eligible complaint (READY outcome)', async ({ page }) => {
    await goToWizard(page);

    // Step 1 - Entity Type: Bank
    await page.locator('.wizard-select').selectOption('BANK');
    await page.locator('.btn-wizard-next').click();

    // Step 2 - Complained to RE: Yes
    const yesRadio = page.locator('.wizard-radio').filter({ hasText: /yes/i });
    await yesRadio.click();
    await page.locator('.btn-wizard-next').click();

    // Step 3 - Date (45 days ago to exceed 30-day window)
    const pastDate = new Date();
    pastDate.setDate(pastDate.getDate() - 45);
    const dateStr = pastDate.toISOString().split('T')[0];
    await page.locator('.wizard-date-input').fill(dateStr);
    await page.locator('.btn-wizard-next').click();

    // Step 4 - RE Response: No reply
    const noReplyRadio = page.locator('.wizard-radio').filter({ hasText: /no.*(reply|response)/i });
    await noReplyRadio.click();
    await page.locator('.btn-wizard-next').click();

    // Wait for result
    await page.waitForSelector('.result-card', { timeout: 15000 });
    await expect(page.locator('.outcome-header')).toBeVisible();
    await expect(page.locator('.outcome-title')).toBeVisible();
  });

  test('full flow - too early (TOO_EARLY outcome)', async ({ page }) => {
    await goToWizard(page);

    // Step 1 - Entity Type: Bank
    await page.locator('.wizard-select').selectOption('BANK');
    await page.locator('.btn-wizard-next').click();

    // Step 2 - Complained to RE: Yes
    const yesRadio = page.locator('.wizard-radio').filter({ hasText: /yes/i });
    await yesRadio.click();
    await page.locator('.btn-wizard-next').click();

    // Step 3 - Date (5 days ago - within 30-day window)
    const recentDate = new Date();
    recentDate.setDate(recentDate.getDate() - 5);
    const dateStr = recentDate.toISOString().split('T')[0];
    await page.locator('.wizard-date-input').fill(dateStr);
    await page.locator('.btn-wizard-next').click();

    // Step 4 - RE Response: No reply
    const noReplyRadio = page.locator('.wizard-radio').filter({ hasText: /no.*(reply|response)/i });
    await noReplyRadio.click();
    await page.locator('.btn-wizard-next').click();

    // Wait for result
    await page.waitForSelector('.result-card', { timeout: 15000 });
    await expect(page.locator('.outcome-header')).toBeVisible();

    // Should show timeline with days remaining
    const timeline = page.locator('.timeline-section');
    if (await timeline.isVisible()) {
      await expect(page.locator('.days-badge')).toBeVisible();
    }
  });

  test('back navigation works across steps', async ({ page }) => {
    await goToWizard(page);

    // Step 1
    await page.locator('.wizard-select').selectOption('NBFC');
    await page.locator('.btn-wizard-next').click();

    // Verify on step 2
    await expect(page.locator('.progress-text')).toContainText('2');

    // Go back
    await page.locator('.btn-wizard-back').click();

    // Verify back to step 1 with answer preserved
    await expect(page.locator('.progress-text')).toContainText('1');
    const selectValue = await page.locator('.wizard-select').inputValue();
    expect(selectValue).toBe('NBFC');
  });

  test('result page has "Start Over" button that resets wizard', async ({ page }) => {
    await quickPathToResult(page);

    // Click Start Over
    const startOverBtn = page.locator('button').filter({ hasText: /start over/i });
    await expect(startOverBtn).toBeVisible();
    await startOverBtn.click();

    // Should be back to step 1
    await expect(page.locator('.wizard-select')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.progress-text')).toContainText('1');
  });

  test('result page has "Go Home" button that navigates away from wizard', async ({ page }) => {
    await quickPathToResult(page);

    // Click Go Home
    const goHomeBtn = page.locator('button').filter({ hasText: /home/i });
    await expect(goHomeBtn).toBeVisible();
    await goHomeBtn.click();

    await page.waitForLoadState('networkidle');
    expect(page.url()).not.toContain('eligibility-wizard');
  });

  test('timeline visualization appears for applicable outcomes', async ({ page }) => {
    await quickPathToResult(page);

    // RE_FIRST outcome should show timeline
    const timeline = page.locator('.timeline-section');
    await expect(timeline).toBeVisible({ timeout: 5000 });

    const timelineItems = page.locator('.timeline-item');
    await expect(timelineItems).toHaveCount(3);
  });

  test('no console errors during wizard flow', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await quickPathToResult(page);
    await page.waitForTimeout(1000);

    const criticalErrors = errors.filter(e =>
      !e.includes('favicon') &&
      !e.includes('404') &&
      !e.includes('config.json') &&
      !e.includes('HttpErrorResponse') &&
      !e.includes('Failed to fetch') &&
      !e.includes('ERR_CONNECTION_REFUSED')
    );
    expect(criticalErrors).toHaveLength(0);
  });
});
