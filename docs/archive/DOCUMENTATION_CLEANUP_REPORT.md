# Documentation Cleanup Report

_Generated: 2026-03-06_
_Branch: Epic/sec-improvement_
_Engineer: Claude Code (Senior SRE / Architect / DocExpert pass)_

---

## Summary

Full documentation audit and restructure for the `yem-saas-platform` repo.
Scope: all files under `docs/`, `context/`, and CI workflows.

---

## 1. Files Created

### New Numbered Core Docs
| File | Purpose | Replaces |
|------|---------|----------|
| `docs/00_OVERVIEW.md` | Mission, stack, domain glossary, phase history | `docs/overview.md` |
| `docs/01_ARCHITECTURE.md` | C4 diagrams, request pipeline, payment/media/PDF docs | `docs/architecture.md` |
| `docs/05_DEV_GUIDE.md` | Complete local setup, test commands, ESLint prerequisites | `docs/local-dev.md` |
| `docs/07_RELEASE_AND_DEPLOY.md` | CI table, release process, media cloud swap, PDF JVM tuning | `docs/CI_AUDIT_AND_RECOMMENDATIONS.md` |
| `docs/08_ONBOARDING_COURSE.md` | 5-day pedagogical ramp-up for new engineers | (new) |
| `docs/09_NEW_ENGINEER_CHECKLIST.md` | 6-gate formal readiness checklist | (new) |

### New Runbook
| File | Purpose |
|------|---------|
| `docs/runbooks/runbook_v2.md` | 14-section production-grade operational runbook |

The new runbook covers: operational profile, system context, architecture summary, component table, environment config (all env vars), deployment procedures, monitoring & observability, incident response (P1–P4 playbooks), 12 troubleshooting scenarios, recovery & rollback procedures, maintenance tasks, 8 known limitations, and operational best practices.

### New Context Files (LLM-Ready)
| File | Purpose |
|------|---------|
| `context/PROJECT_CONTEXT.md` | Compact project overview for LLM sessions |
| `context/ARCHITECTURE.md` | Request pipeline, security matcher, JWT table |
| `context/DOMAIN_RULES.md` | Entity lifecycles, RBAC rules, workflow states |
| `context/SECURITY_BASELINE.md` | Security gates, RBAC annotations, CI security table |
| `context/CONVENTIONS.md` | Code conventions, Definition of Done |
| `context/COMMANDS.md` | All canonical commands in one place |

### Payments v2 Docs
| File | Purpose |
|------|---------|
| `docs/v2/payment-v1-retirement-plan.v2.md` | Runbook + communication templates for v1 retirement |
| `docs/v2/api.v2.md` | v2 API reference |
| `docs/v2/api-quickstart.v2.md` | v2 quick start |

### Work Tracking
| File | Purpose |
|------|---------|
| `docs/_OPEN_POINTS.md` | All 9 open points tracked and resolved |
| `docs/_WORKLOG.md` | Chronological progress log |
| `docs/_TODO_NEXT.md` | Backlog |

---

## 2. Files Updated

| File | Change |
|------|--------|
| `docs/README.md` | Rebuilt as full navigation hub; added runbooks/ and archive/ sections; removed stale links |
| `docs/specs/Functional_Spec.md` | v1.0 → v1.1: Phase 3+4 scope, permission matrix, appendices |
| `docs/specs/Technical_Spec.md` | v1.0 → v1.1: module table updated, appendices |
| `docs/_OPEN_POINTS.md` | All 9 OPs resolved/accepted/documented |
| `context/ARCHITECTURE.md` | Added payment v1/v2 comparison table, portal endpoint, sunset date |
| `context/SECURITY_BASELINE.md` | CI gates table updated: CodeQL removed, Dependency Review removed |
| `.github/workflows/backend-ci.yml` | Added `failsafe:verify` to integration-test step |
| `.github/workflows/snyk.yml` | Added weekly cron trigger (Monday 07:00 UTC) |

---

## 3. Files Archived

Moved to `docs/archive/`. Content superseded by current docs; preserved for historical reference.

