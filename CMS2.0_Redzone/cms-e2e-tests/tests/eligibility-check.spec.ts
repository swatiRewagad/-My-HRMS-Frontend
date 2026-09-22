import { test, expect } from '@playwright/test';
import { PublicHomePage } from '../pages';

test.describe('Eligibility Check (Pre-Filing)', () => {
  test('should display eligibility checker on public portal', async ({ page }) => {
    await page.goto('/eligibility');
    await expect(page.getByText(/eligibility|check.*complaint/i)).toBeVisible();
  });

  test('should ask about prior complaint to RE', async ({ page }) => {
    await page.goto('/eligibility');
    await expect(page.getByText(/complain.*regulated entity|approach.*bank/i)).toBeVisible();
  });

  test('should show eligible result when all conditions met', async ({ page }) => {
    await page.goto('/eligibility');

    await page.getByText(/yes/i).first().click();
    await page.getByRole('button', { name: /next|continue/i }).click();

    await page.getByLabel(/date.*complaint/i).fill('2026-05-01');
    await page.getByRole('button', { name: /next|continue/i }).click();

    await page.getByText(/yes.*reply|dissatisfied/i).click();
    await page.getByRole('button', { name: /check|submit/i }).click();

    await expect(page.getByText(/eligible|you can file/i)).toBeVisible({ timeout: 5000 });
  });

  test('should show ineligible when no prior RE complaint', async ({ page }) => {
    await page.goto('/eligibility');

    await page.getByText(/no/i).first().click();
    await page.getByRole('button', { name: /check|next/i }).click();

    await expect(page.getByText(/not eligible|first.*complain.*entity|approach.*bank first/i)).toBeVisible();
  });

  test('should show warning when filed too early (window not elapsed)', async ({ page }) => {
    await page.goto('/eligibility');

    await page.getByText(/yes/i).first().click();
    await page.getByRole('button', { name: /next|continue/i }).click();

    const today = new Date().toISOString().split('T')[0];
    await page.getByLabel(/date.*complaint/i).fill(today);
    await page.getByRole('button', { name: /next|continue/i }).click();

    await page.getByText(/no.*not.*reply|waiting/i).click();
    await page.getByRole('button', { name: /check|submit/i }).click();

    await expect(page.getByText(/wait|30.*days|window.*not.*elapsed/i)).toBeVisible();
  });

  test('should show link to file complaint after eligibility passes', async ({ page }) => {
    await page.goto('/eligibility');

    await page.getByText(/yes/i).first().click();
    await page.getByRole('button', { name: /next|continue/i }).click();
    await page.getByLabel(/date.*complaint/i).fill('2026-05-01');
    await page.getByRole('button', { name: /next|continue/i }).click();
    await page.getByText(/yes.*reply|dissatisfied/i).click();
    await page.getByRole('button', { name: /check|submit/i }).click();

    const fileLink = page.getByRole('link', { name: /file.*complaint|proceed/i });
    await expect(fileLink).toBeVisible();
    await fileLink.click();
    await expect(page).toHaveURL(/.*file-complaint.*/);
  });
});
