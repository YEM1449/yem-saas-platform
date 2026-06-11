import { test, expect } from '@playwright/test';

/**
 * Wave 12 — VEFA (Loi 44-00) smoke E2E. Uses the shared admin storageState (project
 * dependency on `setup`), so no per-test login. Focuses on the new P6 treasury dashboard,
 * which exercises routing + auth + the GET /api/dashboard/tresorerie endpoint end-to-end.
 *
 * The full OPTION → réservation → rétractation and livraison-avec-réserves flows are
 * documented as CI/manual scenarios (they require staged vente state); see
 * docs/legal/guide-loi-44-00-managers.md.
 */
test.describe('VEFA treasury dashboard', () => {
  test('treasury dashboard loads with KPI cards', async ({ page }) => {
    await page.goto('/app/dashboard/tresorerie');
    await expect(page.getByRole('heading', { name: 'Trésorerie VEFA' })).toBeVisible({ timeout: 15000 });
    // Either the KPI row (data present) or the empty/skeleton state must render — never an error throw.
    await expect(page.locator('text=À encaisser').or(page.locator('.empty-state'))).toBeVisible({ timeout: 15000 });
  });

  test('treasury is reachable from the sidebar nav', async ({ page }) => {
    await page.goto('/app/dashboard');
    const navLink = page.locator('a[href="/app/dashboard/tresorerie"]');
    await expect(navLink.first()).toBeVisible({ timeout: 15000 });
    await navLink.first().click();
    await expect(page).toHaveURL(/.*\/app\/dashboard\/tresorerie/);
  });
});