| Archived File | Reason | Current Equivalent |
|---------------|--------|--------------------|
| `docs/overview.md` | Legacy alias page | `docs/00_OVERVIEW.md` |
| `docs/architecture.md` | Legacy alias page | `docs/01_ARCHITECTURE.md` + `context/ARCHITECTURE.md` |
| `docs/index.md` | Legacy alias page | `docs/README.md` |
| `docs/CI_AUDIT_AND_RECOMMENDATIONS.md` | Content absorbed | `docs/07_RELEASE_AND_DEPLOY.md` |
| `docs/business-specification.md` | French v1 spec, superseded | `docs/v2/business-specification.v2.md` |
| `docs/local-dev.md` | Superseded | `docs/05_DEV_GUIDE.md` |
| `docs/runbook.md` | Superseded by v2 | `docs/runbooks/runbook_v2.md` |
| `docs/ai/PROJECT_CONTEXT.md` | Superseded | `context/PROJECT_CONTEXT.md` |
| `docs/ai/CLAUDECODE_PROMPT_TEMPLATE.md` | Internal tooling template | (archived, no equivalent needed) |

---

## 4. Files Deleted

### Zone.Identifier Files (20 files)
Windows NTFS alternate data stream metadata files with no content value; created automatically when files are downloaded on Windows and synced via WSL. Safely removed.

Files removed from:
- `docs/specs/` (12 files): `Backlog_Priorities.md`, `Backlog_Status.md`, `CDC_Source.md`, `Functional_Spec.md`, `Gap_Analysis.md`, `Implementation_Status.md`, `Requirements_Index.md`, `Spec_Deltas.md`, `Technical_Spec.md`, `User_Guide.md` — all `*:Zone.Identifier` variants
- `docs/specs/sales/` (7 files): all sales spec `*:Zone.Identifier` variants
- `docs/ai/` (2 files): `PROJECT_CONTEXT.md:Zone.Identifier`, `CLAUDECODE_PROMPT_TEMPLATE.md:Zone.Identifier`

### CI Workflows Deleted
| File | Reason |
|------|--------|
| `.github/workflows/codeql.yml` | Requires GHAS (not available on this private repo); Snyk Code covers SAST |
| `.github/workflows/dependency-review.yml` | Requires GHAS; Snyk OSS covers dependency scanning |

---

## 5. Improvements Made

### Runbook Quality
- Old `runbook.md`: 159 lines, basic troubleshooting tips.
- New `runbooks/runbook_v2.md`: 14 sections, ~450 lines, production-ready:
  - Incident severity matrix (P1/P2/P3/P4) with specific response playbooks
  - 12 named troubleshooting scenarios with diagnostic commands
  - Complete recovery and rollback procedures (JWT rotation, Liquibase rollback, outbox reset)
  - Maintenance task schedule (secret rotation, DB maintenance, v1 telemetry monitoring)
  - All environment variables documented (required + optional)
  - Known limitations with workaround guidance

### Documentation Structure
- New folder: `docs/runbooks/` for operational runbooks
- New folder: `docs/archive/` for superseded docs
- Placeholder folders created: `docs/guides/`, `docs/architecture/`, `docs/specifications/` (for future use)
- `docs/README.md` rebuilt as a single authoritative navigation hub

### Context Files (LLM Readiness)
- All 6 context files created under `context/` for LLM-assisted development
- Each file is scoped, compact, and machine-parseable
- `context/ARCHITECTURE.md` includes payment v1/v2 comparison table and security matcher order

### CI Hardening
- Integration test failures now correctly fail CI (added `failsafe:verify`)
- Snyk scans weekly on a cron in addition to PR-triggered scans
- Remaining CI surface: 4 workflows — `backend-ci.yml`, `frontend-ci.yml`, `snyk.yml`, `secret-scan.yml`

### Onboarding
- 5-day structured course with pedagogical model (Learn/Observe/Apply/Validate/Reflect)
- 6-gate formal checklist (access, architecture, backend, frontend, CI/operations, first contribution)

---

## 6. Active Docs Kept (Not Archived)

These files contain unique or actively referenced content:

| File | Status | Note |
|------|--------|------|
| `docs/backend.md` | Active | Referenced from onboarding course Day 2 |
| `docs/frontend.md` | Active | Referenced from onboarding course Day 3 |
| `docs/api.md` | Active | Referenced from onboarding course Day 2 |
| `docs/api-quickstart.md` | Active | Referenced from onboarding course Day 3 |
| `docs/database.md` | Active | Liquibase + schema reference |
| `docs/security.md` | Active | JWT + RBAC + multi-tenancy reference |
| `docs/contributing.md` | Active | Branching + PR conventions |
| `docs/specs/*.md` | Active | Functional/technical specs, backlog files |
| `docs/specs/sales/*.md` | Active | Sales module specs |
| `docs/adr/*.md` | Active | Architecture decision records |
| `docs/audit/` | Active | Security audit artifacts |
| `docs/requirements/` | Active | Requirements files |
| `docs/v2/` | Active | All v2 content is current |

