import { test, expect } from '@playwright/test';

// storageState (admin@acme.com) is injected by playwright.config.ts → project-wizard-tests.
// No explicit login needed in each test.

async function waitForWizardStep(
  page: import('@playwright/test').Page,
  step: number
): Promise<void> {
  await expect(page.getByTestId(`wizard-step-${step}`)).toBeVisible({ timeout: 10000 });
}

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
    await waitForWizardStep(page, 1);
    // Step 1 form fields should be visible
    await expect(page.locator('[data-testid="wizard-project-nom"]')).toBeVisible();
    await expect(page.locator('[data-testid="wizard-project-ville"]')).toBeVisible();
    // Next button should be disabled (form invalid)
    await expect(page.locator('[data-testid="wizard-next"]')).toBeDisabled();
  });

  test('step 1 enables next when required fields filled', async ({ page }) => {
    await page.goto('/app/projects/new');
    await waitForWizardStep(page, 1);
    const ts = Date.now();
    await page.fill('[data-testid="wizard-project-nom"]', `E2E Résidence ${ts}`);
    await page.fill('[data-testid="wizard-project-ville"]', 'Marseille');
    await expect(page.locator('[data-testid="wizard-next"]')).toBeEnabled({ timeout: 3000 });
  });

  test('full 5-step wizard creates project and navigates to detail', async ({ page }) => {
    await page.goto('/app/projects/new');
    await waitForWizardStep(page, 1);
    const ts = Date.now();
    const projectName = `E2E Programme ${ts}`;

    // ── Step 1: Project info ─────────────────────────────────────────────────
    await page.fill('[data-testid="wizard-project-nom"]', projectName);
    await page.fill('[data-testid="wizard-project-ville"]', 'Marseille');
    await page.click('[data-testid="wizard-next"]');

    // ── Step 2: Tranches ─────────────────────────────────────────────────────
    await waitForWizardStep(page, 2);
    // Default: 1 tranche already exists, fill the delivery date
    await page.fill('[data-testid="wizard-tranche-0-livraison"]', '2026-12-31');
    await expect(page.locator('[data-testid="wizard-next"]')).toBeEnabled({ timeout: 3000 });
    await page.click('[data-testid="wizard-next"]');

    // ── Step 3: Buildings ────────────────────────────────────────────────────
    await waitForWizardStep(page, 3);
    // Add 1 building to tranche 0
    await page.click('[data-testid="wizard-add-building-0"]');
    // Building card should appear
    await expect(page.locator('.building-card')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[data-testid="wizard-next"]')).toBeEnabled({ timeout: 3000 });
    await page.click('[data-testid="wizard-next"]');

    // ── Step 4: Floor types (auto-generated, just proceed) ───────────────────
    await waitForWizardStep(page, 4);
    await expect(page.locator('.floor-row').first()).toBeVisible({ timeout: 5000 });
    await page.click('[data-testid="wizard-next"]');

    // ── Step 5: Validation ───────────────────────────────────────────────────
    await waitForWizardStep(page, 5);
    await expect(page.locator('.preview-table')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.preview-totals-grid')).toBeVisible();
    // Total lots should be > 0
    await expect(page.getByTestId('wizard-total-units')).not.toHaveText('0');

    // Generate the project
    const generationResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      response.url().includes('/api/projects/generate')
    );
    await page.click('[data-testid="wizard-generate"]');
    const generationResponse = await generationResponsePromise;
    expect(generationResponse.status()).toBe(201);
    const generationBody = await generationResponse.json() as { projectId: string };

    // Should navigate to the project detail page
    await page.waitForURL(new RegExp(`/app/projects/${generationBody.projectId}\\?generated=true`), {
      timeout: 30000,
    });
    // The query param generated=true signals a post-generation state
    await expect(page).toHaveURL(/generated=true/);
    await expect(page.getByTestId('project-detail-title')).toHaveText(projectName, { timeout: 10000 });
  });

  test('wizard back button returns to previous step', async ({ page }) => {
    await page.goto('/app/projects/new');
    await waitForWizardStep(page, 1);
    const ts = Date.now();
    await page.fill('[data-testid="wizard-project-nom"]', `Back Test ${ts}`);
    await page.fill('[data-testid="wizard-project-ville"]', 'Paris');
    await page.click('[data-testid="wizard-next"]');

    // Now on step 2 — go back
    await waitForWizardStep(page, 2);
    await page.click('[data-testid="wizard-prev"]');
    // Should be back on step 1 — nom field is visible
    await waitForWizardStep(page, 1);
    await expect(page.locator('[data-testid="wizard-project-nom"]')).toBeVisible();
    await expect(page.locator('[data-testid="wizard-project-nom"]')).toHaveValue(`Back Test ${ts}`);
  });

  test('wizard shows error for duplicate project name', async ({ page }) => {
    // Create a project first via the wizard
    await page.goto('/app/projects/new');
    await waitForWizardStep(page, 1);
    const ts = Date.now();
    const nomUnique = `Duplicate Test ${ts}`;

    await page.fill('[data-testid="wizard-project-nom"]', nomUnique);
    await page.fill('[data-testid="wizard-project-ville"]', 'Lyon');
    await page.click('[data-testid="wizard-next"]');

    await waitForWizardStep(page, 2);
    await page.fill('[data-testid="wizard-tranche-0-livraison"]', '2027-06-30');
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 3);
    await page.click('[data-testid="wizard-add-building-0"]');
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 4);
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 5);

    // First creation should succeed
    const firstGenerationResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      response.url().includes('/api/projects/generate')
    );
    await page.click('[data-testid="wizard-generate"]');
    const firstGenerationResponse = await firstGenerationResponsePromise;
    expect(firstGenerationResponse.status()).toBe(201);
    const firstGenerationBody = await firstGenerationResponse.json() as { projectId: string };
    await page.waitForURL(new RegExp(`/app/projects/${firstGenerationBody.projectId}`), {
      timeout: 30000,
    });

    // Now try to create a second project with the same name
    await page.goto('/app/projects/new');
    await waitForWizardStep(page, 1);
    await page.fill('[data-testid="wizard-project-nom"]', nomUnique);
    await page.fill('[data-testid="wizard-project-ville"]', 'Lyon');
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 2);
    await page.fill('[data-testid="wizard-tranche-0-livraison"]', '2027-06-30');
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 3);
    await page.click('[data-testid="wizard-add-building-0"]');
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 4);
    await page.click('[data-testid="wizard-next"]');
    await waitForWizardStep(page, 5);
    const duplicateGenerationResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      response.url().includes('/api/projects/generate')
    );
    await page.click('[data-testid="wizard-generate"]');
    const duplicateGenerationResponse = await duplicateGenerationResponsePromise;
    expect(duplicateGenerationResponse.status()).toBe(409);
    const duplicateGenerationBody = await duplicateGenerationResponse.json() as { code: string };
    expect(duplicateGenerationBody.code).toBe('PROJECT_NAME_EXISTS');

    // Error message should appear
    await expect(page.locator('[data-testid="wizard-error"]')).toBeVisible({ timeout: 8000 });
  });
});
