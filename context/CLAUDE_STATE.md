# CLAUDE_STATE.md — Working State Snapshot

_Last updated: 2026-03-05_

## Branch
`Epic/sec-improvement`

## Program Status
- Open points in `docs/_OPEN_POINTS.md`: resolved/accepted/documented as tracked.
- CI security model: Snyk-based SAST/OSS scanning + audit-mode secret scan.
- GHAS-dependent workflows (`codeql.yml`, `dependency-review.yml`) are removed.

## Platform Baseline
- Backend: Spring Boot 3.5.8 / Java 21
- Frontend: Angular 19.2
- Data: PostgreSQL + Liquibase
- Caching: Caffeine
- CI: `backend-ci`, `frontend-ci`, `snyk`, `secret-scan`

## Current Documentation State
- Core onboarding/dev docs restructured for pedagogical clarity:
  - `docs/00_OVERVIEW.md`
  - `docs/05_DEV_GUIDE.md`
  - `docs/08_ONBOARDING_COURSE.md`
  - `docs/09_NEW_ENGINEER_CHECKLIST.md`
  - `docs/api-quickstart.md`
- Sales specs under `docs/specs/sales/` aligned to implemented behavior and terminology.
- Context files cleaned for prompt efficiency and architectural accuracy.

## Verification Notes
- Documentation cross-reference scan: no missing local markdown links in `docs/` and `context/`.
- Command consistency: integration-test references standardized to `failsafe:integration-test failsafe:verify`.
- Formatting sanity: no whitespace issues in modified markdown files.

## Known Environment Limitation
- Full backend test suite may fail in this local environment due Mockito inline agent attachment limits on WSL/JDK21.
- Use targeted test execution where required and verify full suite in CI/compatible runtime.

## Next Recommended Documentation Work
1. Expand `docs/api.md` into a complete endpoint index including portal + payments v2 emphasis.
2. Add sequence diagrams (Mermaid) for contract sign/cancel and portal auth flows.
3. Add architecture decision records for payment v1 deprecation timeline and migration plan.