---

## 7. Missing Information / Known Gaps

These items should be validated or completed in future work:

| Gap | Priority | Suggested Action |
|-----|----------|-----------------|
| `docs/api.md` and `docs/api-quickstart.md` duplicate v2 content for payment endpoints | Medium | Update to reference v2 paths; mark payment sections deprecated |
| `docs/specs/User_Guide.md` not updated for Phase 3+4 features (receivables, commission, portal) | Medium | Update with portal buyer workflows, cash dashboard, commission reporting |
| `docs/database.md` does not reference changesets 017–025 (outbox, commission, portal_token) | Low | Extend with latest Liquibase changesets table |
| `docs/security.md` predates portal magic-link and ROLE_PORTAL; may have stale claims | Medium | Review and align with `context/SECURITY_BASELINE.md` |
| `docs/guides/`, `docs/architecture/`, `docs/specifications/` folders empty | Low | Populate when content warrants folder-level grouping |
| No `context/DATA_MODEL.md` | Low | Create entity-to-Liquibase-changeset cross-reference |
| `docs/v2/CONTEXT_AND_CONFIGURATION.v2.md` and `MODULES_AND_FEATURES.v2.md` not cross-linked | Low | Add links from README and onboarding course |
| Frontend ESLint not configured (`@angular-eslint/schematics`) | High | Install + configure; add `npm run lint` to `frontend-ci.yml` |
| Snyk weekly cron — verify it runs after merge to main | Medium | Check GitHub Actions logs after Epic/sec-improvement is merged |

---

## 8. Final Docs Structure

```text
docs/
├── README.md                      ← Navigation hub (updated)
├── 00_OVERVIEW.md                 ← Mission, stack, glossary
├── 01_ARCHITECTURE.md             ← C4, request pipeline, payment/media
├── 05_DEV_GUIDE.md                ← Local setup (canonical)
├── 07_RELEASE_AND_DEPLOY.md       ← CI, release, deploy, media, PDF
├── 08_ONBOARDING_COURSE.md        ← 5-day course
├── 09_NEW_ENGINEER_CHECKLIST.md   ← 6-gate checklist
├── DOCUMENTATION_CLEANUP_REPORT.md
├── _OPEN_POINTS.md
├── _TODO_NEXT.md
├── _WORKLOG.md
├── api.md                         ← API catalog (keep, update payment sections)
├── api-quickstart.md              ← API quick start
├── backend.md                     ← Backend deep-dive
├── contributing.md                ← Contribution guidelines
├── database.md                    ← DB/Liquibase reference
├── frontend.md                    ← Frontend architecture
├── security.md                    ← Security reference
├── adr/                           ← Architecture decision records
├── archive/                       ← Superseded docs (do not delete)
│   ├── CI_AUDIT_AND_RECOMMENDATIONS.md
│   ├── architecture.md
│   ├── business-specification.md
│   ├── index.md
│   ├── local-dev.md
│   ├── overview.md
│   ├── runbook.md
│   └── ai/
│       ├── CLAUDECODE_PROMPT_TEMPLATE.md
│       └── PROJECT_CONTEXT.md
├── audit/
├── guides/                        ← (empty, ready for future guides)
├── architecture/                  ← (empty, ready for architecture docs)
├── requirements/
├── runbooks/
│   └── runbook_v2.md              ← Production runbook (new)
├── specs/
│   ├── Functional_Spec.md         ← v1.1 (updated)
│   ├── Technical_Spec.md          ← v1.1 (updated)
│   ├── User_Guide.md              ← (needs Phase 3+4 update)
│   ├── Implementation_Status.md
│   ├── Backlog_Priorities.md
│   ├── Backlog_Status.md
│   ├── CDC_Source.md
│   ├── Gap_Analysis.md
│   ├── Requirements_Index.md
│   ├── Spec_Deltas.md
│   └── sales/
│       └── *.md (7 sales module specs)
├── specifications/                ← (empty, ready for future use)
└── v2/
    ├── README.md
    ├── 00_OVERVIEW.v2.md
    ├── api.v2.md
    ├── api-quickstart.v2.md
    ├── business-specification.v2.md
    ├── payment-v1-retirement-plan.v2.md
    ├── SUMMARY_v1_to_v2.md
    └── ... (other v2 docs)
```

---

_End of report._
