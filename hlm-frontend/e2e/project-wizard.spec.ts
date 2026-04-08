import { test, expect } from '@playwright/test';

// storageState (admin@acme.com) is injected by playwright.config.ts → project-wizard-tests.
// No explicit login needed in each test.

test.describe('Project Creation Wizard', () => {
  test('projects page has create button that navigates to wizard', async ({ page }) => {
    await page.goto('/app/projects');
    const createBtn = page.locator('[data-testid="create-project-btn"]');
    await expect(createBtn).toBeVisible({ timeout: 8000 });
    await createBtn.click();
    await page.waitForURL(/\/app\/projects\/new/, { timeout: 8000 });
    await expect(page.locator('.wizard-title')).toBeVisible({ timeout: 8000 });
  });

  test('wizard renders step 1 on load', async ({ page }) => {
    await page.goto('/app/projects/new');
    await expect(page.locator('.wizard-title')).toBeVisible({ timeout: 8000 });
    // Step 1 form fields should be visible
    await expect(page.locator('[data-testid="wizard-project-nom"]')).toBeVisible();
    await expect(page.locator('[data-testid="wizard-project-ville"]')).toBeVisible();
    // Next button should be disabled (form invalid)
    await expect(page.locator('[data-testid="wizard-next"]')).toBeDisabled();
  });

  test('step 1 enables next when required fields filled', async ({ page }) => {
    await page.goto('/app/projects/new');
    const ts = Date.now();
    await page.fill('[data-testid="wizard-project-nom"]', `E2E Résidence ${ts}`);
    await page.fill('[data-testid="wizard-project-ville"]', 'Marseille');
    await expect(page.locator('[data-testid="wizard-next"]')).toBeEnabled({ timeout: 3000 });
  });

  test('full 5-step wizard creates project and navigates to detail', async ({ page }) => {
    await page.goto('/app/projects/new');
    const ts = Date.now();

    // ── Step 1: Project info ─────────────────────────────────────────────────
    await page.fill('[data-testid="wizard-project-nom"]', `E2E Programme ${ts}`);
    await page.fill('[data-testid="wizard-project-ville"]', 'Marseille');
    await page.click('[data-testid="wizard-next"]');

    // ── Step 2: Tranches ─────────────────────────────────────────────────────
    // Default: 1 tranche already exists, fill the delivery date
    await page.fill('[data-testid="wizard-tranche-0-livraison"]', '2026-12-31');
    await expect(page.locator('[data-testid="wizard-next"]')).toBeEnabled({ timeout: 3000 });
    await page.click('[data-testid="wizard-next"]');

    // ── Step 3: Buildings ────────────────────────────────────────────────────
    // Add 1 building to tranche 0
    await page.click('[data-testid="wizard-add-building-0"]');
    // Building card should appear
    await expect(page.locator('.building-card')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[data-testid="wizard-next"]')).toBeEnabled({ timeout: 3000 });
    await page.click('[data-testid="wizard-next"]');

    // ── Step 4: Floor types (auto-generated, just proceed) ───────────────────
    await expect(page.locator('.floor-row').first()).toBeVisible({ timeout: 5000 });
    await page.click('[data-testid="wizard-next"]');

    // ── Step 5: Validation ───────────────────────────────────────────────────
    await expect(page.locator('.preview-table')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.preview-totals-grid')).toBeVisible();
    // Total lots should be > 0
    await expect(page.locator('.total-chip-value').first()).not.toHaveText('0');

    // Generate the project
    await page.click('[data-testid="wizard-generate"]');

    // Should navigate to the project detail page
    await page.waitForURL(/\/app\/projects\/[0-9a-f-]{36}/, { timeout: 20000 });
    // The query param generated=true signals a post-generation state
    await expect(page).toHaveURL(/generated=true/);
  });

  test('wizard back button returns to previous step', async ({ page }) => {
    await page.goto('/app/projects/new');
    const ts = Date.now();
    await page.fill('[data-testid="wizard-project-nom"]', `Back Test ${ts}`);
    await page.fill('[data-testid="wizard-project-ville"]', 'Paris');
    await page.click('[data-testid="wizard-next"]');

    // Now on step 2 — go back
    await page.click('[data-testid="wizard-prev"]');
    // Should be back on step 1 — nom field is visible
    await expect(page.locator('[data-testid="wizard-project-nom"]')).toBeVisible();
    await expect(page.locator('[data-testid="wizard-project-nom"]')).toHaveValue(`Back Test ${ts}`);
  });

  test('wizard shows error for duplicate project name', async ({ page }) => {
    // Create a project first via the wizard
    await page.goto('/app/projects/new');
    const ts = Date.now();
    const nomUnique = `Duplicate Test ${ts}`;

    await page.fill('[data-testid="wizard-project-nom"]', nomUnique);
    await page.fill('[data-testid="wizard-project-ville"]', 'Lyon');
    await page.click('[data-testid="wizard-next"]');

    await page.fill('[data-testid="wizard-tranche-0-livraison"]', '2027-06-30');
    await page.click('[data-testid="wizard-next"]');
    await page.click('[data-testid="wizard-add-building-0"]');
    await page.click('[data-testid="wizard-next"]');
    await page.click('[data-testid="wizard-next"]');

    // First creation should succeed
    await page.click('[data-testid="wizard-generate"]');
    await page.waitForURL(/\/app\/projects\/[0-9a-f-]{36}/, { timeout: 20000 });

    // Now try to create a second project with the same name
    await page.goto('/app/projects/new');
    await page.fill('[data-testid="wizard-project-nom"]', nomUnique);
    await page.fill('[data-testid="wizard-project-ville"]', 'Lyon');
    await page.click('[data-testid="wizard-next"]');
    await page.fill('[data-testid="wizard-tranche-0-livraison"]', '2027-06-30');
    await page.click('[data-testid="wizard-next"]');
    await page.click('[data-testid="wizard-add-building-0"]');
    await page.click('[data-testid="wizard-next"]');
    await page.click('[data-testid="wizard-next"]');
    await page.click('[data-testid="wizard-generate"]');

    // Error message should appear
    await expect(page.locator('[data-testid="wizard-error"]')).toBeVisible({ timeout: 8000 });
  });
});
