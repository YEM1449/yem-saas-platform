import { test, expect } from '@playwright/test';

// Note: these tests require a SUPER_ADMIN account in the seed data.
// Seed changeset 046 creates superadmin@yourcompany.com / YourSecure2026!

async function loginSuperAdmin(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/login');
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

  test('regular user cannot access superadmin area', async ({ page }) => {
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(/.*\/app/, { timeout: 15000 });
    await page.goto('/superadmin/societes');
    // Should be redirected away from superadmin
    await expect(page).not.toHaveURL(/.*superadmin\/societes/, { timeout: 8000 });
  });
});
