# CLAUDE_STATE.md — Single Source of Truth

_Last updated: 2026-03-05_

## Current Branch
`Epic/sec-improvement`

## Current Phase
**ALL OPEN POINTS RESOLVED** (2026-03-05)

## Stack
Spring Boot 3.5.8 / Java 21 / Angular 19.2 / PostgreSQL / Liquibase / Caffeine  
CI: 4 workflows (backend-ci, frontend-ci, snyk, secret-scan)  
Note: `codeql.yml` and `dependency-review.yml` removed — Snyk Code + Snyk OSS cover SAST and dependency vulnerability scanning without GHAS.

## Open Points Log
| OP | Status | Action |
|----|--------|--------|
| OP-001 | ✅ RESOLVED | failsafe:verify added to backend-ci.yml |
| OP-002 | ✅ RESOLVED | payment/ v1 controller deprecated; migration path to payments/ v2 documented |
| OP-003 | ✅ ACCEPTED | Snyk Code threshold — leave as-is; SARIF works |
| OP-004 | ✅ RESOLVED | Weekly Snyk cron added to snyk.yml |
| OP-005 | ✅ ACCEPTED | Secret scan audit-only — keep; GHAS for enforcement |
| OP-006 | ✅ DOCUMENTED | ESLint not configured; setup steps in 05_DEV_GUIDE.md |
| OP-007 | ✅ DOCUMENTED | Cloud swap in 07_RELEASE_AND_DEPLOY.md + ARCHITECTURE.md |
| OP-008 | ✅ DOCUMENTED | PDF JVM tuning in 07_RELEASE_AND_DEPLOY.md + ARCHITECTURE.md |
| OP-009 | ✅ RESOLVED | codeql.yml and dependency-review.yml removed; Snyk covers both scopes |

## Last Test Run
`./mvnw -B -ntp test` → 46 tests, 0 failures (2026-03-04)

## Files Changed This Session
- `.github/workflows/codeql.yml` — REMOVED
- `.github/workflows/backend-ci.yml` — failsafe:verify added (previous session)
- `.github/workflows/snyk.yml` — weekly cron added (previous session)
- `.github/workflows/dependency-review.yml` — REMOVED
- `docs/01_ARCHITECTURE.md` — payment/payments distinction + media + PDF sections
- `docs/05_DEV_GUIDE.md` — ESLint prerequisite section
- `docs/07_RELEASE_AND_DEPLOY.md` — CodeQL removed from table; media + PDF prod notes
- `context/ARCHITECTURE.md` — payment/payments, media, PDF sections added
- `context/SECURITY_BASELINE.md` — CI gates table updated (CodeQL removed)
- `docs/_OPEN_POINTS.md` — all OPs resolved/documented
- `docs/_TODO_NEXT.md` — updated backlog
- `context/CLAUDE_STATE.md` — this file

## Next Commands to Run
```bash
# Verify backend still compiles + tests pass
cd hlm-backend && ./mvnw -B -ntp test
```

## Next Files to Touch (backlog)
- `context/DATA_MODEL.md` — entity list with Liquibase changeset refs
- `docs/specs/User_Guide.md` — Phase 3+4 portal/commercial intelligence sections
- `docs/api.md` — distinguish payment/ v1 vs payments/ v2 endpoints
