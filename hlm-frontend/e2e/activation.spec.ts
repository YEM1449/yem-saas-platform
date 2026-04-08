import { test, expect } from '@playwright/test';

// In CI the Angular build uses apiUrl='http://localhost:8080' (environment.ci.ts),
// but page.request uses playwright's baseURL ('http://localhost:4200') which is the
// Python SPA static server — it only handles GET.  API write calls must go directly
// to the backend.
const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '';

// ── Helpers ────────────────────────────────────────────────────────────────

async function loginAsAdmin(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/login');
  await page.waitForSelector('[data-testid="email"]', { timeout: 30000 });
  await page.fill('[data-testid="email"]', 'admin@acme.com');
  await page.fill('[data-testid="password"]', 'Admin123!Secure');
  await page.click('[data-testid="login-button"]');
  await page.waitForURL(/.*\/app/, { timeout: 15000 });
}

// ── Tests ──────────────────────────────────────────────────────────────────

test.describe('Activation page', () => {

  // The activation component reads the token from the ?token= query param
  // (not from the path).  Route: { path: 'activation' } — no :token segment.

  test('invalid token renders error state', async ({ page }) => {
    await page.goto('/activation?token=definitely-not-a-valid-token-e2e-123');
    // Component loads, calls GET /auth/invitation/{token}, receives 404 → shows invalid state
    const invalidState = page.getByTestId('activation-invalid-state');
    await expect(invalidState).toBeVisible({ timeout: 12000 });
    await expect(invalidState.locator('h1')).toHaveText('Lien invalide');
    // Back-to-login link is present
    await expect(invalidState.locator('a[href="/login"]')).toBeVisible();
  });

  test('activation shell and brand panel render', async ({ page }) => {
    await page.goto('/activation?token=some-token-smoke-test');
    // Page skeleton must render (brand panel + form panel)
    await expect(page.locator('.activation-shell')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.activation-brand')).toBeVisible();
    await expect(page.locator('.activation-form-panel')).toBeVisible();
    // After the token check resolves (404 for fake token), shows invalid — not a blank page
    await expect(page.getByTestId('activation-invalid-state')).toBeVisible({ timeout: 12000 });
  });

  test('valid invitation shows activation form', async ({ page }) => {
    // Step 1: verify the invite and reinvite API calls succeed (integration smoke).
    // API calls use API_BASE so they reach the backend in CI (port 8080) and
    // through the ng serve proxy in local dev (empty string → port 4200 proxy).
    await loginAsAdmin(page);

    const ts = Date.now();
    const email = `e2e-activation-${ts}@example.com`;

    const inviteRes = await page.request.post(`${API_BASE}/api/mon-espace/utilisateurs`, {
      data: {
        prenom: 'E2EPrenom',
        nomFamille: `Activation-${ts}`,
        email,
        role: 'AGENT',
      },
    });
    expect(inviteRes.status()).toBe(201);
    const membre = await inviteRes.json() as { id: string };

    const reinviteRes = await page.request.post(
      `${API_BASE}/api/mon-espace/utilisateurs/${membre.id}/reinviter`
    );
    expect(reinviteRes.status()).toBe(200);

    // Step 2: assert the activation form renders for a valid token.
    // The real token is sent by email (noop in CI) so we cannot retrieve it.
    // Mock the token-validation endpoint so we can exercise the form branch.
    const MOCK_TOKEN = 'e2e-mock-valid-token-abc123';

    await page.route(`**/auth/invitation/${MOCK_TOKEN}`, (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          prenom: 'E2EPrenom',
          email,
          societeNom: 'ACME Immobilier',
          role: 'AGENT',
          expireDans: '47h',
        }),
      });
    });

    await page.goto(`/activation?token=${MOCK_TOKEN}`);

    // Form state — not loading or invalid
    await expect(page.getByTestId('activation-submit')).toBeVisible({ timeout: 10000 });

    // Invitation details rendered in the subtitle
    await expect(page.locator('.form-subtitle')).toContainText('E2EPrenom');
    await expect(page.locator('.form-subtitle')).toContainText('ACME Immobilier');
    await expect(page.locator('.role-chip')).toContainText('AGENT');

    // CGU checkbox and password fields present
    await expect(page.locator('#motDePasse')).toBeVisible();
    await expect(page.locator('#confirmationMotDePasse')).toBeVisible();
    await expect(page.locator('#cgu')).toBeVisible();

    // Submit button disabled until form is valid (passwords empty, CGU unchecked)
    await expect(page.locator('[data-testid="activation-submit"]')).toBeDisabled();

    // Expiry note shown at bottom of form
    await expect(page.locator('.form-footer-note')).toContainText('47h');
  });

  test('back-to-login link navigates to login page', async ({ page }) => {
    await page.goto('/activation?token=bad-token-nav-test');
    const invalidState = page.getByTestId('activation-invalid-state');
    await expect(invalidState.locator('a[href="/login"]')).toBeVisible({ timeout: 12000 });
    await invalidState.locator('a[href="/login"]').click();
    await expect(page).toHaveURL(/.*\/login/, { timeout: 8000 });
  });

});
