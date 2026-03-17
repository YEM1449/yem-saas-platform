# Frontend Deep Dive — Engineer Guide

This guide covers the Angular 19 frontend: application structure, routing, HTTP interceptors, authentication flow, portal flow, how to add a new feature, and local development tips.

## Table of Contents

1. [Project Structure](#project-structure)
2. [Routing Architecture](#routing-architecture)
3. [HTTP and Interceptors](#http-and-interceptors)
4. [CRM Authentication Flow](#crm-authentication-flow)
5. [Portal Authentication Flow](#portal-authentication-flow)
6. [Adding a New Feature Module](#adding-a-new-feature-module)
7. [Dev Proxy Configuration](#dev-proxy-configuration)
8. [Build and Production Bundle](#build-and-production-bundle)
9. [Nginx Configuration](#nginx-configuration)

---

## Project Structure

```
hlm-frontend/
├── src/
│   └── app/
│       ├── app.config.ts          ← Bootstrap: providers, router, interceptors
│       ├── app.routes.ts          ← Top-level lazy routes
│       ├── core/
│       │   ├── interceptors/
│       │   │   ├── auth.interceptor.ts     ← Attaches CRM JWT to /api/** calls
│       │   │   └── portal.interceptor.ts   ← Attaches portal JWT to /api/portal/** calls
│       │   └── services/
│       │       ├── auth.service.ts         ← CRM login, token storage
│       │       └── token.service.ts        ← JWT decode utilities
│       └── features/
│           ├── auth/               ← /auth/login
│           ├── contacts/           ← /app/contacts
│           ├── contracts/          ← /app/contracts, payment-schedule
│           ├── dashboard/          ← /app/dashboard
│           ├── deposits/           ← /app/deposits
│           ├── portal/             ← /portal/login, /portal/contracts
│           ├── projects/           ← /app/projects
│           ├── properties/         ← /app/properties
│           └── reservations/       ← /app/reservations
├── proxy.conf.json                ← Dev proxy → :8080
├── angular.json                   ← Angular CLI configuration
├── package.json
└── nginx.conf                     ← Production Nginx config
```

---

## Routing Architecture

Top-level routes are defined in `app.routes.ts` with lazy loading. All feature components are standalone Angular components.

```
/                  → redirect to /app/dashboard
/auth/login        → AuthComponent (public)
/app/*             → guarded by auth guard; CRM JWT required
  /app/dashboard
  /app/projects
  /app/properties
  /app/contacts
  /app/deposits
  /app/reservations
  /app/contracts
  /app/contracts/:id/payments   ← v2 payment schedule
/portal/login      → PortalLoginComponent (public)
/portal/*          → guarded by portal auth guard; portal JWT required
  /portal/contracts
  /portal/contracts/:id/payments
```

### Lazy Loading

Each feature is a standalone component imported lazily:

```typescript
// app.routes.ts
{
  path: 'app/contacts',
  loadComponent: () => import('./features/contacts/contacts.component')
    .then(m => m.ContactsComponent),
  canActivate: [authGuard]
}
```

---

## HTTP and Interceptors

Two functional interceptors are registered in `app.config.ts`:

### `authInterceptor`

Attaches the CRM JWT to every outgoing request to `/api/` paths:

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('hlm_token');
  if (token && req.url.includes('/api/')) {
    return next(req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    }));
  }
  return next(req);
};
```

Registration in `app.config.ts`:
```typescript
provideHttpClient(withInterceptors([authInterceptor, portalInterceptor]))
```

### `portalInterceptor`

Attaches the portal JWT only to `/api/portal/` paths:

```typescript
export const portalInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('hlm_portal_token');
  if (token && req.url.includes('/api/portal/')) {
    return next(req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    }));
  }
  return next(req);
};
```

### Local Storage Keys

| Key | Value |
|-----|-------|
| `hlm_token` | CRM JWT (ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT) |
| `hlm_portal_token` | Portal JWT (ROLE_PORTAL) |

---

## CRM Authentication Flow

1. User navigates to `/auth/login`.
2. `AuthComponent` submits `POST /auth/login` with `{email, password}`.
3. On success, response contains `{token, ...}`.
4. `AuthService` stores `token` in `localStorage.setItem('hlm_token', token)`.
5. Router navigates to `/app/dashboard`.
6. Subsequent requests to `/api/**` are intercepted by `authInterceptor`.
7. On logout, `AuthService` clears `localStorage.removeItem('hlm_token')` and navigates to `/auth/login`.

---

## Portal Authentication Flow

1. User navigates to `/portal/login`.
2. `PortalLoginComponent` submits `POST /api/portal/auth/request-link` with `{email}`.
3. Backend sends magic link email (fire-and-forget; frontend always shows success message regardless).
4. User clicks link: `GET /api/portal/auth/verify?token=...`.
5. Backend validates token and returns `{token, ...}` (portal JWT).
6. Frontend stores token in `localStorage.setItem('hlm_portal_token', token)`.
7. `portalInterceptor` attaches portal JWT to all `/api/portal/` calls.
8. Portal guard redirects to `/portal/contracts` on successful login.

---

## Adding a New Feature Module

1. **Create component file:** `src/app/features/{feature}/{feature}.component.ts`.

```typescript
@Component({
  selector: 'app-{feature}',
  standalone: true,
  imports: [CommonModule, RouterModule, /* ...other imports */],
  templateUrl: './{feature}.component.html'
})
export class FeatureComponent implements OnInit {
  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get('/api/{feature}').subscribe(data => { /* ... */ });
  }
}
```

2. **Add route** in `app.routes.ts`:

```typescript
{
  path: 'app/{feature}',
  loadComponent: () => import('./features/{feature}/{feature}.component')
    .then(m => m.FeatureComponent),
  canActivate: [authGuard]
}
```

3. **Use proxy-relative URLs** — always use paths like `/api/{feature}`, never `http://localhost:8080/api/{feature}`. The dev proxy rewrites the path, and in production Nginx proxies the request.

4. **Add navigation link** to the sidebar/nav component.

---

## Dev Proxy Configuration

`proxy.conf.json` forwards requests to the backend during `npm start`:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/auth": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/actuator": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

The `angular.json` `serve` configuration references this file via `"proxyConfig": "proxy.conf.json"`.

To run the frontend with the backend on a different port or host, update the `target` URLs in `proxy.conf.json` (local change only, not committed).

---

## Build and Production Bundle

```bash
cd hlm-frontend
npm run build
```

Output goes to `dist/hlm-frontend/browser/`. The build is optimized with:
- AOT compilation
- Tree-shaking (unused code elimination)
- `--configuration production` by default

Artifacts include:
- `index.html`
- Lazy-chunked JS bundles (one per feature route)
- CSS stylesheets
- Static assets

---

## Nginx Configuration

The production Docker image copies the build output into an Nginx container. `nginx.conf` key settings:

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Angular HTML5 pushState routing — all unknown paths serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API calls to the backend
    location /api/ {
        proxy_pass http://hlm-backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /auth {
        proxy_pass http://hlm-backend:8080;
    }

    location /actuator {
        proxy_pass http://hlm-backend:8080;
    }
}
```

The `try_files $uri $uri/ /index.html` rule is essential for Angular client-side routing — it ensures that refreshing the browser on any deep route (e.g., `/app/contacts/123`) serves `index.html` and lets Angular handle routing.
