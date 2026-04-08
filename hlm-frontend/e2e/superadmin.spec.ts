import { test, expect } from '@playwright/test';
import * as path from 'path';

// Note: these tests require a SUPER_ADMIN account in the seed data.
// Seed changeset 046 creates superadmin@yourcompany.com / YourSecure2026!

const authFile = path.join(__dirname, '../playwright/.auth/admin.json');

async function loginSuperAdmin(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/login');
  await page.waitForSelector('[data-testid="email"]', { timeout: 30000 });
  await page.fill('[data-testid="email"]', 'superadmin@yourcompany.com');
  await page.fill('[data-testid="password"]', 'YourSecure2026!');
  await page.click('[data-testid="login-button"]');
  await page.waitForURL(/.*superadmin/, { timeout: 15000 });
}

test.describe('SuperAdmin — Société management', () => {
  test('super admin login redirects to /superadmin/societes', async ({ page }) => {
    await loginSuperAdmin(page);
    await expect(page).toHaveURL(/.*superadmin\/societes/, { timeout: 8000 });
  });

  test('societe list shows at least one entry', async ({ page }) => {
    await loginSuperAdmin(page);
    await expect(page.locator('table tbody tr, .societe-item').first()).toBeVisible({ timeout: 8000 });
  });
});

// This test uses the admin storageState (set up by auth.setup.ts) to avoid
// an extra login attempt that can trigger the per-identity rate limiter.
test.describe('SuperAdmin — Access control', () => {
  test.use({ storageState: authFile });

  test('regular user cannot access superadmin area', async ({ page }) => {
    // Already logged in as admin@acme.com via storageState — no fresh login needed.
    await page.goto('/superadmin/societes');
    // Should be redirected away from superadmin (guard fires)
    await expect(page).not.toHaveURL(/.*superadmin\/societes/, { timeout: 8000 });
  });
});
