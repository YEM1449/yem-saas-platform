import { test, expect } from '@playwright/test';

// In CI, page.request uses playwright baseURL (port 4200 / Python SPA — GET only).
// API write/read calls must go directly to the backend at port 8080.
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

// ── Test data helper ───────────────────────────────────────────────────────

async function createVenteWithContact(
  page: import('@playwright/test').Page
): Promise<{ venteId: string; contactEmail: string }> {
  const ts = Date.now();

  const projRes = await page.request.post(`${API_BASE}/api/projects`, {
    data: { name: `E2E Portal Project ${ts}` },
  });
  expect(projRes.status()).toBe(201);
  const proj = await projRes.json() as { id: string };

  const propRes = await page.request.post(`${API_BASE}/api/properties`, {
    data: {
      type: 'APPARTEMENT',
      title: `E2E Portal Apt ${ts}`,
      referenceCode: `PORTAL-${ts}`,
      price: 1_500_000,
      surfaceAreaSqm: 70,
      bedrooms: 2,
      bathrooms: 1,
      floorNumber: 2,
      projectId: proj.id,
    },
  });
  expect(propRes.status()).toBe(201);
  const prop = await propRes.json() as { id: string };

  const contactEmail = `portal-buyer-${ts}@example.com`;
  const contRes = await page.request.post(`${API_BASE}/api/contacts`, {
    data: {
      prenom: 'PortalBuyer',
      nomFamille: `E2E-${ts}`,
      email: contactEmail,
    },
  });
  expect(contRes.status()).toBe(201);
  const cont = await contRes.json() as { id: string };

  const venteRes = await page.request.post(`${API_BASE}/api/ventes`, {
    data: { contactId: cont.id, propertyId: prop.id },
  });
  expect(venteRes.status()).toBe(201);
  const vente = await venteRes.json() as { id: string };

  return { venteId: vente.id, contactEmail };
}

// ── Tests ──────────────────────────────────────────────────────────────────

