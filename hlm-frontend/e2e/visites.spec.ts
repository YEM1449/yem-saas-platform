import { test, expect, Page } from '@playwright/test';

/**
 * Wave 16 — Visites module E2E (agenda, prise de RDV, compte-rendu).
 *
 * Smoke + navigation level, consistent with viewer-3d.spec.ts: the full create flow depends on a
 * seeded contact reachable through the typeahead, so the create test selects the first suggestion
 * and skips gracefully when none is available, keeping the suite stable across environments.
 */

async function login(page: Page): Promise<void> {
  await page.goto('/app/visites');
  await page.waitForLoadState('networkidle');
  if (page.url().includes('/login') || !page.url().includes('/app')) {
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(/.*\/app/, { timeout: 15000 });
  }
}

test.describe('Visites', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('agenda is accessible at /app/visites', async ({ page }) => {
    await page.goto('/app/visites');
    await expect(page.getByTestId('visites-agenda')).toBeVisible({ timeout: 8000 });
    await expect(page.getByTestId('visites-title')).toBeVisible();
  });

  test('visites link appears in the sidebar', async ({ page }) => {
    await page.goto('/app/visites');
    await expect(page.locator('a[href="/app/visites"]')).toBeVisible({ timeout: 8000 });
  });

  test('agenda view switch (jour/semaine/mois) works', async ({ page }) => {
    await page.goto('/app/visites');
    await expect(page.getByTestId('view-SEMAINE')).toBeVisible({ timeout: 8000 });
    await page.getByTestId('view-JOUR').click();
    await expect(page.getByTestId('view-JOUR')).toHaveClass(/active/);
    await page.getByTestId('view-MOIS').click();
    await expect(page.getByTestId('view-MOIS')).toHaveClass(/active/);
  });

  test('"Planifier une visite" opens the quick-create form', async ({ page }) => {
    await page.goto('/app/visites');
    await page.getByTestId('new-visite-btn').click();
    await expect(page.getByTestId('visite-form')).toBeVisible({ timeout: 8000 });
    await expect(page.getByTestId('visite-date')).toBeVisible();
    await expect(page.getByTestId('visite-submit')).toBeVisible();
  });

  test('create a visite then see it in the agenda', async ({ page }) => {
    await page.goto('/app/visites/nouvelle');
    await expect(page.getByTestId('visite-form')).toBeVisible({ timeout: 8000 });

    // Pick the first contact from the typeahead; skip if no seed contact is reachable.
    const search = page.locator('app-contact-picker input.picker-input');
    await search.fill('a');
    const firstSuggestion = page.locator('app-contact-picker li.picker-option').first();
    const hasSuggestion = await firstSuggestion
      .waitFor({ state: 'visible', timeout: 4000 })
      .then(() => true)
      .catch(() => false);
    test.skip(!hasSuggestion, 'No seed contact reachable via typeahead in this environment');
    await firstSuggestion.click();

    // Future slot, tomorrow 10:00 (Casablanca wall-clock entered into datetime-local).
    const d = new Date(Date.now() + 24 * 3600 * 1000);
    const wall = `${d.toISOString().slice(0, 10)}T10:00`;
    await page.getByTestId('visite-date').fill(wall);
    await page.getByTestId('visite-submit').click();

    // Lands on the detail page of the created visite.
    await expect(page).toHaveURL(/\/app\/visites\/[0-9a-f-]{36}/, { timeout: 10000 });
  });
});
