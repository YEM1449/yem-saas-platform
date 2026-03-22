# Task 15 — End-to-End Tests with Playwright

## Priority: MEDIUM
## Effort: 3 hours
## Depends on: Tasks 12, 13, 14

## Problem

No E2E tests validate the full user journey through the system. Backend has integration tests with MockMvc, but nothing validates the Angular ↔ Backend ↔ Database flow together.

## Key Scenarios to Cover

### 1. Authentication Flow
- Login with valid credentials → redirect to dashboard
- Login with wrong password → error message
- Multi-société user → société selection screen → select → dashboard
- Token expiry → redirect to login

### 2. Contact Lifecycle
- Create contact → appears in list
- Edit contact → changes saved
- Convert to prospect → status changes
- Cross-société: login as société B → contact from A not visible

### 3. Property + Reservation Flow
- Create project → create property → publish
- Reserve property → status changes to RESERVED
- Cancel reservation → status reverts

### 4. User Management (after Task 14)
- Admin invites user → invitation email sent
- Activate invitation → user can login
- Change role → user sees updated permissions
- Remove user → user can no longer login

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
  baseURL: 'http://localhost:4200',
  use: {
    headless: true,
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
  },
});
```

### Example Test: `hlm-frontend/e2e/auth.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('login with valid credentials redirects to dashboard', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[name="email"]', 'admin@acme.com');
  await page.fill('input[name="password"]', 'Admin123!');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/.*dashboard/);
});

test('login with wrong password shows error', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[name="email"]', 'admin@acme.com');
  await page.fill('input[name="password"]', 'WrongPassword');
  await page.click('button[type="submit"]');
  await expect(page.locator('.error-message')).toBeVisible();
});
```

## Run

```bash
# Requires backend + frontend running (docker compose up)
cd hlm-frontend && npx playwright test
```

## Acceptance Criteria

- [ ] At least 4 E2E scenarios pass (auth, contact CRUD, property flow, cross-société)
- [ ] Tests run in CI (GitHub Actions with Docker Compose)
- [ ] Screenshots captured on failure