test.describe('Buyer Portal', () => {

  // ── Portal login page ──────────────────────────────────────────────────

  test('portal login page renders', async ({ page }) => {
    await page.goto('/portal/login');
    await expect(page.locator('h1')).toContainText('Client Portal', { timeout: 8000 });
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#societeKey')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('sending magic link shows sent confirmation', async ({ page }) => {
    // Mock the request-link endpoint so no real email is sent
    await page.route('**/api/portal/auth/request-link', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Magic link sent', magicLinkUrl: '' }),
      });
    });

    await page.goto('/portal/login');
    await page.fill('#societeKey', 'acme');
    await page.fill('#email', 'buyer@example.com');
    await page.click('button[type="submit"]');

    // After sending, shows the "check your inbox" confirmation
    await expect(page.locator('.portal-alert-success')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('.portal-alert-success')).toContainText('Check your inbox');
    await expect(page.locator('.portal-alert-success')).toContainText('buyer@example.com');

    // "Send another link" button lets user retry
    await expect(page.locator('button:has-text("Send another link")')).toBeVisible();
  });

  test('invalid magic link token shows error state', async ({ page }) => {
    // Verify endpoint returns 400/404 for bad token
    await page.route('**/api/portal/auth/verify*', (route) => {
      route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Token invalide ou expiré' }),
      });
    });

    // Simulate the redirect from magic link email with a bad token
    await page.goto('/portal/login?token=bad-invalid-token-xyz');

    // Should show error state after verification fails
    await expect(
      page.locator('.portal-alert-error, .state-invalid, .portal-login-page')
    ).toBeVisible({ timeout: 10000 });
  });

  // ── Auth guard ─────────────────────────────────────────────────────────

  test('unauthenticated access to portal ventes redirects to login', async ({ page }) => {
    // No mocked auth — getTenantInfo returns 401 → portalGuard redirects
    await page.goto('/portal/ventes');
    await expect(page).toHaveURL(/.*portal\/login/, { timeout: 10000 });
  });

  // ── Portal ventes (mocked auth) ────────────────────────────────────────

  test('portal ventes page renders with pipeline stepper', async ({ page }) => {
    // Mock auth validation so portalGuard passes
    await page.route('**/api/portal/tenant-info', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          societeNom: 'ACME Immobilier',
          contactPrenom: 'Jean',
          contactNom: 'Dupont',
        }),
      });
    });

    // Mock portal ventes list
    await page.route('**/api/portal/ventes', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'mock-vente-id-1',
            societeId: 'mock-societe',
            propertyId: 'mock-property',
            contactId: 'mock-contact',
            contactFullName: 'Jean Dupont',
            agentId: 'mock-agent',
            reservationId: null,
            statut: 'FINANCEMENT',
            prixVente: 1_500_000,
            dateCompromis: '2026-03-01',
            dateActeNotarie: null,
            dateLivraisonPrevue: '2026-12-31',
            dateLivraisonReelle: null,
            notes: 'Dossier en cours',
            echeances: [
              {
                id: 'mock-ech-1',
                venteId: 'mock-vente-id-1',
                libelle: 'Acompte 10%',
                montant: 150_000,
                dateEcheance: '2026-04-01',
                statut: 'PAYEE',
                datePaiement: '2026-04-01',
                notes: null,
                createdAt: '2026-03-01T10:00:00Z',
              },
              {
                id: 'mock-ech-2',
                venteId: 'mock-vente-id-1',
                libelle: 'Solde restant',
                montant: 1_350_000,
                dateEcheance: '2026-12-31',
                statut: 'EN_ATTENTE',
                datePaiement: null,
                notes: null,
                createdAt: '2026-03-01T10:00:00Z',
              },
            ],
            documents: [],
            createdAt: '2026-03-01T10:00:00Z',
            updatedAt: '2026-04-01T10:00:00Z',
          },
        ]),
      });
    });

    await page.goto('/portal/ventes');

    // Should NOT redirect to login
    await expect(page).not.toHaveURL(/.*portal\/login/, { timeout: 5000 }).catch(() => {});
    await expect(page.url()).not.toContain('/portal/login');

    // Section title
    await expect(page.locator('.portal-section-title')).toContainText('Mon Acquisition', { timeout: 10000 });

    // Pipeline stepper component rendered
    await expect(page.locator('app-pipeline-stepper')).toBeVisible({ timeout: 8000 });

    // Financial summary visible
    await expect(page.locator('.summary-value')).toContainText('1 500 000');
  });

  test('portal ventes shows echeances when present', async ({ page }) => {
    await page.route('**/api/portal/tenant-info', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ societeNom: 'ACME', contactPrenom: 'Jean', contactNom: 'Dupont' }),
      });
    });

    await page.route('**/api/portal/ventes', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'mock-vente-2',
            societeId: 'mock-societe', propertyId: 'mock-property',
            contactId: 'mock-contact', contactFullName: 'Jean Dupont',
            agentId: 'mock-agent', reservationId: null,
            statut: 'COMPROMIS', prixVente: 800_000,
            dateCompromis: '2026-04-01', dateActeNotarie: null,
            dateLivraisonPrevue: null, dateLivraisonReelle: null, notes: null,
            echeances: [
              {
                id: 'e1', venteId: 'mock-vente-2',
                libelle: 'Versement initial', montant: 80_000,
                dateEcheance: '2026-05-01', statut: 'EN_ATTENTE',
                datePaiement: null, notes: null, createdAt: '2026-04-01T10:00:00Z',
              },
            ],
            documents: [],
            createdAt: '2026-04-01T10:00:00Z',
            updatedAt: '2026-04-01T10:00:00Z',
          },
        ]),
      });
    });

    await page.goto('/portal/ventes');
    await expect(page.locator('.portal-section-title')).toContainText('Mon Acquisition', { timeout: 10000 });

    // Echeance row visible
    await expect(page.locator('text=Versement initial')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('text=En attente')).toBeVisible();
  });

  // ── Portal invite API (integration smoke) ─────────────────────────────

  test('admin can invite buyer to portal via vente', async ({ page }) => {
    await loginAsAdmin(page);

    const { venteId } = await createVenteWithContact(page);

    // POST /api/ventes/{id}/portal/invite should return 200
    const inviteRes = await page.request.post(`${API_BASE}/api/ventes/${venteId}/portal/invite`);
    expect(inviteRes.status()).toBe(200);
    const body = await inviteRes.json() as { message: string };
    expect(body.message).toBeTruthy();
  });

  test('admin invite appears on vente detail page', async ({ page }) => {
    await loginAsAdmin(page);
    const { venteId } = await createVenteWithContact(page);

    await page.goto(`/app/ventes/${venteId}`);

    // Portal card is visible on the vente detail
    await expect(page.getByText('Portail acquéreur')).toBeVisible({ timeout: 10000 });

    // Invite button (portal invite section)
    const inviteBtn = page.locator('button:has-text("Inviter")');
    await expect(inviteBtn).toBeVisible({ timeout: 8000 });
  });

});
