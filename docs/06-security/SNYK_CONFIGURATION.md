# Snyk Security Configuration Guide

Date: 2026-04-01

This guide explains the Snyk setup in this repository from start to finish:
- what is configured
- why it is configured that way
- what GitHub secrets and variables are required
- how each scan job behaves
- how to run the same scans locally

## 1. What the hardened setup covers

The repository now uses Snyk in three layers:

1. Open Source dependency scanning
   - backend Maven dependencies from `hlm-backend/pom.xml`
   - frontend npm dependencies from `hlm-frontend/package.json`
   - frontend scan explicitly includes `devDependencies` because build-tooling vulnerabilities matter in CI and developer environments
2. Snyk Code static analysis
   - scans the repository root for source-level security issues
   - respects the root `.snyk` policy file
3. Snyk Container scanning
   - builds and scans the backend image from `hlm-backend/Dockerfile`
   - builds and scans the frontend image from `hlm-frontend/Dockerfile`

The workflow file is:

- `.github/workflows/snyk.yml`

The policy file is:

- `.snyk`

## 2. Why this is stronger than the previous setup

The previous configuration was useful, but basic. The hardened version improves it in these ways:

- uses a pinned Snyk CLI version instead of a floating global install
- scans container images, not just source dependencies and code
- uploads SARIF to GitHub Security so findings appear in the Security tab
- uploads JSON artifacts for auditability and post-run investigation
- keeps monitor/report steps on `main` and scheduled runs so the Snyk UI stays current
- uses matrix jobs so backend and frontend are handled independently
- preserves artifacts even when a scan fails by delaying the hard failure until the end of each job
- keeps `.snyk` intentionally strict and excludes only generated or non-production content

## 3. Files changed

The Snyk hardening lives in these files:

- `.snyk`
- `.github/workflows/snyk.yml`
- `docs/06-security/SNYK_CONFIGURATION.md`
- `docs/06-security/README.md`

## 4. Step-by-step setup from start to end

### Step 1. Create the Snyk API token

Create a Snyk API token in the Snyk UI for the organization that owns this project.

Required GitHub secret:

- `SNYK_TOKEN`

This is mandatory. If it is missing, the workflow does not fail the repository, but it emits a warning and skips all Snyk jobs.

### Step 2. Configure the Snyk organization

Choose the Snyk organization that should receive tests, monitors, and code reports.

Supported configuration:

- GitHub secret `SNYK_ORG`
- or GitHub repository variable `SNYK_ORG`

The workflow accepts either. A repository variable is usually enough because the organization slug or id is not a secret.

### Step 3. Decide the enforcement thresholds

The workflow supports repository variables for policy tuning without editing YAML:

- `SNYK_OSS_SEVERITY_THRESHOLD`
  - default: `high`
- `SNYK_CODE_SEVERITY_THRESHOLD`
  - default: `high`
- `SNYK_CONTAINER_SEVERITY_THRESHOLD`
  - default: `high`
- `SNYK_OSS_FAIL_ON`
  - optional
  - accepted by Snyk Open Source: `all`, `upgradable`, `patchable`
  - if unset, the workflow uses Snyk’s strict default behavior and fails on any vulnerability at or above the selected threshold
- `SNYK_CONTAINER_FAIL_ON`
  - optional
  - accepted by Snyk Container: `all`, `upgradable`
  - if unset, the workflow uses the strict default behavior

Recommended expert baseline:

- keep all three severity thresholds at `high`
- leave both `FAIL_ON` variables unset unless you deliberately want “only fail when fixable”

### Step 4. Define the repository policy in `.snyk`

The root `.snyk` file is now intentionally strict:

```yaml
version: v1.25.0
ignore: {}
patch: {}
exclude:
  global:
    - docs/**
    - .artifacts/**
    - hlm-backend/target/**
    - hlm-frontend/dist/**
    - hlm-frontend/coverage/**
```

What this means:

- `ignore: {}` means no vulnerability is silently waived
- `patch: {}` means there are no Snyk patch exceptions in play
- `exclude.global` removes non-production or generated content from Snyk Code scope

Why these exclusions exist:

- `docs/**` often contains examples and snippets that are not executable production code
- `.artifacts/**` is generated during CI
- `hlm-backend/target/**`, `hlm-frontend/dist/**`, and `hlm-frontend/coverage/**` are build outputs, not source

Important rule:

- do not add permanent ignores casually
- if an ignore is truly required, it should be temporary, justified, and tracked in code review

### Step 5. Trigger the workflow on the right changes

