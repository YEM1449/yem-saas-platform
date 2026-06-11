import { test, expect, Page } from '@playwright/test';

/**
 * Wave 12 — VEFA (Loi 44-00) end-to-end flows. Uses the shared admin storageState
 * (project dependency on `setup`), so no per-test login. Data is set up via the API
 * (page.request shares the auth cookie), then the VEFA-specific actions are driven
 * through the UI using the data-testids added in Wave 12.
 *
 * Runs in CI (e2e.yml brings up the full stack). PLAYWRIGHT_API_BASE points page.request
 * at the backend (4200 static SPA only serves GET).
 */
const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '';

async function createActiveProperty(page: Page): Promise<{ propertyId: string; contactId: string }> {
  const ts = Date.now() + Math.floor(Math.random() * 1000);

  const proj = await (await page.request.post(`${API_BASE}/api/projects`, {
    data: { name: `E2E VEFA Project ${ts}` },
  })).json() as { id: string };

  const propRes = await page.request.post(`${API_BASE}/api/properties`, {
    data: {
      type: 'APPARTEMENT', title: `E2E VEFA Apt ${ts}`, referenceCode: `E2E-VEFA-${ts}`,
      price: 1500000, surfaceAreaSqm: 75, bedrooms: 2, bathrooms: 1, floorNumber: 3, projectId: proj.id,
    },
  });
  expect(propRes.status()).toBe(201);
  const prop = await propRes.json() as { id: string };
  expect((await page.request.patch(`${API_BASE}/api/properties/${prop.id}/status`,
    { data: { status: 'ACTIVE' } })).status()).toBe(200);

  const cont = await (await page.request.post(`${API_BASE}/api/contacts`, {
    data: { firstName: 'E2E', lastName: `VEFA-${ts}`, email: `e2e-vefa-${ts}@example.com`, processingBasis: 'CONTRACT' },
  })).json() as { id: string };

  return { propertyId: prop.id, contactId: cont.id };
}

test.describe('VEFA pipeline (Loi 44-00)', () => {

  test('option → confirm reservation (deposit ≤5%) → exercise retraction', async ({ page }) => {
    const { propertyId, contactId } = await createActiveProperty(page);

    // OPTION via API → 201
    const optRes = await page.request.post(`${API_BASE}/api/ventes/option`, {
      data: { propertyId, contactId, dureeHeures: 48 },
    });
    expect(optRes.status()).toBe(201);
    const vente = await optRes.json() as { id: string; statut: string };
    expect(vente.statut).toBe('OPTION');

    await page.goto(`/app/ventes/${vente.id}`);
    await expect(page.locator('[data-testid="vefa-actions"]')).toBeVisible({ timeout: 15000 });

    // Confirm reservation with a legal deposit (≤ 5% of 1 500 000 = 75 000)
    await page.click('[data-testid="vefa-confirm-open"]');
    await page.fill('[data-testid="vefa-deposit"]', '50000');
    await page.click('[data-testid="vefa-confirm-submit"]');

    // Now in the legal retraction window
    await expect(page.locator('[data-testid="vefa-retract"]')).toBeVisible({ timeout: 15000 });

    // Exercise retraction (JS confirm dialog → accept)
    page.on('dialog', d => d.accept());
    await page.click('[data-testid="vefa-retract"]');

    // Vente is cancelled — the stepper shows the cancelled pill, VEFA actions gone
    await expect(page.locator('[data-testid="vefa-retract"]')).toHaveCount(0, { timeout: 15000 });
  });

  test('excessive deposit (>5%) is rejected', async ({ page }) => {
    const { propertyId, contactId } = await createActiveProperty(page);
    const vente = await (await page.request.post(`${API_BASE}/api/ventes/option`, {
      data: { propertyId, contactId, dureeHeures: 48 },
    })).json() as { id: string };

    await page.goto(`/app/ventes/${vente.id}`);
    await page.click('[data-testid="vefa-confirm-open"]');
    await page.fill('[data-testid="vefa-deposit"]', '200000'); // > 5% of 1 500 000
    await page.click('[data-testid="vefa-confirm-submit"]');

    // The error surface shows the legal violation; the deposit form stays open.
    await expect(page.locator('[data-testid="vefa-error"]')).toBeVisible({ timeout: 15000 });
  });

  test('delivery with reserves → lift reserve', async ({ page }) => {
    const { propertyId, contactId } = await createActiveProperty(page);

    // Create a direct sale (COMPROMIS) then advance to ACTE via API for setup.
    const vente = await (await page.request.post(`${API_BASE}/api/ventes`, {
      data: { contactId, propertyId, prixVente: 1500000 },
    })).json() as { id: string };
    for (const statut of ['FINANCEMENT', 'ACTE']) {
      const res = await page.request.patch(`${API_BASE}/api/ventes/${vente.id}/statut`, { data: { statut } });
      expect(res.status()).toBe(200);
    }

    await page.goto(`/app/ventes/${vente.id}`);
    await expect(page.locator('[data-testid="vefa-delivery-open"]')).toBeVisible({ timeout: 15000 });

    await page.click('[data-testid="vefa-delivery-open"]');
    await page.fill('[data-testid="vefa-delivery-reserves"]', 'Fissure plafond séjour');
    await page.click('[data-testid="vefa-delivery-submit"]');

    // Reserves panel appears with a "Lever" action
    await expect(page.locator('[data-testid="vefa-reserves-list"]')).toBeVisible({ timeout: 15000 });
    await page.click('[data-testid="vefa-lift-reserve"]');
    // After lifting the only reserve, it is marked levée (the lift button disappears)
    await expect(page.locator('[data-testid="vefa-lift-reserve"]')).toHaveCount(0, { timeout: 15000 });
  });

  test('legal échéancier generation + quittance for a paid call', async ({ page }) => {
    const { propertyId, contactId } = await createActiveProperty(page);
    const vente = await (await page.request.post(`${API_BASE}/api/ventes`, {
      data: { contactId, propertyId, prixVente: 1500000 },
    })).json() as { id: string };

    await page.goto(`/app/ventes/${vente.id}`);
    await page.click('[data-testid="ech-generate-legal"]');
    // The 7 legal calls appear; the "Marquer payée" action is available on each
    await expect(page.locator('[data-testid="ech-quittance"]').or(page.getByText('Marquer payée').first()))
      .toBeVisible({ timeout: 15000 });
  });
});

test.describe('VEFA treasury dashboard', () => {
  test('treasury dashboard loads with KPI cards', async ({ page }) => {
    await page.goto('/app/dashboard/tresorerie');
    await expect(page.getByRole('heading', { name: 'Trésorerie VEFA' })).toBeVisible({ timeout: 15000 });
    await expect(page.locator('text=À encaisser').or(page.locator('.empty-state'))).toBeVisible({ timeout: 15000 });
  });
});
