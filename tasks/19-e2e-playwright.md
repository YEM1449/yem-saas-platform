# Task 19 — Playwright E2E Test Suite

## Priority: MEDIUM
## Effort: 3 hours
## Depends on: Tasks 16, 17, 18 (frontend features complete)

## What to Build

End-to-end tests that validate the full stack: Angular → Nginx → Spring Boot → PostgreSQL.

## Setup

```bash
cd hlm-frontend
npm install -D @playwright/test
npx playwright install chromium
```

### `hlm-frontend/playwright.config.ts`

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  retries: 1,
  baseURL: 'http://localhost:4200',
  use: {
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 120000,
  },
});
```

### `hlm-frontend/package.json` — add script

```json
"scripts": {
  "e2e": "playwright test",
  "e2e:headed": "playwright test --headed"
}
```

## Test Scenarios

### `hlm-frontend/e2e/auth.spec.ts` — Authentication

```typescript
import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('login with valid credentials → dashboard', async ({ page }) => {
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@test.com');
    await page.fill('[data-testid="password"]', 'TestPassword123!');
    await page.click('[data-testid="login-button"]');
    await expect(page).toHaveURL(/.*app/);
  });

  test('login with wrong password → error', async ({ page }) => {
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@test.com');
    await page.fill('[data-testid="password"]', 'wrong');
    await page.click('[data-testid="login-button"]');
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
  });

  test('unauthenticated access → redirect to login', async ({ page }) => {
    await page.goto('/app/properties');
    await expect(page).toHaveURL(/.*login/);
  });
});
```

### `hlm-frontend/e2e/contacts.spec.ts` — Contact CRUD

```typescript
test.describe('Contacts', () => {
  test.beforeEach(async ({ page }) => {
    // Login helper
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'admin@test.com');
    await page.fill('[data-testid="password"]', 'TestPassword123!');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(/.*app/);
  });

  test('create contact → appears in list', async ({ page }) => {
    await page.goto('/app/contacts');
    await page.click('[data-testid="create-contact"]');
    await page.fill('[data-testid="firstName"]', 'E2E');
    await page.fill('[data-testid="lastName"]', 'TestContact');
    await page.fill('[data-testid="email"]', 'e2e@test.com');
    await page.click('[data-testid="save-button"]');
    await expect(page.locator('text=E2E TestContact')).toBeVisible();
  });
});
```

### `hlm-frontend/e2e/tasks.spec.ts` — Task Management

### `hlm-frontend/e2e/superadmin.spec.ts` — SuperAdmin Societe Management

## Data-Testid Convention

Add `data-testid` attributes to existing components for test stability:
- Login form: `data-testid="email"`, `data-testid="password"`, `data-testid="login-button"`
- Contact form: `data-testid="firstName"`, `data-testid="lastName"`, etc.
- Action buttons: `data-testid="create-contact"`, `data-testid="save-button"`
- Error display: `data-testid="error-message"`

## CI Integration

Add to `.github/workflows/e2e.yml`:

```yaml
name: E2E Tests
on:
  push:
    branches: [main]
jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker compose up -d
      - run: docker compose exec hlm-backend curl -sf http://localhost:8080/actuator/health
      - uses: actions/setup-node@v4
        with: { node-version: 22 }
      - run: cd hlm-frontend && npm ci && npx playwright install chromium
      - run: cd hlm-frontend && npx playwright test
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: playwright-report, path: hlm-frontend/test-results/ }
```

## Acceptance Criteria

- [ ] At least 6 E2E scenarios pass: login, failed login, auth redirect, contact CRUD, task CRUD, superadmin societe list
- [ ] Tests use data-testid attributes (not fragile CSS selectors)
- [ ] Screenshots captured on failure
- [ ] CI workflow runs E2E tests
