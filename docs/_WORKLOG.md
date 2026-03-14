# _WORKLOG.md — Chronological Progress

_Updated: 2026-03-05_

## Phase 0 — Recon (2026-03-04) — Complete
- Stack: Spring Boot 3.5.8 / Java 21 / Angular 19.2 / PostgreSQL / Liquibase / Caffeine
- Historical snapshot at the time: 6 GitHub Actions workflows (before GHAS workflow removal); Snyk OSS+Code+SARIF; no `context/` dir existed
- Files created: context/CLAUDE_STATE.md, context/COMMANDS.md, docs/_WORKLOG.md, docs/_OPEN_POINTS.md, docs/_TODO_NEXT.md

## Phase 1 — Code Cleanup (2026-03-04) — Scoped/Deferred
- CI workflows clean; app code sweep deferred; Phase 6 covers workflow cleanup

## Phase 2 — Docs Cleanup (2026-03-04) — Complete
- Created: docs/README.md, docs/00_OVERVIEW.md, docs/01_ARCHITECTURE.md, docs/05_DEV_GUIDE.md, docs/07_RELEASE_AND_DEPLOY.md

## Phase 3 — Specs Update (2026-03-04) — Complete
- Functional_Spec.md: v1.1, Phase 3+4 scope, permission matrix, Appendix A+B
- Technical_Spec.md: v1.1, module table updated, Appendix A+B (technical details)

## Phase 4 — Context Files (2026-03-04) — Complete
- Created: context/PROJECT_CONTEXT.md, ARCHITECTURE.md, DOMAIN_RULES.md, SECURITY_BASELINE.md, CONVENTIONS.md

## Phase 5 — Onboarding Course (2026-03-04) — Complete
- Created: docs/08_ONBOARDING_COURSE.md (5-day, 9 labs, debugging playbook, glossary)
- Created: docs/09_NEW_ENGINEER_CHECKLIST.md

## Phase 6 — CI + Snyk Hardening (2026-03-04) — Complete
- backend-ci.yml: added failsafe:verify (OP-001 resolved)
- snyk.yml: added weekly schedule cron (OP-004 resolved)
- All workflow YAMLs validated (later reduced to 4 after removing GHAS-only workflows)

## Phase 7 — Final Verification (2026-03-04) — Complete
- mvnw compile → BUILD SUCCESS
- mvnw test → 46 tests, 0 failures, 0 errors

## Open Points Resolution Session (2026-03-04) — Complete

**Goal**: Resolve all OP-001 through OP-009 using recommended options.

**OP-001** — Already resolved in previous session (failsafe:verify).
**OP-003** — Accepted as-is (Snyk Code SARIF approach is correct; no threshold flag supported).
**OP-004** — Already resolved in previous session (weekly cron).
**OP-005** — Accepted as-is (audit-only; GHAS native secret scanning for enforcement).

**OP-002** — Initially documented as coexistence (`payment/` v1 + `payments/` v2). Subsequently resolved (2026-03-05): v1 controller deprecated and now emits migration headers toward v2 routes.

**OP-006** — Investigated: no `@angular-eslint` configured in `angular.json` or `package.json`. No CI lint step possible yet. Added setup instructions to `docs/05_DEV_GUIDE.md`.

**OP-007** — Investigated: `MediaStorageService` interface already designed for cloud swap (Javadoc says "@Primary S3-backed bean"). Documented swap pattern + env vars in `docs/07_RELEASE_AND_DEPLOY.md` and `context/ARCHITECTURE.md`.

**OP-008** — Investigated: `DocumentGenerationService.convertToPdf()` is synchronous + in-memory with `useFastMode()`. JVM tuning recommendations documented in `docs/07_RELEASE_AND_DEPLOY.md` and `context/ARCHITECTURE.md`.

**OP-009** — Removed `codeql.yml` and `dependency-review.yml` (GHAS not enabled). Snyk Code + Snyk OSS in `snyk.yml` cover SAST and dependency vulnerability scanning. Updated `docs/07_RELEASE_AND_DEPLOY.md` and `context/SECURITY_BASELINE.md`.

**Test run**: 46 tests, 0 failures. All 4 remaining workflow YAMLs valid.

**Files changed**:
- `.github/workflows/codeql.yml` — REMOVED
- `docs/01_ARCHITECTURE.md` — payment/payments/media/PDF sections
- `docs/05_DEV_GUIDE.md` — ESLint prerequisite
- `docs/07_RELEASE_AND_DEPLOY.md` — workflow table + media + PDF prod notes
- `context/ARCHITECTURE.md` — payment/payments/media/PDF sections
- `context/SECURITY_BASELINE.md` — CI gates updated
- `docs/_OPEN_POINTS.md` — all OPs closed
- `docs/_TODO_NEXT.md` — backlog updated
- `context/CLAUDE_STATE.md` — state updated

