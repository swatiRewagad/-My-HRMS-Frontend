import { test, expect } from '@playwright/test';

test.describe('Portal Launch - http://localhost:4200', () => {

  test('landing page loads successfully', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('http://localhost:4200/');
    await expect(page).toHaveTitle(/CMS|Complaint/i);
  });

  test('hero section displays RBI heading and tagline', async ({ page }) => {
    await page.goto('/');
    const heading = page.locator('h1');
    await expect(heading).toBeVisible({ timeout: 10000 });
    await expect(heading).toContainText('RBI Complaint Management System');

    const tagline = page.locator('.tagline');
    await expect(tagline).toBeVisible();
    await expect(tagline).toContainText('Integrated Ombudsman Scheme');
  });

  test('RBI logo is displayed', async ({ page }) => {
    await page.goto('/');
    const logo = page.locator('.rbi-logo');
    await expect(logo).toBeVisible({ timeout: 10000 });
  });

  test('all action cards are visible', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.action-cards', { timeout: 10000 });

    const cards = page.locator('.action-card');
    await expect(cards).toHaveCount(7);

    await expect(page.locator('.search-card')).toBeVisible();
    await expect(page.locator('.simulator-card')).toBeVisible();
    await expect(page.locator('.staff-card')).toBeVisible();
    await expect(page.locator('.public-card').first()).toBeVisible();
    await expect(page.locator('.portal2-card')).toBeVisible();
    await expect(page.locator('.keycloak-card')).toBeVisible();
    await expect(page.locator('.rules-card')).toBeVisible();
  });

  test('action cards have correct titles', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.action-cards', { timeout: 10000 });

    await expect(page.locator('.search-card h3')).toHaveText('Search Complaints');
    await expect(page.locator('.simulator-card h3')).toHaveText('Email Simulation');
    await expect(page.locator('.staff-card h3')).toHaveText('Staff Portal (SSO)');
    await expect(page.locator('.keycloak-card h3')).toHaveText('Keycloak Admin');
    await expect(page.locator('.rules-card h3')).toHaveText('Rule Engine');
  });

  test('info section "Before You File" is displayed', async ({ page }) => {
    await page.goto('/');
    const infoSection = page.locator('.info-section');
    await expect(infoSection).toBeVisible({ timeout: 10000 });
    await expect(infoSection.locator('h2')).toHaveText('Before You File');

    const infoItems = infoSection.locator('.info-item');
    await expect(infoItems).toHaveCount(3);
    await expect(infoItems.nth(0).locator('h4')).toHaveText('Approach Your Bank First');
    await expect(infoItems.nth(1).locator('h4')).toHaveText('Wait 30 Days');
    await expect(infoItems.nth(2).locator('h4')).toHaveText('Eligibility Check');
  });

  test('Search Complaints card navigates to /search', async ({ page }) => {
    await page.goto('/');
    await page.locator('.search-card').click();
    await expect(page).toHaveURL(/\/search/);
  });

  test('Staff Portal card navigates to /staff/login', async ({ page }) => {
    await page.goto('/');
    await page.locator('.staff-card').click();
    await page.waitForTimeout(2000);
    const url = page.url();
    expect(url.includes('/staff') || url.includes('/realms/')).toBeTruthy();
  });

  test('page loads without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const criticalErrors = errors.filter(e =>
      !e.includes('favicon') && !e.includes('404') && !e.includes('config.json')
    );
    expect(criticalErrors).toHaveLength(0);
  });

  test('page is responsive - mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await expect(page.locator('h1')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.action-cards')).toBeVisible();
  });

  test('no broken images on landing page', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const images = page.locator('img');
    const count = await images.count();
    for (let i = 0; i < count; i++) {
      const img = images.nth(i);
      const naturalWidth = await img.evaluate((el: HTMLImageElement) => el.naturalWidth);
      const src = await img.getAttribute('src');
      expect(naturalWidth, `Image ${src} is broken`).toBeGreaterThan(0);
    }
  });
});
