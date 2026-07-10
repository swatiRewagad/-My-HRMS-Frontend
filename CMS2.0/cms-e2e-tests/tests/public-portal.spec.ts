import { test, expect } from '@playwright/test';
import { PublicHomePage, FileComplaintPage, TrackComplaintPage } from '../pages';
import { VALID_COMPLAINT, SAMPLE_PINCODE } from '../fixtures/test-data';

const PAUSE = 2500;

test.describe.configure({ mode: 'serial' });

test.describe('Public Portal', () => {
  test.describe('Landing Page', () => {
    test('should display the home page with CMS branding', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await page.waitForTimeout(PAUSE);
      await homePage.expectPageLoaded();
      await page.waitForTimeout(PAUSE);
    });

    test('should have file complaint and track complaint links', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.heroSection).toBeVisible({ timeout: 30000 });
      await page.waitForTimeout(PAUSE);
      await expect(homePage.fileComplaintButton).toBeVisible({ timeout: 5000 });
      await page.waitForTimeout(1000);
      await expect(homePage.trackComplaintButton).toBeVisible({ timeout: 5000 });
      await page.waitForTimeout(PAUSE);
    });

    test('should navigate to file complaint (redirects to login if not authenticated)', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.heroSection).toBeVisible({ timeout: 30000 });
      await page.waitForTimeout(PAUSE);
      await homePage.fileComplaintButton.click({ force: true });
      await page.waitForTimeout(PAUSE);
      await expect(page).toHaveURL(/.*\/(file-complaint|login).*/, { timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });

    test('should navigate to track complaint page', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.heroSection).toBeVisible({ timeout: 30000 });
      await page.waitForTimeout(PAUSE);
      await homePage.navigateToTrackComplaint();
      await page.waitForTimeout(PAUSE);
      await expect(page).toHaveURL(/.*track.*/, { timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });

    test('should have language selector with multiple Indian languages', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await page.waitForTimeout(PAUSE);
      await expect(homePage.languageSelector).toBeVisible({ timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });

    test('should have login link', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await page.waitForTimeout(PAUSE);
      const hamburger = page.locator('button.hamburger-btn');
      if (await hamburger.isVisible()) {
        await hamburger.click();
        await page.locator('nav.main-nav.mobile-open').waitFor({ state: 'visible' });
        await page.waitForTimeout(1000);
      }
      await expect(homePage.loginLink).toBeVisible({ timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });
  });

  test.describe('Eligibility Wizard', () => {
    test('should display the eligibility wizard page', async ({ page }) => {
      await page.goto('/public/eligibility-wizard', { waitUntil: 'networkidle' });
      await page.waitForTimeout(PAUSE);
      await expect(page.locator('.elig-heading, h1, h2').first()).toBeVisible({ timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });

    test('should display entity type selection', async ({ page }) => {
      await page.goto('/public/eligibility-wizard', { waitUntil: 'networkidle' });
      await page.waitForTimeout(PAUSE);
      const selectOrRadio = page.locator('select, .radio-list, .radio-option').first();
      await expect(selectOrRadio).toBeVisible({ timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });

    test('should have navigation buttons', async ({ page }) => {
      await page.goto('/public/eligibility-wizard', { waitUntil: 'networkidle' });
      await page.waitForTimeout(PAUSE);
      const nextBtn = page.locator('button').filter({ hasText: /next|proceed|continue/i }).first();
      await expect(nextBtn).toBeVisible({ timeout: 15000 });
      await page.waitForTimeout(PAUSE);
    });
  });

  test.describe('Track Complaint', () => {
    let trackPage: TrackComplaintPage;

    test.beforeEach(async ({ page }) => {
      trackPage = new TrackComplaintPage(page);
      await trackPage.goto();
      await page.waitForTimeout(PAUSE);
    });

    test('should display the tracking form', async ({ page }) => {
      await expect(trackPage.complaintNumberInput).toBeVisible();
      await page.waitForTimeout(1000);
      await expect(trackPage.searchButton).toBeVisible();
      await page.waitForTimeout(PAUSE);
    });

    test('should allow entering complaint number', async ({ page }) => {
      await trackPage.complaintNumberInput.fill('CMP-20260525-000001');
      await page.waitForTimeout(PAUSE);
      await expect(trackPage.complaintNumberInput).toHaveValue('CMP-20260525-000001');
      await page.waitForTimeout(PAUSE);
    });

    test('should click track button and search', async ({ page }) => {
      await trackPage.complaintNumberInput.fill('CMP-20260525-000001');
      await page.waitForTimeout(1500);
      await trackPage.searchButton.click();
      await page.waitForTimeout(3000);
    });

    test('should show error for invalid complaint number', async ({ page }) => {
      await trackPage.complaintNumberInput.fill('CMP-99999999-999999');
      await page.waitForTimeout(1500);
      await trackPage.searchButton.click();
      await page.waitForTimeout(3000);
      await expect(page.locator('.error-message')).toBeVisible({ timeout: 10000 });
      await page.waitForTimeout(PAUSE);
    });
  });
});
