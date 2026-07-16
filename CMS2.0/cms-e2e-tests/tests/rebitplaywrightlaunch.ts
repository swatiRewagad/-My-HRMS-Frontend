import { test, expect } from '@playwright/test';
import { RebitHomePage } from '../pages/rebit-home.page';

test.describe('ReBIT Website - Our Vision', () => {

  test('should navigate to Our Vision page via Know More', async ({ page }) => {
    const rebitHome = new RebitHomePage(page);

    await rebitHome.goto();
    await expect(page).toHaveURL(/rebit\.org\.in/);

    await rebitHome.clickKnowMore();
    await rebitHome.expectOurVisionPageLoaded();
  });

});
