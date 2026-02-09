# Frontend Guide (Angular)

## Tech stack
- **Framework:** Angular 19
- **Language:** TypeScript
- **Build tooling:** Angular CLI / Vite-backed dev server

## Local development

### Install
```bash
cd frontend
npm ci
```

### Run (dev)
```bash
npm start
```

The app runs on `http://localhost:4200`. A dev proxy (`proxy.conf.json`) forwards `/auth`, `/api`, and `/actuator` to the backend at `http://localhost:8080` so CORS does not block local calls.

### Build (production)
```bash
npm run build
```

The output is written to `dist/frontend/` and uses the production environment config.

## Environment configuration

`src/environments/environment.ts` controls the API base URL in development. It defaults to an empty string so requests go through the proxy. If you set `apiUrl` to an explicit backend URL (for example, `http://localhost:8080`), you must also add your frontend origin to the backend CORS allow list.

## Authentication flow

1. Login form submits `tenantKey`, `email`, and `password` to `/auth/login`.
2. The `AuthService` stores `accessToken` in `localStorage`.
3. `authInterceptor` attaches the `Authorization` header to every request and logs the user out on 401 errors.

## Routing
- `/login` is public and renders the login page.
- `/app/*` routes are protected by an auth guard and render the shell layout.
- `/app/properties` shows the properties list.

## Key frontend modules

```
src/app/
  core/
    auth/          # AuthService, AuthGuard, AuthInterceptor
    models/        # DTOs for login, properties, error responses
  features/
    login/         # Login page
    shell/         # App shell layout + nav
    properties/    # Properties list
  app.routes.ts    # Route configuration
  app.config.ts    # Providers + HttpClient interceptor setup
```

## Troubleshooting
- **401 loops:** check that the backend is running and your JWT secret matches the issuing environment.
- **CORS errors:** use the dev proxy or add your origin to `CorsConfig.allowedOrigins`.
