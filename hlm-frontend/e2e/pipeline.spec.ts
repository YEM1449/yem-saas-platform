import { test, expect } from '@playwright/test';

// In CI the Angular build uses apiUrl='http://localhost:8080' (environment.ci.ts),
// but page.request uses playwright's baseURL ('http://localhost:4200') which is the
// Python SPA static server — it only handles GET.  API write/read calls must go
// directly to the backend.
const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '';

// ── Auth helper ────────────────────────────────────────────────────────────

async function loginAsAdmin(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/login');
  await page.waitForSelector('[data-testid="email"]', { timeout: 30000 });
  await page.fill('[data-testid="email"]', 'admin@acme.com');
  await page.fill('[data-testid="password"]', 'Admin123!Secure');
  await page.click('[data-testid="login-button"]');
  await page.waitForURL(/.*\/app/, { timeout: 15000 });
}

// ── Data-setup helpers (use page.request so auth cookies are shared) ────────

async function createTestVente(
  page: import('@playwright/test').Page
): Promise<{ id: string; statut: string }> {
  const ts = Date.now();

  // 1. Project (property requires projectId)
  const projRes = await page.request.post(`${API_BASE}/api/projects`, {
    data: { name: `E2E Pipeline Project ${ts}`, description: null },
  });
  expect(projRes.status()).toBe(201);
  const proj = await projRes.json() as { id: string };

  // 2. Property (APPARTEMENT — minimal required fields)
  const propRes = await page.request.post(`${API_BASE}/api/properties`, {
    data: {
      type: 'APPARTEMENT',
      title: `E2E Apt ${ts}`,
      referenceCode: `E2E-${ts}`,
      price: 1500000,
      surfaceAreaSqm: 75,
      bedrooms: 2,
      bathrooms: 1,
      floorNumber: 3,
      projectId: proj.id,
    },
  });
  expect(propRes.status()).toBe(201);
  const prop = await propRes.json() as { id: string };

  // 3. Contact
  const contRes = await page.request.post(`${API_BASE}/api/contacts`, {
    data: {
      firstName: 'E2E',
      lastName: `Pipeline-${ts}`,
      email: `e2e-pipeline-${ts}@example.com`,
      processingBasis: 'CONTRACT',
    },
  });
  expect(contRes.status()).toBe(201);
  const cont = await contRes.json() as { id: string };

  // 4. Vente
  const venteRes = await page.request.post(`${API_BASE}/api/ventes`, {
    data: { contactId: cont.id, propertyId: prop.id },
  });
  expect(venteRes.status()).toBe(201);
  return await venteRes.json() as { id: string; statut: string };
}

// ── Tests ──────────────────────────────────────────────────────────────────

