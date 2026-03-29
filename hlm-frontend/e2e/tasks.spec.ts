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

test.describe('Tasks', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('tasks page is accessible at /app/tasks', async ({ page }) => {
    await page.goto('/app/tasks');
    await expect(page.locator('h2')).toBeVisible({ timeout: 8000 });
  });

  test('tasks link appears in sidebar navigation', async ({ page }) => {
    await page.goto('/app/properties');
    const tasksLink = page.locator('a[href="/app/tasks"]');
    await expect(tasksLink).toBeVisible({ timeout: 8000 });
  });

  test('create task via tasks page', async ({ page }) => {
    await page.goto('/app/tasks');
    await expect(page.locator('[data-testid="new-task-btn"]')).toBeVisible({ timeout: 8000 });
    await page.click('[data-testid="new-task-btn"]');
    const ts = Date.now();
    await page.fill('[data-testid="task-title"]', `Tâche E2E ${ts}`);
    await page.click('[data-testid="task-submit"]');
    await expect(page.locator(`text=Tâche E2E ${ts}`)).toBeVisible({ timeout: 10000 });
  });

  test('status filter works on tasks page', async ({ page }) => {
    await page.goto('/app/tasks');
    await expect(page.locator('[data-testid="status-filter"]')).toBeVisible({ timeout: 8000 });
    await page.selectOption('[data-testid="status-filter"]', 'OPEN');
    await page.waitForTimeout(500);
    // Page should still be on tasks (filter applied without error)
    await expect(page.locator('h2')).toBeVisible();
  });
});