The workflow runs on:

- `push`
- `pull_request`
- weekly schedule every Monday at `07:00 UTC`
- `workflow_dispatch`

The path filters include:

- `hlm-backend/**`
- `hlm-frontend/**`
- `docker-compose*.yml`
- `hlm-backend/Dockerfile`
- `hlm-frontend/Dockerfile`
- `.snyk`
- `.github/workflows/snyk.yml`

Why this matters:

- dependency changes trigger SCA
- Dockerfile changes trigger container scanning
- policy changes trigger re-evaluation
- scheduled runs catch newly disclosed vulnerabilities even when code has not changed

### Step 6. Pin the Snyk CLI version

The workflow now uses the official setup action and pins the CLI version:

```yaml
uses: snyk/actions/setup@v1.0.0
with:
  snyk-version: v1.1303.2
```

Why this is important:

- avoids surprise behavior from a floating CLI release
- makes scan behavior reproducible across runs
- gives you a deliberate upgrade point when Snyk changes flags or output behavior

### Step 7. Run Open Source scans as a matrix

The `open-source` job scans backend and frontend independently.

Backend configuration:

- manifest: `hlm-backend/pom.xml`
- package manager: `maven`
- project name: `hlm-backend-open-source`
- project attributes:
  - environment: `backend,internal,saas,hosted`
  - lifecycle: `production`
  - business criticality: `critical`

Frontend configuration:

- manifest: `hlm-frontend/package.json`
- package manager: `npm`
- project name: `hlm-frontend-open-source`
- extra flags:
  - `--dev`
  - `--strict-out-of-sync=true`
  - `--prune-repeated-subdependencies`
- project attributes:
  - environment: `frontend,external,saas,hosted`
  - lifecycle: `production`
  - business criticality: `high`

Why the frontend uses `--dev`:

- Snyk does not scan npm `devDependencies` by default
- in this project, Angular CLI, build tooling, and CI dependencies are part of the attack surface
- earlier audit work already showed that the remaining npm risk sits mainly in dev/build dependencies

### Step 8. Preserve artifacts before enforcing failure

Each scan step uses `continue-on-error: true` and writes its exit code to `GITHUB_OUTPUT`.

Then the workflow:

1. uploads JSON artifacts
2. uploads SARIF to GitHub Security
3. optionally publishes a monitor snapshot
4. fails the job at the very end if Snyk returned a blocking exit code

Why this is expert-level behavior:

- you do not lose scan output just because the gate failed
- investigators can still download the exact JSON from the failed run
- GitHub Security still receives the SARIF results

### Step 9. Publish monitor snapshots only where they make sense

Open Source and Container jobs run `snyk monitor` only on:

- scheduled runs
- pushes to `main`

Why:

- PRs should test and gate, but they should not create long-lived project snapshots for every branch push
- `main` and scheduled runs are the right places to keep the Snyk UI inventory current

The monitor steps include project metadata:

- `--project-name`
- `--project-environment`
- `--project-lifecycle`
- `--project-business-criticality`
- `--project-tags`

That metadata makes the Snyk UI much easier to manage at scale.

Note:

- Open Source monitoring uses `--target-reference`
- Container monitoring uses the Dockerfile, policy path, and project metadata, but Snyk does not meaningfully use `--target-reference` for `container monitor`

### Step 10. Run Snyk Code from the repository root

The `code` job scans the repository root with:

- SARIF output
- JSON artifact output
- optional `--report` on `main` and scheduled runs

When reporting is enabled, the workflow also sets:

- `--project-name=yem-saas-platform-code`
- `--target-name=<owner/repo>`
- `--target-reference=<branch>`
- `--remote-repo-url=https://github.com/<owner/repo>`

Why:

- PRs get a blocking code-security gate
- `main` and scheduled runs also keep the Snyk Code project updated in the Snyk UI

### Step 11. Build and scan container images

The `container` job builds both application images locally:

- backend image from `hlm-backend/Dockerfile`
- frontend image from `hlm-frontend/Dockerfile`

After build, it runs:

- `snyk container test`
- `snyk container monitor` on `main` and scheduled runs

Why container scanning matters here:

- base image vulnerabilities are outside the Maven/npm dependency graph
- OS packages, runtime layers, and Dockerfile decisions are part of the deployed attack surface
- this is especially valuable for the backend JRE image and frontend Nginx runtime image

### Step 12. Upload SARIF into GitHub Security

Each scan family uploads SARIF with a distinct category:

