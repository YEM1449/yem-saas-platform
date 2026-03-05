# _WORKLOG.md — Chronological Progress

_Updated: 2026-03-04_

## Phase 0 — Recon (2026-03-04) — Complete
- Stack: Spring Boot 3.5.8 / Java 21 / Angular 19.2 / PostgreSQL / Liquibase / Caffeine
- 6 GitHub Actions workflows; Snyk OSS+Code+SARIF; no context/ dir existed
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
- All 6 YAML files validated OK

## Phase 7 — Final Verification (2026-03-04) — Complete
- mvnw compile → BUILD SUCCESS
- mvnw test → 46 tests, 0 failures, 0 errors

## Open Points Resolution Session (2026-03-04) — Complete

**Goal**: Resolve all OP-001 through OP-009 using recommended options.

**OP-001** — Already resolved in previous session (failsafe:verify).
**OP-003** — Accepted as-is (Snyk Code SARIF approach is correct; no threshold flag supported).
**OP-004** — Already resolved in previous session (weekly cron).
**OP-005** — Accepted as-is (audit-only; GHAS native secret scanning for enforcement).

**OP-002** — Investigated: `payment/` = v1 tranche model; `payments/` = v2 item workflow. Both serve active routes. Documented distinct responsibilities. No code change (merge is high-risk, low-value without dedicated refactoring sprint).

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
