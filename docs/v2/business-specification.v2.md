# Business Specification v2 — YEM SaaS Platform

Version: 2.0  
Date: 2026-03-05  
Status: Client-ready working baseline

## 1. Executive Summary
YEM SaaS Platform digitizes the commercial cycle for real-estate promotion teams, from prospecting to signed contracts and post-sale cash visibility.

It is built for multi-company operation with strict data isolation and role-based access, while enabling buyer-facing transparency through a secure portal.

### Strategic value
- Better sales control (reservation/sale state integrity)
- Better forecast quality (contract-based KPI semantics)
- Better customer trust (buyer portal and document access)
- Better operational resilience (auditable events and outbox messaging)

## 2. Business Context and Problem Statement
### Typical pain points addressed
- fragmented lead and contact information
- manual reservation tracking and risk of stock conflicts
- inconsistent sales reporting definitions
- low client visibility after reservation/signature

### Target transformation
- one tenant-scoped system of record for commercial operations,
- one consistent contract-based definition of a sale,
- one auditable lifecycle for deposits and contracts,
- one portal for buyer self-service contract/payment visibility.

## 3. Scope
### In scope (current platform)
- Multi-tenant CRM core (users/roles, contacts, properties, projects)
- Deposit lifecycle and reservation documents
- Contract lifecycle and contract PDF
- Commercial dashboards, discount analytics, receivables
- Payments module (v2) and reminders
- Outbox messaging (email/SMS queueing + dispatch)
- Buyer portal (magic link auth + own-data access)

### Out of scope (current)
- native accounting export orchestration
- e-signature workflow orchestration
- full legal compliance automation packs per jurisdiction
- cross-tenant collaboration/marketplace behavior

## 4. Personas and Needs
| Persona | Primary Need | Platform Support |
|--------|---------------|------------------|
| Admin | Govern users, data, and controls | Full admin endpoints, role/enablement management |
| Manager | Operate portfolio and team execution | Commercial write access and KPI dashboards |
| Agent | Handle assigned sales operations | Ownership-scoped access to contracts/deposits/payments |
| Buyer (Portal) | View own contract/payment/property data | ROLE_PORTAL read-only endpoints |
| Director | Trust KPI reporting for decisions | Contract-signature-based commercial metrics |

## 5. Functional Modules (Purpose, Inputs, Outputs, Dependencies)
### 5.1 Authentication and Security
- Purpose: authenticate users and enforce role/tenant boundaries.
- Inputs: login credentials or portal token.
- Outputs: CRM JWT or portal JWT.
- Dependencies: `auth`, `user`, `tenant`, `security` layers.

### 5.2 Contacts and Prospect Management
- Purpose: manage prospects/clients and qualification workflow.
- Inputs: contact data, status changes, interest actions.
- Outputs: contact records, timeline events, conversion state.
- Dependencies: `contact`, `notification`, `audit`, `outbox`.

### 5.3 Projects and Properties
- Purpose: organize inventory and track property lifecycle.
- Inputs: project/property CRUD operations, status updates.
- Outputs: inventory state, project KPIs.
- Dependencies: `project`, `property`, dashboards.

### 5.4 Deposits (Reservations)
- Purpose: reserve properties with controlled lifecycle states.
- Inputs: deposit create/confirm/cancel actions.
- Outputs: deposit status, reservation PDF, property state changes.
- Dependencies: `deposit`, `property`, `audit`.

### 5.5 Contracts (Sales)
- Purpose: define sales lifecycle and legal sale events.
- Inputs: contract create/sign/cancel operations.
- Outputs: signed sale records, buyer snapshots, contract PDFs.
- Dependencies: `contract`, `property`, `project`, `deposit`, `audit`.

### 5.6 Payments and Cash Tracking
- Purpose: monitor post-sale payment schedules and receivables.
- Inputs: schedule item creation, issue/send/cancel/payment actions.
- Outputs: schedule states, call-for-funds PDFs, cash KPIs.
- Dependencies: `payments`, `dashboard`, `outbox`, `reminder`.

### 5.7 Messaging and Notifications
- Purpose: ensure consistent outbound communication and in-app alerts.
- Inputs: compose requests, scheduler dispatch jobs.
- Outputs: outbox states (`PENDING/SENT/FAILED`), notifications.
- Dependencies: `outbox`, `notification`, provider interfaces.

### 5.8 Buyer Portal
- Purpose: secure self-service access for buyers.
- Inputs: email + tenant key (magic link), verify token.
- Outputs: portal JWT and buyer-scoped data responses.
- Dependencies: `portal`, `auth`, `contract`, `payment`.

## 6. Core Workflows
### 6.1 Lead to Reservation
1. Contact created and qualified.
2. Property set to `ACTIVE`.
3. Deposit created (`PENDING`) -> property `RESERVED`.
4. Deposit confirmed or canceled/expired.

### 6.2 Reservation to Sale
1. Contract created in `DRAFT`.
2. Contract signed -> `SIGNED`.
3. Property moves to `SOLD`.
4. Buyer snapshot captured for legal/commercial immutability.

### 6.3 Sale Cancellation (Controlled rollback)
1. Signed contract canceled.
2. Property rollback:
- to `RESERVED` when active confirmed deposit exists,
- to `ACTIVE` otherwise.

