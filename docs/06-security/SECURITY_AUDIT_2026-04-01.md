# Security Audit Report

Date: 2026-04-01

Scope:
- `hlm-backend` Spring Boot API
- `hlm-frontend` Angular SPA + Nginx container
- deployment hardening in `docker-compose.prod.yml`
- direct dependency review for frontend packages
- attempted dependency review for backend packages

## Executive Summary

This audit found several meaningful security issues in session handling, portal authentication, file-upload validation, browser hardening, and production deployment defaults.

The highest-risk problems were remediated in code:
- buyer portal JWTs are no longer persisted in browser `localStorage`
- portal verification no longer returns a reusable bearer token to frontend code
- portal request-link behavior no longer leaks whether a tenant exists
- unrestricted document upload paths now enforce file type and file size allow-lists
- SVG is no longer accepted for project and société logos
- the frontend container now emits stronger security headers including a Content Security Policy
- production compose now requires real secrets instead of silently accepting insecure fallbacks

Frontend dependency exposure was reduced substantially. The remaining `npm audit` findings are build-tooling issues in Angular CLI/devkit and require a semver-major migration to Angular 21 toolchain packages.

Backend dependency scanning could not be completed conclusively because external vulnerability feed tooling failed during this engagement. That remains an explicit residual risk.

## Methodology

The review combined:
- manual code inspection of authentication, portal, upload, and deployment paths
- frontend dependency review with `npm audit`
- targeted backend regression tests for the hardened areas
- frontend production build verification
- attempted backend SCA using OWASP Dependency-Check and an OSS Index-based alternative

Primary verification commands:

```bash
cd hlm-frontend && npm audit --json
cd hlm-frontend && npm run build -- --configuration production
cd hlm-backend && ./mvnw -B -ntp -Dtest=PortalAuthIT,PortalContractsIT,PortalPaymentsIT,PortalMagicLinkEmailIT,DocumentControllerIT,PropertyImportIT,JwtAuthenticationFilterTest test
```

Attempted backend dependency scan:

```bash
cd hlm-backend && ./mvnw -B -ntp -Dmaven.repo.local=/tmp/m2-repo -DskipTests org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=0 -Dformats=JSON
```

## Findings Remediated

### 1. Portal session token exposure in browser storage

Severity: Critical

Issue:
- the buyer portal stored an active JWT in `localStorage`
- any XSS in the portal would have allowed silent token theft and account takeover
- frontend code also injected that token into requests from JavaScript, increasing exposure

Remediation:
- moved portal auth to an `HttpOnly` cookie named `hlm_portal_auth`
- restricted cookie path to `/api/portal`
- backend verification now sets the cookie directly
- frontend no longer reads or stores the portal JWT in browser storage
- portal logout now actively clears the cookie server-side

Key changes:
- `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/PortalCookieHelper.java`
- `hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java`
- `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java`
- `hlm-frontend/src/app/portal/core/portal-auth.service.ts`
- `hlm-frontend/src/app/portal/core/portal.interceptor.ts`
- `hlm-frontend/src/app/portal/core/portal-session.store.ts`

### 2. Portal token leakage in API responses

Severity: High

Issue:
- the portal verification endpoint returned the live portal JWT in JSON
- request-link responses also exposed the raw magic-link URL in normal environments

Impact:
- browser extensions, frontend logging, or accidental telemetry could leak credentials
- developers could unknowingly ship a debug behavior into production

Remediation:
- portal verification now returns an empty `accessToken` field and sets only the cookie
- raw magic-link URLs are returned only when `app.portal.return-magic-link-in-response=true`
- that behavior is enabled for local/test profiles only
- no-store headers were added to sensitive auth responses

Key changes:
- `hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java`
- `hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java`
- `hlm-backend/src/main/resources/application.yml`
- `hlm-backend/src/main/resources/application-local.yml`
- `hlm-backend/src/test/resources/application-test.yml`
- `hlm-backend/src/main/java/com/yem/hlm/backend/auth/api/AuthController.java`

### 3. Tenant enumeration in buyer portal request-link flow

Severity: High

Issue:
- unknown `societeKey` requests behaved differently from known tenants
- this let an attacker probe valid tenant identifiers

