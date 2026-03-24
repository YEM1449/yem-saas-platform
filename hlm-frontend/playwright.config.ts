import { defineConfig } from '@playwright/test';
import * as path from 'path';

const authFile = path.join(__dirname, 'playwright/.auth/admin.json');

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  retries: 1,
  workers: 1,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
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
  ],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 120000,
  },
});