- `snyk-open-source-backend`
- `snyk-open-source-frontend`
- `snyk-code`
- `snyk-container-backend`
- `snyk-container-frontend`

Why this helps:

- findings are visible in GitHub Security, not just in raw logs
- categories keep result sets separated and easier to review

### Step 13. Keep the missing-token case safe

If `SNYK_TOKEN` is missing:

- the workflow emits a warning
- scan jobs are skipped
- the repository does not fail on configuration absence alone

This avoids noisy failures on forks or freshly cloned repositories before secrets are configured.

## 5. Exact workflow behavior by job

### `check-token`

Purpose:

- detect whether `SNYK_TOKEN` exists

Output:

- `configured=true|false`

### `open-source`

Purpose:

- scan backend and frontend dependency graphs

Outputs:

- JSON artifact per project
- SARIF per project
- Snyk monitor snapshots on `main` and weekly schedule

Failure condition:

- non-zero Snyk exit code after artifact upload and monitor attempt

### `code`

Purpose:

- scan the repository for source-level security issues

Outputs:

- JSON artifact
- SARIF upload to GitHub Security
- Snyk Code report on `main` and weekly schedule

Failure condition:

- non-zero Snyk exit code after artifacts are preserved

### `container`

Purpose:

- scan the built backend and frontend images

Outputs:

- JSON artifact per image
- SARIF per image
- Snyk monitor snapshots on `main` and weekly schedule

Failure condition:

- non-zero Snyk exit code after artifacts are preserved

### `missing-token`

Purpose:

- make missing secret configuration explicit without breaking every fork PR

## 6. How to run the same scans locally

You can reproduce the CI behavior locally after authenticating the Snyk CLI.

### Local prerequisites

1. Install the Snyk CLI.
2. Authenticate:

```bash
snyk auth
```

Or export:

```bash
export SNYK_TOKEN=...
export SNYK_ORG=...
```

### Open Source local commands

Backend:

```bash
snyk test \
  --file=hlm-backend/pom.xml \
  --package-manager=maven \
  --policy-path=.snyk \
  --project-name=hlm-backend-open-source \
  --severity-threshold=high
```

Frontend:

```bash
cd hlm-frontend && npm ci && cd ..

snyk test \
  --file=hlm-frontend/package.json \
  --package-manager=npm \
  --policy-path=.snyk \
  --project-name=hlm-frontend-open-source \
  --dev \
  --strict-out-of-sync=true \
  --prune-repeated-subdependencies \
  --severity-threshold=high
```

### Snyk Code local command

```bash
snyk code test . --severity-threshold=high
```

### Container local commands

Backend image:

```bash
docker build -t local/hlm-backend-snyk:local -f hlm-backend/Dockerfile hlm-backend
snyk container test local/hlm-backend-snyk:local --file=hlm-backend/Dockerfile --severity-threshold=high
```

Frontend image:

```bash
docker build -t local/hlm-frontend-snyk:local -f hlm-frontend/Dockerfile hlm-frontend
snyk container test local/hlm-frontend-snyk:local --file=hlm-frontend/Dockerfile --severity-threshold=high
```

## 7. How to change the configuration safely

### To tighten security

- lower thresholds from `high` to `medium`
- keep `FAIL_ON` variables unset so any thresholded issue fails
- remove exclusions only if you want Snyk Code to inspect docs or generated content as well

### To reduce false positives without weakening policy too much

- prefer adjusting project metadata and triage in Snyk UI first
- add temporary ignores only with justification and expiry
- avoid excluding real source directories from `.snyk`

### To expand coverage later

Possible future upgrades:

- add dedicated IaC scanning if Terraform/Kubernetes manifests are introduced
- add Snyk API-based reporting or dashboards for org-wide policy drift
- pin GitHub Actions by full commit SHA if the team wants stricter supply-chain controls for CI actions themselves

## 8. Operating model

The intended operating model is:

1. PRs run all relevant Snyk tests and block merges on thresholded findings.
2. `main` pushes and weekly schedules also publish monitor/report snapshots to the Snyk platform.
3. GitHub Security receives SARIF so findings are visible in the repository UI.
4. Raw JSON artifacts remain attached to each run for auditing and incident response.

## 9. Final recommendation

Keep this setup strict:

- do not normalize long-lived `.snyk` ignores
- treat Snyk Code and Container as first-class controls, not optional extras
- keep the CLI version pinned and upgrade it intentionally
- review the weekly scheduled run, not just PR failures

This configuration is now suitable for a serious production repository rather than a minimal “Snyk enabled” checkbox setup.
