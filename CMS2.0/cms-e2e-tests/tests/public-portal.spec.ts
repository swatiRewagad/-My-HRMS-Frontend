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

    test('should have file complaint and track complaint buttons', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.fileComplaintButton).toBeVisible();
      await expect(homePage.trackComplaintButton).toBeVisible();
    });

    test('should navigate to file complaint form', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await homePage.navigateToFileComplaint();
      await expect(page).toHaveURL(/.*file-complaint.*/);
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

    test('should have staff login link', async ({ page }) => {
      const homePage = new PublicHomePage(page);
      await homePage.goto();
      await expect(homePage.loginLink).toBeVisible();
    });
  });

  test.describe('File Complaint Form', () => {
    let complaintPage: FileComplaintPage;

    test.beforeEach(async ({ page }) => {
      complaintPage = new FileComplaintPage(page);
      await complaintPage.goto();
    });

    test('should display the multi-step complaint form', async ({ page }) => {
      await expect(complaintPage.nameInput).toBeVisible();
    });

    test('should validate required fields on submission', async ({ page }) => {
      await complaintPage.submit();
      await expect(page.getByText(/required/i)).toBeVisible();
    });

    test('should validate email format', async ({ page }) => {
      await complaintPage.emailInput.fill('invalid-email');
      await complaintPage.emailInput.blur();
      await expect(page.getByText(/valid.*email|email.*invalid/i)).toBeVisible();
    });

    test('should validate phone number format (Indian 10-digit)', async ({ page }) => {
      await complaintPage.phoneInput.fill('12345');
      await complaintPage.phoneInput.blur();
      await expect(page.getByText(/valid.*phone|10.*digit/i)).toBeVisible();
    });

    test('should auto-fill district/state from pincode', async ({ page }) => {
      await complaintPage.fillPincode(SAMPLE_PINCODE.pincode);
      await expect(page.getByText(new RegExp(SAMPLE_PINCODE.expectedDistrict, 'i'))).toBeVisible();
      await expect(page.getByText(new RegExp(SAMPLE_PINCODE.expectedState, 'i'))).toBeVisible();
    });

    test('should show RE complaint date field when prior complaint is Yes', async ({ page }) => {
      await complaintPage.priorComplaintYes.click();
      await expect(complaintPage.reComplaintDateInput).toBeVisible();
    });

    test('should hide RE complaint date when prior complaint is No', async ({ page }) => {
      await complaintPage.priorComplaintNo.click();
      await expect(complaintPage.reComplaintDateInput).not.toBeVisible();
    });

    test('should submit a valid complaint successfully', async ({ page }) => {
      await complaintPage.fillPersonalDetails({
        name: VALID_COMPLAINT.complainantName,
        email: VALID_COMPLAINT.complainantEmail,
        phone: VALID_COMPLAINT.complainantPhone,
        address: VALID_COMPLAINT.complainantAddress,
      });
      await complaintPage.fillComplaintDetails({
        category: 'ATM/Debit Card',
        subject: VALID_COMPLAINT.subject,
        description: VALID_COMPLAINT.description,
        relief: VALID_COMPLAINT.reliefSought,
      });
      await complaintPage.fillPriorComplaint({
        priorComplaint: true,
        date: VALID_COMPLAINT.reComplaintDate,
        reference: VALID_COMPLAINT.reComplaintReference,
        replied: true,
      });
      await complaintPage.submit();
      await complaintPage.expectSuccess();
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

    test('should find complaint by number', async () => {
      await trackPage.trackByNumber('CMS/2026/MUM/000001');
      await trackPage.expectResultFound();
    });

    test('should show status for tracked complaint', async () => {
      await trackPage.trackByNumber('CMS/2026/MUM/000001');
      await trackPage.expectStatus('in_progress');
    });

    test('should show no result for invalid complaint number', async () => {
      await trackPage.trackByNumber('CMS/9999/XXX/999999');
      await trackPage.expectNoResult();
    });

    test('should find complaints by email', async () => {
      await trackPage.trackByEmail('rajesh.kumar@gmail.com');
      await trackPage.expectResultFound();
    });
  });
});