Remediation:
- request-link now returns the same generic success response for missing tenants and unknown emails
- email normalization is applied before lookup and rate limiting

Key changes:
- `hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java`

### 4. Overly permissive document uploads

Severity: High

Issue:
- generic document upload accepted files without a strict allow-list
- this increased risk of stored XSS, malicious file hosting, and oversized upload abuse

Remediation:
- added backend allow-list validation for document MIME types
- added backend file-size enforcement
- restricted frontend file picker accept lists to the same allowed classes
- added multipart size limits to backend configuration

Key changes:
- `hlm-backend/src/main/java/com/yem/hlm/backend/document/service/DocumentService.java`
- `hlm-backend/src/main/resources/application.yml`
- `hlm-frontend/src/app/features/documents/document-list.component.html`
- `hlm-frontend/src/app/features/projects/project-detail.component.html`

### 5. SVG upload acceptance for logos

Severity: High

Issue:
- SVG is active content, not just a bitmap
- accepting user-controlled SVG for logos can create stored script execution and content-injection risks depending on serving behavior

Remediation:
- removed `image/svg+xml` from project and société logo allow-lists
- aligned frontend logo inputs with raster-only types

Key changes:
- `hlm-backend/src/main/java/com/yem/hlm/backend/project/service/ProjectService.java`
- `hlm-backend/src/main/java/com/yem/hlm/backend/societe/service/SocieteLogoService.java`
- `hlm-frontend/src/app/features/projects/project-detail.component.html`
- `hlm-frontend/src/app/features/superadmin/societes/societe-detail.component.html`

### 6. Weak browser hardening and opener leaks

Severity: Medium

Issue:
- the SPA container did not enforce a modern CSP or several complementary browser security headers
- multiple `_blank` navigations lacked `noopener`, enabling reverse-tabnabbing or opener abuse

Remediation:
- added CSP, `Permissions-Policy`, `Cross-Origin-Opener-Policy`, `Cross-Origin-Resource-Policy`, and `server_tokens off`
- set `X-Frame-Options: DENY`
- added `rel="noopener noreferrer"` and `window.open(..., 'noopener,noreferrer')` on externally opened links

Key changes:
- `hlm-frontend/nginx.frontend.conf`
- `hlm-frontend/src/app/features/documents/document-list.component.ts`
- `hlm-frontend/src/app/features/templates/template-list.component.ts`
- `hlm-frontend/src/app/features/templates/template-editor.component.ts`
- `hlm-frontend/src/app/features/properties/property-detail.component.html`

### 7. Sensitive tokens lingering in browser URLs

Severity: Medium

Issue:
- activation and portal magic-link tokens remained in the URL after use
- that creates avoidable leakage through browser history, screenshots, copy-paste, and referrer propagation

Remediation:
- activation and portal verification flows now strip the `token` query parameter after processing

Key changes:
- `hlm-frontend/src/app/features/activation/activation.component.ts`
- `hlm-frontend/src/app/portal/features/portal-login/portal-login.component.ts`

### 8. Unsafe production deployment defaults

Severity: High

Issue:
- the production overlay still inherited insecure fallback behavior from base compose
- important secrets could fall back to default values
- MinIO console/API exposure risk remained too easy to misconfigure