### 6.4 Buyer Portal Access
1. Request link (`POST /api/portal/auth/request-link`).
2. Verify link (`GET /api/portal/auth/verify`).
3. Buyer reads own contracts, payment schedule, and related property details.

## 7. KPI and Reporting Model
### KPI semantics
- Reservation: deposit `PENDING`/`CONFIRMED`
- Sale: contract `SIGNED`
- Revenue: `agreedPrice`
- Discount metrics: require `listPrice`

### Management dashboards
- Commercial summary and sales drill-down
- Receivables dashboard
- Cash dashboard (payment schedule module)

## 8. Non-Functional Expectations
- Security: role-based access and tenant isolation on every request path.
- Reliability: outbox retry strategy with terminal fail state.
- Traceability: commercial audit event trail.
- Performance: cache-enabled dashboard aggregations.
- Maintainability: additive Liquibase migrations and layered service boundaries.

## 9. Risks and Mitigations (Resolved Plan)
This section defines the **active risk treatment plan** for delivery and operations.

Risk scale used:
- Likelihood: Low / Medium / High
- Impact: Medium / High / Critical
- Status: Controlled / In Progress / Accepted

### 9.1 Operational Risk Register
| ID | Risk | Likelihood | Impact | Status | Mitigation and Control Plan | Owner | Monitoring Trigger | Contingency / Exit Criteria |
|----|------|------------|--------|--------|------------------------------|-------|--------------------|------------------------------|
| R-01 | Legacy v1 payment API consumers (`payment/`) create migration friction | Medium | High | In Progress | Keep v1 endpoints available with deprecation headers (`Deprecation`, `Sunset`, `Warning`, `Link`), publish migration mapping to v2 (`payments/`), track adoption by endpoint traffic | Architecture + API Product Owner | v1 endpoint usage trend not decreasing month-over-month | Remove v1 only after 30 consecutive days of near-zero usage and formal release notice |
| R-02 | KPI interpretation inconsistencies across teams | Medium | High | Controlled | Enforce locked KPI semantics (`Sale = SIGNED contract`, reservation separate), centralize KPI logic in dashboard services, use shared glossary in specs/docs | Product + Data Owner | conflicting KPI values between reports or stakeholder escalation | Block KPI changes unless semantics and docs are updated together in same release |
| R-03 | PDF generation memory/latency pressure | Medium | High | Controlled | Keep JVM sizing baseline (`-Xmx512m` minimum), monitor render latency and error rates, optimize templates, prepare async offloading path for high-volume periods | Platform Operations | PDF p95 latency degradation or OOM event | Scale instance memory and enable async PDF generation roadmap if thresholds are exceeded |
| R-04 | Email/SMS provider mismatch or misconfiguration | Medium | High | Controlled | Provider abstraction with retry/backoff via outbox, environment validation before production cutover, staging smoke tests against real provider credentials | Integration Operations | outbound failure-rate spike, queue backlog growth | Fail closed to queued state, alert operations, and switch provider config without changing core code paths |
| R-05 | Tenant isolation regression | Low | Critical | Controlled | Tenant-scoped service/repository patterns, cross-tenant integration tests as release gate, security-focused code review checklist on sensitive modules | Backend Lead + Security Lead | any cross-tenant test failure in CI | Immediate release block and hotfix workflow; no production deploy until isolation tests pass |
| R-06 | Secret/configuration hygiene gaps | Medium | High | Controlled | Environment-only secrets, fail-fast JWT secret validation, CI scanning, periodic rotation policy for high-impact secrets | DevSecOps | missing/weak secret checks in CI or config drift findings | Freeze deployment pipeline until secret remediation and rotation are completed |
| R-07 | Scheduler/state drift in reminders and payment overdue flows | Medium | Medium | Controlled | Idempotent reminder design, manual trigger endpoints for recovery, dashboard-level visibility on overdue/queue state | Application Operations | overdue queue growth with no state transitions | Run controlled manual recovery, investigate scheduler health, and backfill missed runs |
| R-08 | Documentation drift from implementation | Medium | Medium | In Progress | “Docs update required” in Definition of Done, release checklist includes docs audit, maintain v2 as client-facing canonical set | Engineering Management | repeated support questions caused by doc mismatch | Reject PR/release that changes behavior without matching doc updates |

### 9.2 Immediate Governance Decisions
1. v1 payment endpoints remain temporary compatibility layer and are not feature-expansion targets.
2. Contract-signature semantics remain the sole source of truth for sales reporting.
3. Tenant isolation and security regressions are release-blocking defects.
4. Documentation updates are mandatory deliverables for behavior/API contract changes.

### 9.3 Residual Risks Explicitly Accepted
- Full compliance automation packs (jurisdiction-specific legal tooling) are deferred to future phases.
- Event-driven analytics projection architecture is deferred while current query/caching model remains within acceptable performance targets.

## 10. Future Evolution Guidance
1. Formalize event-driven analytics projections for higher volume.
2. Complete v1 payment endpoint retirement plan and client migration communication.
3. Add advanced compliance packs (retention/audit/legal templates) where required.
4. Expand portal branding, localization, and customer communication playbooks.

## 11. References
- [Overview v2](00_OVERVIEW.v2.md)
- [API v2](api.v2.md)
- [Quickstart v2](api-quickstart.v2.md)
- [Summary v1→v2](SUMMARY_v1_to_v2.md)
