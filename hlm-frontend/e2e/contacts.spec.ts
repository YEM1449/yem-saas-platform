import { test, expect } from '@playwright/test';

async function login(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/app/properties');
  await page.waitForLoadState('networkidle');
  // If storageState was injected, we are already authenticated — skip login form
  if (page.url().includes('/login') || !page.url().includes('/app')) {
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(/.*\/app/, { timeout: 15000 });
  }
}

test.describe('Contacts', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('contacts page loads and shows list', async ({ page }) => {
    await page.goto('/app/contacts');
    await expect(page.getByTestId('contacts-page-title')).toBeVisible({ timeout: 8000 });
  });

  test('navigate to contacts page from sidebar', async ({ page }) => {
    await page.goto('/app/properties');
    await page.click('a[href="/app/contacts"]');
    await expect(page).toHaveURL(/.*contacts/, { timeout: 8000 });
  });

  test('create contact appears in list', async ({ page }) => {
    await page.goto('/app/contacts');
    // Open create modal
    await expect(page.locator('[data-testid="create-contact"]')).toBeVisible({ timeout: 8000 });
    await page.click('[data-testid="create-contact"]');

    const ts = Date.now();
    const email = `e2e-contact-${ts}@example.com`;
    await page.fill('[data-testid="firstName"]', 'E2E');
    await page.fill('[data-testid="lastName"]', `Test-${ts}`);
    await page.getByPlaceholder('jean.dupont@example.com').fill(email);
    await page.click('[data-testid="save-button"]');

    // After creation the app navigates to the contact detail page — verify that
    await page.waitForURL(/\/app\/contacts\/[^/]+$/, { timeout: 10000 });
    await expect(page.getByRole('heading', { name: `E2E Test-${ts}`, exact: false })).toBeVisible({ timeout: 8000 });
  });
});
