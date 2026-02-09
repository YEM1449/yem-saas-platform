# HLM Frontend

Angular 19 SPA for the HLM multi-tenant CRM backend.

## Prerequisites

- **Node 18+** and **npm 9+**
- Backend running at `http://localhost:8080` (see root [README](../README.md))

## Setup

```bash
cd frontend
npm ci
```

## Run (development)

```bash
npm start
# or: npx ng serve
```

Opens at **http://localhost:4200**. A dev proxy (`proxy.conf.json`) forwards `/auth`, `/api`, and `/actuator` to `http://localhost:8080`, avoiding CORS issues.

## Build (production)

```bash
npm run build
```

Output: `dist/frontend/`. Uses `environment.production.ts` (same-origin `apiUrl`).

## API base URL

Configured in `src/environments/environment.ts`:

```ts
export const environment = {
  production: false,
  apiUrl: '',  // empty = use proxy; set 'http://localhost:8080' to bypass
};
```

If bypassing the proxy, add your dev origin to the backend's `CorsConfig.java` `allowedOrigins`.

## Project structure

```
src/app/
  core/
    auth/           # AuthService, AuthGuard, AuthInterceptor
    models/         # LoginRequest/Response, ErrorResponse, Property DTOs
  features/
    login/          # Login page (tenantKey + email + password)
    shell/          # App shell with nav bar + router-outlet
    properties/     # Properties list page + PropertyService
  app.routes.ts     # Route config: /login, /app/*, guards
  app.config.ts     # Providers: router, HttpClient + interceptor
```

## Routes

| Path              | Guard | Component        |
|-------------------|-------|------------------|
| `/login`          | none  | LoginComponent   |
| `/app`            | auth  | ShellComponent   |
| `/app/properties` | auth  | PropertiesComponent |
