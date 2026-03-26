# Changelog

All notable changes to this project follow [Semantic Versioning](https://semver.org/).
Format: `[BREAKING]` `[FEAT]` `[FIX]` `[SECURITY]` `[PERF]` `[DOCS]`

---

## [Unreleased] — 2026-03-26

### [FEAT] Multi-language support (i18n — Phase 1–5)

**Backend**
- Added `MessageSource` with `.properties` files for French, English, and Arabic (`messages.properties`, `messages_fr.properties`, `messages_en.properties`, `messages_ar.properties`).
- Added `UserLocaleResolver` — resolves locale from `Accept-Language` header → user `langueInterface` → French default.
- Added `Messages` helper bean for `LocaleContextHolder`-aware message lookup in services.
- Added `I18nConfig` — registers `UserLocaleResolver` as the Spring MVC `localeResolver` bean.
- `GET /auth/me` now returns `langueInterface` and `platformRole` fields.
- `PUT /auth/me/langue` — new endpoint. Persists the user's preferred language (`fr` / `en` / `ar`).

**Frontend**
- Installed `@ngx-translate/core` v17 and `@ngx-translate/http-loader` v17.
- Created translation files: `public/assets/i18n/fr.json`, `en.json`, `ar.json`.
- Configured `provideTranslateService` + `provideTranslateHttpLoader` in `app.config.ts`.
- `APP_INITIALIZER` applies saved language (`localStorage.hlm_lang`) on boot.
- New `LanguageSwitcherComponent` — FR / EN / ع buttons in sidebar footer and login footer. Fires `PUT /auth/me/langue` on change.
- Shell and superadmin-shell navigation labels converted to `| translate` pipe.
- Login form labels and buttons converted to `| translate` pipe.
- `MeResponse` model gains `langueInterface?` field.
- `AuthService.verifySession()` applies the user's persisted language on session restore.
- Created `src/styles-rtl.css` — RTL overrides for `[dir="rtl"]` (sidebar, tables, filter bars).
- `angular.json` includes `styles-rtl.css` in both build and test configurations.

---

### [SECURITY] Authentication hardening

- **Email enumeration timing-attack mitigation** (`AuthService.java`): `login()` now always runs a BCrypt comparison regardless of whether the email exists. A pre-computed dummy hash is used for non-existent accounts, equalising response time and preventing email enumeration via timing differences.
- **X-XSS-Protection header** (`SecurityConfig.java`): Added `1; mode=block` for legacy browser defence.
- **CSP expansion** (`SecurityConfig.java`): Added `img-src 'self' data:`, `font-src 'self'`, `connect-src 'self'`, `frame-ancestors 'none'`, `form-action 'self'`, `base-uri 'self'`.
- **Permissions-Policy** (`SecurityConfig.java`): Disable geolocation, microphone, camera, payment, USB, magnetometer, gyroscope via `StaticHeadersWriter` (replaces deprecated `permissionsPolicy()` API removed in Spring Security 6.4).
- **Referrer-Policy** (`SecurityConfig.java`): Added `strict-origin-when-cross-origin`.
- **Content-Type-Options** (`SecurityConfig.java`): Explicit `nosniff` declaration added.

---

### [FIX] index.html

- Title changed from `"Frontend"` to `"HLM CRM"` — removes generic placeholder that leaked technology stack.
- Added `<html lang="fr" dir="ltr">` (dynamically updated by `LanguageSwitcherComponent`).
- Added `<meta name="robots" content="noindex, nofollow">` — prevents search engine indexing of the authenticated app.
- Added `<meta name="description">` and security meta tags (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`).

---

### [FEAT] UX/UI redesign

- **`design-tokens.css`**: New standalone design-token file. Full CSS custom properties system — colour palette (10-shade blue scale + semantic aliases), spacing scale (4 px base), typography scale (xs–4xl), border radii, shadows (xs–2xl), transitions, z-index scale, focus-ring, and touch-target constant. Imported as first statement in `styles.css`.
- **Login page** (`login.component.css`, `login.component.html`): Full redesign.
  - Full-viewport centred layout with subtle radial-gradient backdrop.
  - Branded card: logo mark, product name, subtitle, rounded card with `box-shadow`.
  - Input focus: `border-color` shift + `box-shadow` focus ring (WCAG 2.2 AA compliant).
  - Submit button: hover lift (`translateY(-1px)` + shadow), loading spinner (CSS animation), `min-height: 44px` touch target.
  - Error state: icon + coloured alert panel with `role="alert"`.
  - Société selection step: card-style buttons with hover highlight.
  - `autocomplete` attributes on email / password inputs.
  - Language switcher embedded in card footer.
  - **Signature micro-interaction**: CSS spinner inside the submit button during loading.
- **Shell sidebar** (`shell.component.css`):
  - Active nav item: left border accent (`border-left: 2px solid #60a5fa`) — differentiating visual motif.
  - `focus-visible` ring on nav items and logout button.
  - Logout button `min-height: 36px` for accessible touch target.
- **Superadmin shell** (`superadmin-shell.component.html`/`.ts`): Added `LanguageSwitcherComponent` and `| translate` pipe.

---

### [PROD] Production build hardening

- `angular.json`: Explicit `"sourceMap": false`, `"optimization": true`, `"extractLicenses": true` in production configuration. Removes any ambiguity about source-map emission in prod.

---

### [DOCS]

- Created `SECURITY.md` — threat model, trust boundaries, attacker profiles, full security controls map, dependency audit process, vulnerability disclosure policy with SLA, security findings log.
- Created `CHANGELOG.md` (this file).
