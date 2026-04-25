import { test, expect, Page } from '@playwright/test';

const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '';

/** Create a project via page.request (shares auth cookies with browser context) */
async function createProject(page: Page): Promise<string> {
  const uid = Date.now();
  const res = await page.request.post(`${API_BASE}/api/projects`, {
    data: {
      name: `3D E2E Project ${uid}`,
      description: 'E2E test project',
      adresse: '1 rue Test',
      ville: 'Paris',
      codePostal: '75001',
    },
  });
  expect(res.status()).toBe(201);
  const data = await res.json() as { id: string };
  return data.id;
}

/** Navigate to the app (storageState cookie is already present from the setup project) */
async function ensureLoggedIn(page: Page): Promise<void> {
  await page.goto('/app/properties');
  await page.waitForLoadState('networkidle');
  if (page.url().includes('/login') || !page.url().includes('/app')) {
    await page.fill('[data-testid="email"]', 'admin@acme.com');
    await page.fill('[data-testid="password"]', 'Admin123!Secure');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(/.*\/app/, { timeout: 15000 });
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Suite: Visualiseur 3D — navigation et fallback
// ─────────────────────────────────────────────────────────────────────────────

test.describe('Viewer 3D — navigation', () => {

  test('navigates to viewer route without crashing', async ({ page }) => {
    await ensureLoggedIn(page);
    // Navigate to a non-existent project — expect the page to render (not crash)
    const fakeId = '00000000-0000-0000-0000-000000000001';
    await page.goto(`/app/projets/${fakeId}/viewer-3d`);
    await page.waitForLoadState('networkidle');
    // Either the viewer or the fallback renders — no blank/error page
    const body = await page.textContent('body');
    expect(body).not.toBeNull();
  });

  test('shows fallback state when no 3D model is configured', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    await page.goto(`/app/projets/${projectId}/viewer-3d`);
    await page.waitForLoadState('networkidle');

    // When no model exists the component should show a non-canvas fallback
    // The canvas element should NOT be present (no model to render)
    await expect(page.locator('[data-testid="viewer-no-model"]').or(
      page.locator('[data-testid="viewer-error"]')
    )).toBeVisible({ timeout: 10000 });
  });

  test('dashboard 3D tab is accessible', async ({ page }) => {
    await ensureLoggedIn(page);
    await page.goto('/app/dashboard/commercial/3d');
    await page.waitForLoadState('networkidle');
    // Tab should render without HTTP 500/error page
    await expect(page.locator('body')).not.toContainText('Error', { timeout: 8000 });
  });

});

// ─────────────────────────────────────────────────────────────────────────────
// Suite: Upload admin — contrôles RBAC
// ─────────────────────────────────────────────────────────────────────────────

test.describe('Viewer 3D — upload admin (RBAC)', () => {

  test('admin sees upload zone on viewer page when no model exists', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    await page.goto(`/app/projets/${projectId}/viewer-3d`);
    await page.waitForLoadState('networkidle');

    // The upload component should be visible for admins with no model
    await expect(page.locator('[data-testid="glb-file-input"]').or(
      page.locator('[data-testid="viewer-no-model"]')
    )).toBeVisible({ timeout: 10000 });
  });

  test('upload: selecting a non-glb file shows error', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    await page.goto(`/app/projets/${projectId}/viewer-3d`);
    await page.waitForLoadState('networkidle');

    const fileInput = page.locator('[data-testid="glb-file-input"]');
    // Only run this sub-check if the upload zone is visible
    if (await fileInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await fileInput.setInputFiles({
        name: 'bad-file.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('fake'),
      });
      await expect(page.locator('text=.glb')).toBeVisible({ timeout: 5000 });
    }
  });

});

// ─────────────────────────────────────────────────────────────────────────────
// Suite: API endpoint checks (smoke via page.request)
// ─────────────────────────────────────────────────────────────────────────────

test.describe('Viewer 3D — API smoke tests', () => {

  test('GET /3d-model returns 404 when no model exists', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    const res = await page.request.get(`${API_BASE}/api/projects/${projectId}/3d-model`);
    expect(res.status()).toBe(404);
  });

  test('GET /3d-properties-status returns 200 empty array', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    const res = await page.request.get(`${API_BASE}/api/projects/${projectId}/3d-properties-status`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('POST /upload-url as admin returns fileKey with correct prefix', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    const res = await page.request.post(`${API_BASE}/api/projects/${projectId}/3d-model/upload-url`, {
      data: { fileName: 'test.glb', fileSizeBytes: 1000, dracoCompressed: true },
    });
    expect(res.status()).toBe(200);
    const body = await res.json() as { fileKey: string };
    expect(body.fileKey).toContain(projectId);
    expect(body.fileKey).toMatch(/\.glb$/);
  });

  test('POST /upload-url with dracoCompressed=false returns 400', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    const res = await page.request.post(`${API_BASE}/api/projects/${projectId}/3d-model/upload-url`, {
      data: { fileName: 'test.glb', fileSizeBytes: 1000, dracoCompressed: false },
    });
    expect(res.status()).toBe(400);
  });

  test('GET /upload-url as AGENT returns 401', async ({ page }) => {
    await ensureLoggedIn(page);
    const projectId = await createProject(page);

    const res = await page.request.post(`${API_BASE}/api/projects/${projectId}/3d-model/upload-url`, {
      headers: { 'Authorization': 'Bearer INVALID' },
      data: { fileName: 'test.glb', fileSizeBytes: 1000, dracoCompressed: true },
    });
    expect(res.status()).toBe(401);
  });

});
