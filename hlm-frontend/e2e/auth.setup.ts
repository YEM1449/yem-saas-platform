import { test as setup } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const authDir  = path.join(__dirname, '../playwright/.auth');
const authFile = path.join(authDir, 'admin.json');

setup('authenticate as admin', async ({ page }) => {
  // Ensure storage directory exists before saving state
  fs.mkdirSync(authDir, { recursive: true });

  await page.goto('/login');
  await page.fill('[data-testid="email"]',    'admin@acme.com');
  await page.fill('[data-testid="password"]', 'Admin123!Secure');
  await page.click('[data-testid="login-button"]');
  await page.waitForURL(/.*\/app/, { timeout: 20000 });

  // Persist token in localStorage so subsequent tests skip the login form
  await page.context().storageState({ path: authFile });
});
