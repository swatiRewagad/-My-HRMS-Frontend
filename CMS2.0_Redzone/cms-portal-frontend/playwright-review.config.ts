import { defineConfig, devices } from '@playwright/test';

// Slow, watchable, single-worker variant of playwright.config.ts for review runs.
// PW_SLOWMO tunes the pace; PW_HEADED=1 shows the browser.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: Number(process.env['PW_TIMEOUT'] || 120000),
  // json output path comes from PLAYWRIGHT_JSON_OUTPUT_NAME
  reporter: [['line'], ['json']],
  outputDir: process.env['PW_OUTPUT_DIR'] || undefined,
  use: {
    baseURL: process.env['UI_BASE_URL'] || 'http://localhost:4200',
    trace: 'off',
    screenshot: (process.env['PW_SCREENSHOT'] as 'on' | 'only-on-failure') || 'on',
    actionTimeout: Number(process.env['PW_ACTION_TIMEOUT'] || 20000),
    headless: process.env['PW_HEADED'] !== '1',
    launchOptions: { slowMo: Number(process.env['PW_SLOWMO'] || 300) },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