Remediation:
- production compose now requires `POSTGRES_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `MINIO_ROOT_USER`, and `MINIO_ROOT_PASSWORD`
- MinIO ports are no longer published by default in production
- backend production env explicitly receives object-storage credentials

Key changes:
- `docker-compose.prod.yml`

### 9. Portal route separation and session scoping

Severity: Medium

Issue:
- buyer and CRM sessions needed stronger separation to reduce cross-surface trust and routing mistakes

Remediation:
- `JwtAuthenticationFilter` now resolves the portal cookie only for `/api/portal/**`
- CRM cookie handling remains isolated for staff APIs
- public portal auth endpoints were made explicit in Spring Security

Key changes:
- `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java`
- `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java`

## Dependency Review

### Frontend dependencies

Status: Partially remediated

Packages upgraded:
- `@angular/common` to `^19.2.20`
- `@angular/compiler` to `^19.2.20`
- `@angular/core` to `^19.2.20`
- `@angular/forms` to `^19.2.20`
- `@angular/platform-browser` to `^19.2.20`
- `@angular/platform-browser-dynamic` to `^19.2.20`
- `@angular/router` to `^19.2.20`
- `@angular/compiler-cli` to `^19.2.20`
- `@angular/cli` to `^19.2.23`
- `@angular-devkit/build-angular` to `^19.2.23`
- `wrangler` to `^4.79.0`

Observed reduction during this engagement:
- initial `npm audit` findings: 31 total vulnerabilities
- remaining `npm audit` findings after remediation: 6 high, 0 critical

Current remaining findings:
- `@angular-devkit/build-angular`
- `@angular/cli`
- transitive `copy-webpack-plugin`
- transitive `pacote`
- transitive `serialize-javascript`
- transitive `tar`

Important interpretation:
- the remaining findings are dev/build-tooling issues, not direct runtime issues in the shipped Angular application bundle
- `npm audit` recommends a semver-major upgrade to Angular 21 toolchain packages (`21.2.5`) to fully clear them
- that migration was not forced in this hardening pass because it carries material build and compatibility risk

### Backend dependencies

Status: Inconclusive

Outcome:
- an OWASP Dependency-Check run was attempted but failed while updating vulnerability data from NVD feeds
- an OSS Index-based alternative was also attempted but did not return a usable component vulnerability report

Risk implication:
- backend code hardening was completed, but backend third-party dependency exposure is not yet fully audited
- a clean backend SCA run remains required before claiming full dependency assurance

## Verification Results

### Backend tests

Passed:

```bash
cd hlm-backend && ./mvnw -B -ntp -Dtest=PortalAuthIT,PortalContractsIT,PortalPaymentsIT,PortalMagicLinkEmailIT,DocumentControllerIT,PropertyImportIT,JwtAuthenticationFilterTest test
```

Result:
- `BUILD SUCCESS`
- `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`

What this verified:
- portal cookie issuance and portal auth behavior
- tenant-enumeration-safe request-link behavior
- document upload blocking for SVG
- property import blocking for non-CSV payloads
- auth filter regression coverage for portal cookie support

### Frontend build

Passed:

```bash
cd hlm-frontend && npm run build -- --configuration production
```

Result:
- production bundle generated successfully
- output written to `hlm-frontend/dist/frontend`

## Residual Risks

### 1. Backend dependency scan still pending

This is the largest unresolved audit gap. The code-level hardening is real, but backend dependency assurance is incomplete until SCA tooling completes successfully against current feeds.

### 2. Angular toolchain findings remain

The remaining 6 high findings are tied to Angular CLI/build tooling. They do not appear in the shipped runtime bundle, but they still matter for developer workstations and CI environments that execute the toolchain.

### 3. Future portal write endpoints need CSRF design review

Portal auth now uses an `HttpOnly` cookie, while global Spring Security CSRF protection remains disabled. The current portal surface is mostly read-only after login, so the immediate risk is limited. However, any future portal `POST`, `PUT`, `PATCH`, or `DELETE` endpoint should add explicit CSRF protections or strict origin validation before rollout.

### 4. Existing broader test suite may still contain outdated contact fixtures

Some test helpers outside the hardened target suite still create contacts without GDPR legal-basis metadata. That is a test-maintenance issue, not a production vulnerability, but it can hide regressions if the full suite is run later without cleanup.

## Recommended Next Steps

1. Complete a backend SCA run in an environment where NVD/OSS Index access is stable and authenticated as needed.
2. Plan an Angular 21 CLI/devkit migration to eliminate the remaining build-tooling advisories.
3. Add a dedicated CSRF/origin-check policy before expanding portal write operations.
4. Sweep the rest of backend tests for legacy `CreateContactRequest` fixtures that predate the GDPR validation rule.
5. Consider adding security-focused CI jobs for:
   - `npm audit --omit=optional`
   - backend SCA
   - targeted upload/auth regression tests

## Final Assessment

The platform is materially safer than at the start of this engagement. The most serious practical risks around browser token theft, portal credential leakage, tenant enumeration, unsafe uploads, and insecure production defaults were remediated and verified.

This is not yet a claim of perfect security. The remaining work is concentrated in dependency assurance and toolchain modernization rather than in the core application flaws addressed here.
