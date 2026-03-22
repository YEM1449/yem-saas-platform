# Implementation Plan — YEM SaaS Platform Security Audit

This plan was generated from a comprehensive security and architecture audit. Tasks are ordered by priority and dependency. Execute them sequentially using Claude Code.

## How to Use with Claude Code

For each task, tell Claude Code:

```
Read tasks/01-critical-null-guard-fix.md and implement it
```

Or to run all tasks in sequence:

```
Read tasks/IMPLEMENTATION_PLAN.md. Start with task 01 and work through each task file in order. After each task, run the specified tests before moving to the next.
```

## Task Overview

### Phase 1 — Critical Security Fixes (Immediate)

| Task | File | Risk | Effort | Description |
|------|------|------|--------|-------------|
| 01 | `01-critical-null-guard-fix.md` | 🔴 CRITICAL | 30 min | Fix null societeId bypass in Commission + Dashboard controllers |
| 02 | `02-societe-context-helper.md` | 🟠 MEDIUM | 1 hour | Extract shared `SocieteContextHelper` Spring component |
| 03 | `03-scheduler-context-standardization.md` | 🟠 MEDIUM | 1 hour | Standardize all scheduler SocieteContext handling |
| 04 | `04-audit-societe-switch.md` | 🟢 LOW | 20 min | Add audit logging for société switching |

### Phase 2 — Code Quality & Consistency

| Task | File | Risk | Effort | Description |
|------|------|------|--------|-------------|
| 05 | `05-rename-tenant-references.md` | 🟢 LOW | 1 hour | Rename legacy "tenant" references to "societe" |
| 06 | `06-jwt-secret-validation.md` | 🟢 LOW | 20 min | Add JWT secret minimum length validation at startup |
| 07 | `07-cors-production-guard.md` | 🟠 MEDIUM | 20 min | CORS production safety check |

### Phase 3 — Domain Extensions (New Features)

| Task | File | Risk | Effort | Description |
|------|------|------|--------|-------------|
| 08 | `08-task-module.md` | NEW | 3 hours | New Task/follow-up management module |
| 09 | `09-document-module.md` | NEW | 3 hours | New Document attachment module |

### Phase 4 — Defense in Depth

| Task | File | Risk | Effort | Description |
|------|------|------|--------|-------------|
| 10 | `10-rls-policies.md` | 🟠 MEDIUM | 2 hours | PostgreSQL Row-Level Security policies |

## Dependency Graph

```
01 (critical fix) ──→ 02 (helper extraction) ──→ 03 (scheduler standardization)
                                                       │
04 (audit logging) ─── independent                     │
05 (rename) ─── independent                            │
06 (JWT validation) ─── independent                    │
07 (CORS guard) ─── independent                        │
                                                       │
08 (task module) ─── depends on 02 (uses helper) ◄─────┘
09 (document module) ─── depends on 02 (uses helper)
10 (RLS) ─── independent, can run anytime after 05
```

## Verification After All Tasks

After completing all tasks, run the full verification:

```bash
# 1. Full backend test suite
cd hlm-backend && ./mvnw verify

# 2. Cross-société isolation test specifically
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT

# 3. Frontend build check
cd hlm-frontend && npm run build -- --configuration=production

# 4. Docker compose build
docker compose build
```
