import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  timeout: 60000,
  reporter: 'html',
  outputDir: process.env['PW_OUTPUT_DIR'] || undefined,
  use: {
    baseURL: process.env['UI_BASE_URL'] || 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: (process.env['PW_SCREENSHOT'] as 'on' | 'only-on-failure') || 'only-on-failure',
    actionTimeout: 15000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
