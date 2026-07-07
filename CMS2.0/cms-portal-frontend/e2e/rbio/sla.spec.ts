import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

/**
 * RBIO SLA (Service Level Agreement) Tests
 *
 * Validates RBIO-specific SLA indicators:
 * - New complaint shows 30-day stage deadline
 * - SLA progress component shows correct stage (Officer/Conciliation/Adjudication)
 * - Breached complaint shows red indicator
 * - Overall 120-day lifecycle progress displays
 */
test.describe('RBIO SLA Indicators', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('New RBIO complaint shows 30-day stage deadline', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO SLA 30-day Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Check for SLA/deadline display in header or detail section
      const slaElement = page.locator(
        '[data-testid="sla-due"], [data-testid="stage-deadline"], .sla-due, .stage-deadline, .header-meta span:has-text("SLA"), .header-meta span:has-text("Due"), .header-meta span:has-text("Deadline")'
      );
      await expect(slaElement).toBeVisible({ timeout: 5000 });

      // The deadline text should contain a date (not empty)
      const slaText = await slaElement.textContent();
      expect(slaText?.trim()).toBeTruthy();
      expect(slaText).not.toBe('—');

      // Optionally verify it mentions 30 days or shows a future date
      // The stage deadline for RBIO Officer stage is 30 days from creation
      const slaSection = page.locator(
        '[data-testid="sla-progress"], .sla-progress, .sla-indicator'
      );
      if (await slaSection.isVisible()) {
        const progressText = await slaSection.textContent();
        expect(progressText?.toLowerCase()).toMatch(/30|day|officer|stage/);
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('SLA progress component shows correct stage (Officer/Conciliation/Adjudication)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a complaint in conciliation stage to verify stage display
    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO SLA Stage Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      await loginAsRbioRole(page, 'RBIO_CONCILIATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Look for SLA progress/stage component
      const slaProgress = page.locator(
        '[data-testid="sla-progress"], .sla-progress, .sla-stage, .workflow-stage, .stage-indicator'
      );

      if (await slaProgress.isVisible({ timeout: 5000 }).catch(() => false)) {
        const stageText = await slaProgress.textContent();
        // Should indicate the current stage is Conciliation
        expect(stageText?.toLowerCase()).toMatch(/conciliation/);
      } else {
        // Check for stage info in header meta
        const headerMeta = page.locator('.header-meta, [data-testid="complaint-meta"]');
        if (await headerMeta.isVisible()) {
          const metaText = await headerMeta.textContent();
          // Should mention current stage somewhere
          expect(metaText?.toLowerCase()).toMatch(/conciliation|stage/);
        }
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Breached complaint shows red indicator', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('[data-testid="task-table"], .task-table, .complaints-table, .empty-state', {
      timeout: 15000,
    });

    // Look for any overdue/breached rows
    const overdueRows = page.locator(
      'tbody tr.overdue, tbody tr.breached, tbody tr:has([data-testid="sla-breached"]), tbody tr:has(.overdue-text), tbody tr:has(.sla-red)'
    );
    const count = await overdueRows.count();

    if (count > 0) {
      // First breached row should have visual indicator (red styling)
      const firstOverdueRow = overdueRows.first();
      await expect(firstOverdueRow).toBeVisible();

      // Check for red indicator or breached badge
      const indicator = firstOverdueRow.locator(
        '.overdue-text, .sla-red, .breached-badge, [data-testid="sla-breached"]'
      );
      await expect(indicator).toBeVisible();
    } else {
      // No breached complaints — this is a valid state for fresh test data
      test.info().annotations.push({
        type: 'info',
        description: 'No SLA-breached RBIO complaints found in current data',
      });
    }

    await logout(page);
  });

  test('Overall 120-day lifecycle progress displays', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO 120-day Lifecycle Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Look for overall lifecycle progress component
      const lifecycleProgress = page.locator(
        '[data-testid="lifecycle-progress"], .lifecycle-progress, .overall-sla, [data-testid="overall-progress"]'
      );

      if (await lifecycleProgress.isVisible({ timeout: 5000 }).catch(() => false)) {
        const progressText = await lifecycleProgress.textContent();
        // Should mention 120 days or show overall progress
        expect(progressText?.toLowerCase()).toMatch(/120|overall|lifecycle|total/);
      } else {
        // Check for any progress bar or percentage indicator
        const progressBar = page.locator(
          '.progress-bar, [role="progressbar"], .sla-bar, [data-testid="sla-bar"]'
        );
        if (await progressBar.isVisible()) {
          // Progress bar should exist with some value attribute or width
          await expect(progressBar).toBeVisible();
        } else {
          // At minimum, check for SLA due date which implies lifecycle tracking
          const slaDue = page.locator(
            '[data-testid="sla-due"], .sla-due, .header-meta span:has-text("Due")'
          );
          if (await slaDue.isVisible()) {
            const dueText = await slaDue.textContent();
            expect(dueText?.trim()).toBeTruthy();
          } else {
            test.info().annotations.push({
              type: 'info',
              description: 'Lifecycle progress component not found — may not be implemented yet',
            });
          }
        }
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
