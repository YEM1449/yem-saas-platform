import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('login with valid credentials redirects to app', async ({ page }) => {
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await expect(page).toHaveURL(/.*app/, { timeout: 10000 });
  });

  test('login with wrong password shows error', async ({ page }) => {
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'wrongpassword');
    await page.click('[data-testid="login-button"]');
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible({ timeout: 5000 });
  });

  test('unauthenticated access to /app redirects to login', async ({ page }) => {
    await page.goto('/app/properties');
    await expect(page).toHaveURL(/.*login/, { timeout: 5000 });
  });

  test('logout clears session and redirects to login', async ({ page }) => {
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await expect(page).toHaveURL(/.*app/, { timeout: 10000 });
    await page.click('[data-testid="logout-button"]');
    await expect(page).toHaveURL(/.*login/, { timeout: 5000 });
  });
});
