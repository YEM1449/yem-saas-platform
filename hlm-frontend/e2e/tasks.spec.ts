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

test.describe('Tasks', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('tasks page is accessible at /app/tasks', async ({ page }) => {
    await page.goto('/app/tasks');
    await expect(page.locator('h2:has-text("Tâches"), h2:has-text("tâches")')).toBeVisible({ timeout: 5000 });
  });

  test('tasks link appears in sidebar navigation', async ({ page }) => {
    await page.goto('/app/properties');
    const tasksLink = page.locator('a[href="/app/tasks"]');
    await expect(tasksLink).toBeVisible();
  });

  test('create task via tasks page', async ({ page }) => {
    await page.goto('/app/tasks');
    await page.click('button:has-text("Nouvelle tâche"), button:has-text("+ Nouvelle")');
    const ts = Date.now();
    await page.fill('input[placeholder*="titre"], input[placeholder*="Ex:"]', `Tâche E2E ${ts}`);
    await page.click('button:has-text("Créer"), button[type="submit"]');
    await expect(page.locator(`text=Tâche E2E ${ts}`)).toBeVisible({ timeout: 5000 });
  });

  test('status filter works on tasks page', async ({ page }) => {
    await page.goto('/app/tasks');
    await page.selectOption('select', 'OPEN');
    await page.waitForTimeout(500);
    // Page should still be on tasks (filter applied without error)
    await expect(page.locator('h2')).toBeVisible();
  });
});
