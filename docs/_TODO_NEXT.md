# _TODO_NEXT.md — Next Actions

_Updated: 2026-03-05_

## Completed This Session
All OPs from _OPEN_POINTS.md resolved or documented:
- OP-001: resolved (failsafe:verify added)
- OP-002: resolved (v1 payment controller deprecated + migration headers added)
- OP-003: accepted (leave as-is)
- OP-004: resolved (weekly Snyk cron)
- OP-005: accepted (audit-only secret scan)
- OP-006: documented (ESLint not configured; setup steps added to 05_DEV_GUIDE.md)
- OP-007: documented (cloud swap pattern + env vars in 07_RELEASE_AND_DEPLOY.md, ARCHITECTURE.md)
- OP-008: documented (PDF memory + JVM tuning in 07_RELEASE_AND_DEPLOY.md, ARCHITECTURE.md)
- OP-009: resolved (codeql.yml and dependency-review.yml removed; Snyk covers SAST + OSS dependencies)

## Backlog (Future Sessions)

### High Priority
- **Payment v1 retirement**: Publish deprecation notice to frontend/integrations and remove `payment/api/*` after migration window.
- **OP-006 follow-up**: Add `@angular-eslint/schematics` to frontend; configure lint rules; add `npm run lint` to `frontend-ci.yml`.
- **GHAS enablement**: If GHAS is enabled later, optionally restore `codeql.yml` and `dependency-review.yml` from git history.

### Medium Priority
- **context/DATA_MODEL.md**: Create entity-to-Liquibase-changeset cross-reference map.
- **docs/specs/User_Guide.md**: Add Phase 3 (commercial intelligence) + Phase 4 (portal) user-facing content.
- **Media cloud**: Implement S3 `MediaStorageService` when moving to cloud deployment.
- **PDF async**: Queue PDF jobs in outbox for high-traffic scenarios.

### Low Priority
- **api.md**: Distinguish `payment/` v1 vs `payments/` v2 endpoints in API catalog.
- **Snyk SNYK_TOKEN**: Verify CI has SNYK_TOKEN set and weekly scan is running after merge.
