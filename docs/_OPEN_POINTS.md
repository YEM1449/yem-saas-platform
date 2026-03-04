# _OPEN_POINTS.md — Backlog of Open Questions

_Updated: 2026-03-04_

## [OP-001] Integration Test CI — Failsafe result check
**Context**: `backend-ci.yml` runs `failsafe:integration-test` but does not run `failsafe:verify` which is needed for the plugin to actually fail the build on IT failures.
**Options**:
1. Change `failsafe:integration-test` to `failsafe:integration-test failsafe:verify` ← **Recommended**
2. Use `mvn verify -DskipTests` to run IT + verify in one command
**Impact**: Currently IT failures may not fail the CI job. Low risk if developers also run locally.
**Owner**: DevOps / Team lead

## [OP-002] payments/ vs payment/ — Duplicate Packages
**Context**: Two packages exist: `payment/` and `payments/` under the backend root.
**Options**:
1. Merge into a single `payment/` package (requires code review)
2. Document the distinct responsibilities of each
**Impact**: Potential confusion for new engineers.
**Owner**: Backend team

## [OP-003] Snyk Code — `--severity-threshold` not supported
**Context**: `snyk code test` does not support `--severity-threshold` flag (as of Snyk CLI v1.x). The open-source job uses it correctly; the code job does not set a threshold, which means it always exits 0 unless errors exist.
**Options**:
1. Leave as-is (SARIF upload still works; findings show in GitHub Security tab)
2. Add `|| true` explicitly to make intent clear
**Recommendation**: Leave as-is — SARIF upload is the right pattern for code scanning.
**Owner**: DevOps

## [OP-004] Scheduled Snyk Scan
**Context**: No scheduled (cron) Snyk scan exists. New CVEs between PRs would only be caught when code changes trigger the workflow.
**Options**:
1. Add a weekly scheduled trigger to `snyk.yml` ← **Recommended**
2. Rely on Snyk's dashboard monitoring (requires `snyk monitor` to have run at least once)
**Owner**: DevOps

## [OP-005] Secret Scan — Audit-Only Mode
**Context**: `secret-scan.yml` always exits 0 (audit-only). Findings are uploaded as artifacts but never fail the build.
**Options**:
1. Keep audit-only (safe for false positives) ← **Current**
2. Fail on confirmed patterns (requires tuning to reduce false positives)
**Recommendation**: Keep audit-only; enable GitHub Advanced Security secret scanning for real enforcement.
**Owner**: DevOps / Security

## [OP-006] Frontend Lint Gate Missing
**Context**: `frontend-ci.yml` runs tests and build but no ESLint/TSLint step.
**Options**:
1. Add `npm run lint` step if `@angular-eslint` is configured in the project
2. Skip (add to backlog)
**Action needed**: Check if `angular.json` has lint target configured.
**Owner**: Frontend team

## [OP-007] Media Storage — Local vs Cloud
**Context**: `application.yml` configures local filesystem media storage (`app.media.storage-dir`). No cloud storage (S3, GCS) integration visible.
**Impact**: Deployments requiring horizontal scaling or cloud deployment need a shared storage solution.
**Owner**: Architecture

## [OP-008] PDF Generation — OpenHtmlToPDF Memory
**Context**: OpenHtmlToPDF is included as a dependency. No configuration for memory limits or async processing visible.
**Impact**: Large PDF generation may cause OOM under load.
**Owner**: Backend team