## Pre-existing IT Bug Fixes (2026-03-14) — Complete

**Goal**: Resolve all remaining integration test failures across the full 286-test suite.

**Root causes and fixes:**

1. **`@ConditionalOnProperty` treats empty string as "present"** — `SmtpEmailSender` was activated in tests because `app.email.host: ${EMAIL_HOST:}` resolves to `""` when `EMAIL_HOST` is unset, and Spring Boot's `@ConditionalOnProperty` considers `""` as "property exists". Fixed by switching to `@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")`, which correctly treats empty/blank as "not configured".

2. **JPQL null `LocalDateTime` params — PostgreSQL type inference** — `(:param IS NULL OR field >= :param)` pattern fails with a null `LocalDateTime` because PostgreSQL cannot infer the parameter type. Fixed with `CAST(:param AS LocalDateTime) IS NULL` in `OutboundMessageRepository.findByTenant()` and `CommercialAuditRepository.search()`.

3. **openhtmltopdf rejects HTML4 named entities** — The PDF renderer uses a SAX XML parser; `&mdash;` and `&nbsp;` are HTML4 named entities, not valid XML character references. Fixed `call_for_funds.html` (`&mdash;` → `&#8212;`) and `appel-de-fonds.html` (`&nbsp;` → `&#160;`).

4. **RBAC: agents cannot create projects, properties, or deposits via API** — `ContractPdfIT` and `ReservationPdfIT` helpers used the test bearer (which might be `agentBearer`) for `POST /api/projects`, `POST /api/properties`, `PUT /api/properties`, and `POST /api/deposits` — all of which require `ADMIN` or `MANAGER`. Fixed by always using `adminBearer` for setup and adding a `createDepositForAgent()` helper that saves the `Deposit` entity directly via `DepositRepository` with the correct agent owner.

5. **StrongPassword validator** — Tests in `TenantControllerIT` and `AuthLoginIT` used passwords like `"supersecret"` (11 chars, no uppercase/digit/special) and `"Admin123!"` (9 chars). The `StrongPassword` validator requires `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{12,}$`. Updated to `"Supersecret1!"` and `"Admin123!Secure"`.

6. **PropertyType validation** — `PropertyImportIT` used `APARTMENT` (not a valid enum value; correct is `APPARTEMENT`). `VILLA` rows in import CSV were missing required `surfaceAreaSqm` and `landAreaSqm` fields.

7. **PropertyMediaIT VILLA setup** — `PropertyCreateRequest` for VILLA was missing all four required fields (`surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms`), causing 400 on property creation.

**Files changed (production):**
- `SmtpEmailSender.java` — `@ConditionalOnProperty` → `@ConditionalOnExpression`
- `OutboundMessageRepository.java` — CAST fix + `CLOCK_TIMESTAMP()` for native query
- `CommercialAuditRepository.java` — CAST fix for null LocalDateTime params
- `call_for_funds.html` — `&mdash;` → `&#8212;`
- `appel-de-fonds.html` — `&nbsp;` → `&#160;` (3 occurrences)

**Files changed (tests):**
- `TenantControllerIT.java`, `AuthLoginIT.java` — passwords meeting StrongPassword requirements
- `PropertyMediaIT.java` — VILLA required fields
- `PropertyImportIT.java` — `APARTMENT` → `APPARTEMENT` + VILLA fields in CSVs
- `ContractPdfIT.java` — adminBearer for all setup steps
- `ReservationPdfIT.java` — adminBearer for setup + `createDepositForAgent()` helper

**Final test count**: 286 integration tests, 0 failures, 0 errors.

---

## Phase 8 — Audit Findings Closure (2026-03-05) — Complete
- Added configurable rate limiting via `app.rate-limit.*` (capacity, refill period, message) with validated typed properties.
- Deprecated v1 payment schedule API controller (`payment/`) and added `Deprecation`/`Sunset`/`Warning`/`Link` headers for migration to `payments/` v2.
- Added optional secret-scan enforcement switch (`SECRET_SCAN_ENFORCE=true`) in CI.
- Cleaned and aligned core `docs/` + `context/` files:
  - workflow counts (4)
  - portal auth endpoint (`/api/portal/auth/request-link`)
  - OP status consistency (OP-002 resolved)
