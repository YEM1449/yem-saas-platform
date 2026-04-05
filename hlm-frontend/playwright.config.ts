import { defineConfig } from '@playwright/test';
import * as path from 'path';

const authFile = path.join(__dirname, 'playwright/.auth/admin.json');

export default defineConfig({
  testDir: './e2e',
  timeout: 45000,
  retries: 1,
  workers: 1,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
    // Angular dev-server can be slower in CI; allow more time per action
    actionTimeout: 15000,
    navigationTimeout: 20000,
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'auth-tests',
      testMatch: /(auth|superadmin)\.spec\.ts/,
    },
    {
      name: 'crm-tests',
      testMatch: /(contacts|tasks)\.spec\.ts/,
      dependencies: ['setup'],
      use: { storageState: authFile },
    },
    // activation.spec.ts does its own login where needed; no shared storageState required
    {
      name: 'activation-tests',
      testMatch: /activation\.spec\.ts/,
    },
    // pipeline.spec.ts does its own login in beforeEach; no shared storageState required
    {
      name: 'pipeline-tests',
      testMatch: /pipeline\.spec\.ts/,
    },
  ],
  // In CI the static server is started by the workflow before Playwright runs.
  // Setting undefined skips webServer so Playwright never launches `npm start`
  // (ng serve), which would race against Angular compilation and cause flaky
  // timeouts. Locally, webServer starts `npm start` automatically.
  webServer: process.env['CI']
    ? undefined
    : {
        command: 'npm start',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 120000,
      },
});
