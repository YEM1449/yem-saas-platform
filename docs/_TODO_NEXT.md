# _TODO_NEXT.md — Next Actions

_Updated: 2026-03-04_

## Completed This Session
- [x] Phase 0–7 complete (see _WORKLOG.md)
- [x] OP-001: Added failsafe:verify to backend-ci.yml
- [x] OP-004: Added weekly Snyk scan schedule

## Backlog (Future Sessions)

### High Priority
- OP-001 (resolved): Verify failsafe:verify works correctly in next CI run
- OP-002: Investigate payment/ vs payments/ packages — merge or document distinct responsibilities
- OP-006: Add `npm run lint` step to frontend-ci.yml if @angular-eslint is configured

### Medium Priority
- OP-007: Evaluate cloud storage (S3/MinIO) for media module
- OP-008: Add memory/timeout config for OpenHtmlToPDF PDF generation

### Low Priority
- OP-003: Consider `snyk code test || true` explicit comment for clarity
- OP-005: Enable GitHub Advanced Security for enforcement-grade secret scanning

### Documentation Gaps
- Create context/DATA_MODEL.md (entity list with Liquibase changeset cross-references)
- Update docs/specs/User_Guide.md with Phase 3+4 portal and commercial intelligence sections