test.describe('Vente pipeline', () => {
  // Auth is injected via storageState at the project level (playwright.config.ts).
  // No login call needed — the stored admin session is already present.
  test.beforeEach(async ({ page }) => {
    await page.goto('/app/ventes');
    await page.waitForURL(/.*\/app/, { timeout: 10000 });
  });

  // ── List ──────────────────────────────────────────────────────────────

  test('ventes list page renders', async ({ page }) => {
    // beforeEach already navigated to /app/ventes; wait for content to hydrate
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1')).toContainText('Ventes', { timeout: 8000 });
    // Statut filter select is present
    await expect(page.locator('select')).toBeVisible();
    // Button to create a new vente is present for ADMIN
    await expect(page.locator('[data-testid="create-vente"]')).toBeVisible();
  });

  test('statut filter dropdown lists all statuts', async ({ page }) => {
    // beforeEach already navigated to /app/ventes
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('select', { timeout: 8000 });
    const options = await page.locator('select option').allTextContents();
    expect(options).toContain('Tous les statuts');
    expect(options).toContain('Compromis');
    expect(options).toContain('Financement');
    expect(options).toContain('Acte notarié');
    expect(options).toContain('Livré');
    expect(options).toContain('Annulé');
  });

  // ── Detail + stepper ─────────────────────────────────────────────────

  test('vente detail shows pipeline stepper', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    // Page header with statut badge
    await expect(page.locator('.page-header h1')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.page-header .badge')).toContainText('Compromis');

    // Pipeline stepper component rendered with step labels
    const stepper = page.locator('app-pipeline-stepper');
    await expect(stepper).toBeVisible();
    await expect(stepper).toContainText('Compromis');
    await expect(stepper).toContainText('Financement');
    await expect(stepper).toContainText('Acte notarié');
    await expect(stepper).toContainText('Livré');

    // Info card visible
    await expect(page.locator('.card-title').first()).toBeVisible();
    // Portal card visible
    await expect(page.locator('text=Portail acquéreur').first()).toBeVisible();
  });

  test('advance pipeline button opens dialog for non-terminal statut', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    // Button must be visible (COMPROMIS is non-terminal)
    await expect(page.locator('[data-testid="advance-pipeline"]')).toBeVisible({ timeout: 10000 });

    // Click — dialog opens
    await page.click('[data-testid="advance-pipeline"]');
    await expect(page.locator('app-advance-pipeline-dialog')).toBeVisible({ timeout: 5000 });

    // Dialog shows the target statut (COMPROMIS → FINANCEMENT)
    await expect(page.locator('app-advance-pipeline-dialog')).toContainText('Financement');
  });

  test('cancel in advance dialog closes it', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    await page.waitForSelector('[data-testid="advance-pipeline"]', { timeout: 10000 });
    await page.click('[data-testid="advance-pipeline"]');
    await expect(page.locator('app-advance-pipeline-dialog')).toBeVisible();

    // Click the cancel button in the dialog footer
    await page.locator('[data-testid="dialog-cancel"]').click();
    await expect(page.locator('app-advance-pipeline-dialog')).not.toBeVisible({ timeout: 5000 });
  });

  test('confirming advance transitions statut from COMPROMIS to FINANCEMENT', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    await page.waitForSelector('[data-testid="advance-pipeline"]', { timeout: 10000 });
    await page.click('[data-testid="advance-pipeline"]');

    // Confirm without a date (COMPROMIS→FINANCEMENT needs no date)
    const confirmBtn = page.locator('app-advance-pipeline-dialog [data-testid="dialog-confirm"]');
    await expect(confirmBtn).toBeEnabled({ timeout: 5000 });
    await confirmBtn.click();

    // Dialog closes and statut badge updates
    await expect(page.locator('app-advance-pipeline-dialog')).not.toBeVisible({ timeout: 8000 });
    await expect(page.locator('.page-header .badge')).toContainText('Financement', { timeout: 8000 });

    // Advance button still visible (FINANCEMENT is also non-terminal)
    await expect(page.locator('[data-testid="advance-pipeline"]')).toBeVisible();
  });

  test('invalid transition is rejected with error', async ({ page }) => {
    const vente = await createTestVente(page);

    // Force-advance to FINANCEMENT via API (skipping UI for speed)
    const advRes = await page.request.patch(`${API_BASE}/api/ventes/${vente.id}/statut`, {
      data: { statut: 'FINANCEMENT', notes: 'E2E setup' },
    });
    expect(advRes.status()).toBe(200);

    // Force an illegal transition via API (FINANCEMENT → LIVRE skips ACTE_NOTARIE)
    const badRes = await page.request.patch(`${API_BASE}/api/ventes/${vente.id}/statut`, {
      data: { statut: 'LIVRE' },
    });
    expect(badRes.status()).toBe(409);
    const body = await badRes.json() as { code: string };
    expect(body.code).toBe('INVALID_STATUS_TRANSITION');
  });

  // ── Contract card (Wave 13) ──────────────────────────────────────────

  test('contract card shows PENDING status on a new vente', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    // Contract card should be visible for non-terminal statut
    await expect(page.locator('[data-testid="contract-card"]')).toBeVisible({ timeout: 10000 });
    // Status badge shows PENDING label
    await expect(page.locator('[data-testid="contract-card"] .badge')).toContainText('En attente');
    // Generate button visible for ADMIN
    await expect(page.locator('[data-testid="btn-generate-contract"]')).toBeVisible();
    // Sign button not yet visible
    await expect(page.locator('[data-testid="btn-sign-contract"]')).not.toBeVisible();
  });

  test('generating a contract transitions contractStatus to GENERATED', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    await page.waitForSelector('[data-testid="btn-generate-contract"]', { timeout: 10000 });
    await page.click('[data-testid="btn-generate-contract"]');

    // Badge updates to Généré
    await expect(page.locator('[data-testid="contract-card"] .badge')).toContainText('Généré', { timeout: 8000 });
    // Sign button now visible
    await expect(page.locator('[data-testid="btn-sign-contract"]')).toBeVisible();
    // Generate button no longer present
    await expect(page.locator('[data-testid="btn-generate-contract"]')).not.toBeVisible();
  });

  test('signing a contract transitions contractStatus to SIGNED', async ({ page }) => {
    const vente = await createTestVente(page);
    // Pre-generate via API so UI opens directly in GENERATED state
    const genRes = await page.request.post(`${API_BASE}/api/ventes/${vente.id}/contract/generate`);
    expect(genRes.status()).toBe(200);

    await page.goto(`/app/ventes/${vente.id}`);

    await page.waitForSelector('[data-testid="btn-sign-contract"]', { timeout: 10000 });
    await page.click('[data-testid="btn-sign-contract"]');

    // Badge updates to Signé
    await expect(page.locator('[data-testid="contract-card"] .badge')).toContainText('Signé', { timeout: 8000 });
    // Both action buttons gone after signing
    await expect(page.locator('[data-testid="btn-sign-contract"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="btn-generate-contract"]')).not.toBeVisible();
  });

  test('contract card is hidden when vente is ANNULE', async ({ page }) => {
    const vente = await createTestVente(page);
    // Cancel via API (motifAnnulation is now required)
    const res = await page.request.patch(`${API_BASE}/api/ventes/${vente.id}/statut`, {
      data: { statut: 'ANNULE', motifAnnulation: 'ACCORD_PARTIES' },
    });
    expect(res.status()).toBe(200);

    await page.goto(`/app/ventes/${vente.id}`);
    await page.waitForLoadState('networkidle');

    // Contract card must not be visible for ANNULE statut
    await expect(page.locator('[data-testid="contract-card"]')).not.toBeVisible({ timeout: 8000 });
  });

  // ── Cancellation motif (Wave 13) ─────────────────────────────────────────

  test('API rejects ANNULE transition without motifAnnulation (400)', async ({ page }) => {
    const vente = await createTestVente(page);
    const res = await page.request.patch(`${API_BASE}/api/ventes/${vente.id}/statut`, {
      data: { statut: 'ANNULE' },
    });
    expect(res.status()).toBe(400);
  });

  test('cancel mode in advance dialog requires a motif before confirm is enabled', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    await page.waitForSelector('[data-testid="advance-pipeline"]', { timeout: 10000 });
    await page.click('[data-testid="advance-pipeline"]');
    await expect(page.locator('app-advance-pipeline-dialog')).toBeVisible();

    // Switch to cancel mode
    await page.locator('app-advance-pipeline-dialog .btn-cancel-toggle').first().click();

    // Motif dropdown must appear
    await expect(page.locator('[data-testid="dialog-motif-annulation"]')).toBeVisible({ timeout: 5000 });

    // Confirm button disabled until motif selected
    await expect(page.locator('[data-testid="dialog-confirm"]')).toBeDisabled();

    // Select a motif
    await page.selectOption('[data-testid="dialog-motif-annulation"]', 'CREDIT_REFUSE');

    // Confirm button now enabled
    await expect(page.locator('[data-testid="dialog-confirm"]')).toBeEnabled({ timeout: 3000 });
  });

  test('cancelling a vente with motif sets statut to ANNULE', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    await page.waitForSelector('[data-testid="advance-pipeline"]', { timeout: 10000 });
    await page.click('[data-testid="advance-pipeline"]');

    // Switch to cancel mode and select motif
    await page.locator('app-advance-pipeline-dialog .btn-cancel-toggle').first().click();
    await expect(page.locator('[data-testid="dialog-motif-annulation"]')).toBeVisible();
    await page.selectOption('[data-testid="dialog-motif-annulation"]', 'CSP_NON_REALISEE');

    // Confirm cancellation
    await page.locator('[data-testid="dialog-confirm"]').click();

    // Dialog closes and statut badge updates to Annulé
    await expect(page.locator('app-advance-pipeline-dialog')).not.toBeVisible({ timeout: 8000 });
    await expect(page.locator('.page-header .badge')).toContainText('Annulé', { timeout: 8000 });
  });

  // ── Financing panel (Wave 13) ────────────────────────────────────────────

  test('financing panel is visible and editable, saves to backend', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    // Financing card visible
    await expect(page.locator('[data-testid="financing-card"]')).toBeVisible({ timeout: 10000 });
    // Edit button visible for ADMIN on non-terminal statut
    await expect(page.locator('[data-testid="btn-edit-financing"]')).toBeVisible();

    // Open the financing form
    await page.click('[data-testid="btn-edit-financing"]');
    await expect(page.locator('[data-testid="financing-type"]')).toBeVisible({ timeout: 5000 });

    // Fill in financing details
    await page.selectOption('[data-testid="financing-type"]', 'CREDIT_IMMOBILIER');
    await page.fill('[data-testid="financing-banque"]', 'Banque Populaire');
    await page.fill('[data-testid="financing-montant"]', '850000');

    // Save
    await page.click('[data-testid="financing-submit"]');

    // Form closes; display shows the updated values
    await expect(page.locator('[data-testid="financing-card"]')).toContainText('Crédit immobilier', { timeout: 8000 });
    await expect(page.locator('[data-testid="financing-card"]')).toContainText('Banque Populaire');
  });

  // ── Échéancier ───────────────────────────────────────────────────────

  test('adding an echéance via the form appears in table', async ({ page }) => {
    const vente = await createTestVente(page);
    await page.goto(`/app/ventes/${vente.id}`);

    // Open echéance form
    await page.waitForSelector('.card-header button', { timeout: 10000 });
    await page.locator('.card-header button', { hasText: '+ Ajouter' }).click();

    // Fill in the form
    await page.fill('[data-testid="ech-libelle"]', 'Acompte 30%');
    await page.fill('[data-testid="ech-montant"]', '450000');
    await page.fill('[data-testid="ech-date"]', '2026-12-31');
    await page.click('[data-testid="ech-submit"]');

    // Row should appear in the table
    await expect(page.locator('.data-table tbody')).toContainText('Acompte 30%', { timeout: 8000 });
    await expect(page.locator('.data-table tbody')).toContainText('450 000');
  });

});
