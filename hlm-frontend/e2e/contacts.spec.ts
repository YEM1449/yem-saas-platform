import { test, expect } from '@playwright/test';

async function login(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/app/properties');
  // If storageState was injected, we are already authenticated — skip login form
  if (page.url().includes('/login') || !page.url().includes('/app')) {
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(/.*app/, { timeout: 10000 });
  }
}

test.describe('Contacts', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('contacts page loads and shows list', async ({ page }) => {
    await page.goto('/app/contacts');
    await expect(page.locator('h2, h1')).toBeVisible({ timeout: 5000 });
  });

  test('navigate to contacts page from sidebar', async ({ page }) => {
    await page.goto('/app/properties');
    await page.click('a[href="/app/contacts"]');
    await expect(page).toHaveURL(/.*contacts/, { timeout: 5000 });
  });

  test('create contact appears in list', async ({ page }) => {
    await page.goto('/app/contacts');
    // Open create modal
    await expect(page.locator('[data-testid="create-contact"]')).toBeVisible({ timeout: 5000 });
    await page.click('[data-testid="create-contact"]');

    const ts = Date.now();
    await page.fill('[data-testid="firstName"]', 'E2E');
    await page.fill('[data-testid="lastName"]', `Test-${ts}`);
    await page.click('[data-testid="save-button"]');

    // Wait for modal to close (success path closes it) then assert row is visible
    await expect(page.locator('.modal-backdrop')).toBeHidden({ timeout: 5000 });
    await expect(page.locator(`text=Test-${ts}`)).toBeVisible({ timeout: 5000 });
  });
});
