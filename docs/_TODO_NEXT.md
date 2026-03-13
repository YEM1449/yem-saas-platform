# _TODO_NEXT.md — Next Actions

_Updated: 2026-03-13_

## Completed
- OP-001: resolved (failsafe:verify added)
- OP-002: **DONE** — v1 payment package (`payment/`) fully deleted from backend in Epic/sec-improvement; only `payments/` (v2) remains. OP-002 resolution notes updated.
- OP-003: accepted (leave as-is)
- OP-004: resolved (weekly Snyk cron)
- OP-005: accepted (audit-only secret scan)
- OP-006: documented (ESLint not configured; setup steps added to 05_DEV_GUIDE.md)
- OP-007: documented (cloud swap pattern + env vars in 07_RELEASE_AND_DEPLOY.md, ARCHITECTURE.md)
- OP-008: documented (PDF memory + JVM tuning in 07_RELEASE_AND_DEPLOY.md, ARCHITECTURE.md)
- OP-009: resolved (codeql.yml and dependency-review.yml removed; Snyk covers SAST + OSS dependencies)
- **Payment v1 retirement**: `payment/api/*` controllers deleted. DB tables retained (additive-only Liquibase rule). Frontend payment route updated to v2 component.
- **Frontend UI overhaul**: Global design system (CSS custom properties), sidebar shell layout, "New Contact" + "Add Property" modals, live search on contacts/properties tables, modernized all data tables with empty states and loading spinners.
- **docs/api.md**: v1 payment endpoints section updated to reflect deletion (not just deprecation).

## Backlog (Future Sessions)

### High Priority
- **OP-006 follow-up**: Add `@angular-eslint/schematics` to frontend; configure lint rules; add `npm run lint` to `frontend-ci.yml`.
- **GHAS enablement**: If GHAS is enabled later, optionally restore `codeql.yml` and `dependency-review.yml` from git history.

### Medium Priority
- **context/DATA_MODEL.md**: Create entity-to-Liquibase-changeset cross-reference map.
- **docs/specs/User_Guide.md**: Add Phase 3 (commercial intelligence) + Phase 4 (portal) user-facing content.
- **Media cloud**: Implement S3 `MediaStorageService` when moving to cloud deployment.
- **PDF async**: Queue PDF jobs in outbox for high-traffic scenarios.

### Low Priority
- **Snyk SNYK_TOKEN**: Verify CI has SNYK_TOKEN set and weekly scan is running after merge.
