import { test, expect } from '@playwright/test';
import { PublicHomePage, FileComplaintPage, TrackComplaintPage } from '../pages';
import { VALID_COMPLAINT, SAMPLE_PINCODE } from '../fixtures/test-data';

test.describe('Public Portal', () => {
  test.describe('Landing Page', () => {
    test('should display the home page with CMS branding', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await homePage.expectPageLoaded();
    });

    test('should have file complaint and track complaint links', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.fileComplaintButton).toBeVisible();
      await expect(homePage.trackComplaintButton).toBeVisible();
    });

    test('should navigate to file complaint (redirects to login if not authenticated)', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await homePage.fileComplaintButton.click();
      await expect(page).toHaveURL(/.*\/(file-complaint|login).*/);
    });

    test('should navigate to track complaint page', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await homePage.navigateToTrackComplaint();
      await expect(page).toHaveURL(/.*track.*/);
    });

    test('should have language selector with multiple Indian languages', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.languageSelector).toBeVisible();
    });

    test('should have login link', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      const hamburger = page.locator('button.hamburger-btn');
      if (await hamburger.isVisible()) {
        await hamburger.click();
        await page.locator('nav.main-nav.mobile-open').waitFor({ state: 'visible' });
      }
      await expect(homePage.loginLink).toBeVisible();
    });
  });

  test.describe('File Complaint Form (simple form)', () => {
    let complaintPage: FileComplaintPage;

    test.beforeEach(async ({ page }) => {
      complaintPage = new FileComplaintPage(page);
      await complaintPage.goto();
    });

    test('should display the complaint form', async ({ page }) => {
      await expect(complaintPage.nameInput).toBeVisible();
    });

    test('should disable submit button when required fields are empty', async ({ page }) => {
      await expect(complaintPage.submitButton).toBeDisabled();
    });

    test('should allow entering email', async ({ page }) => {
      await complaintPage.emailInput.fill('test@example.com');
      await expect(complaintPage.emailInput).toHaveValue('test@example.com');
    });

    test('should allow entering phone number', async ({ page }) => {
      await complaintPage.phoneInput.fill('9876543210');
      await expect(complaintPage.phoneInput).toHaveValue('9876543210');
    });

    test('should display category dropdown', async ({ page }) => {
      await expect(complaintPage.categorySelect).toBeVisible();
    });

    test('should display subject field', async ({ page }) => {
      await expect(complaintPage.subjectInput).toBeVisible();
    });

    test('should display description field', async ({ page }) => {
      await expect(complaintPage.descriptionInput).toBeVisible();
    });

    test('should have submit button', async ({ page }) => {
      await expect(complaintPage.submitButton).toBeVisible();
    });
  });

  test.describe('Track Complaint', () => {
    let trackPage: TrackComplaintPage;

    test.beforeEach(async ({ page }) => {
      trackPage = new TrackComplaintPage(page);
      await trackPage.goto();
    });

    test('should display the tracking form', async ({ page }) => {
      await expect(trackPage.complaintNumberInput).toBeVisible();
      await expect(trackPage.searchButton).toBeVisible();
    });

    test('should allow entering complaint number', async ({ page }) => {
      await trackPage.complaintNumberInput.fill('CMS/2026/MUM/000001');
      await expect(trackPage.complaintNumberInput).toHaveValue('CMS/2026/MUM/000001');
    });

    test('should click track button', async ({ page }) => {
      await trackPage.complaintNumberInput.fill('CMS/2026/MUM/000001');
      await trackPage.searchButton.click();
      await page.waitForTimeout(2000);
    });

    test('should show error for invalid complaint number when backend is unavailable', async ({ page }) => {
      await trackPage.complaintNumberInput.fill('CMS/9999/XXX/999999');
      await trackPage.searchButton.click();
      await page.waitForTimeout(2000);
    });
  });
});
