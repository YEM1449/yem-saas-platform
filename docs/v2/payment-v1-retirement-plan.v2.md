# Payment API v1 Retirement Plan (v2)

Version: 1.0  
Date: 2026-03-05  
Scope: retirement of legacy `payment/` API endpoints and migration to `payments/` v2.

## 1. Objective
Retire payment API v1 safely, without business disruption, by:
- migrating all clients to payment v2 endpoints,
- giving clear communication and support to integrators,
- tracking residual v1 traffic with measurable telemetry,
- removing v1 only after objective exit criteria are met.

## 2. Current v1 Surface and Functional Dependency Analysis
### 2.1 Endpoint inventory
| v1 Endpoint | Purpose | Current Backend Owner | v2 Target | Migration Notes |
|---|---|---|---|---|
| `GET /api/contracts/{contractId}/payment-schedule` | Read schedule + tranches for one contract | `payment/api/PaymentScheduleController#get` | `GET /api/contracts/{contractId}/schedule` | Direct mapping |
| `POST /api/contracts/{contractId}/payment-schedule` | Create schedule | `payment/api/PaymentScheduleController#create` | `POST /api/contracts/{contractId}/schedule` | Direct mapping |
| `PATCH /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}` | Update tranche | `payment/api/PaymentScheduleController#updateTranche` | `PUT /api/schedule-items/{itemId}` | Method + route change |
| `POST /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}/issue-call` | Issue call for tranche | `payment/api/PaymentCallController#issueCall` | `POST /api/schedule-items/{itemId}/issue` | Direct intent mapping |
| `GET /api/payment-calls` | List payment calls globally | `payment/api/PaymentCallController#listCalls` | `GET /api/contracts/{contractId}/schedule` (+ cash dashboard) | No strict 1:1 global list endpoint in v2 |
| `GET /api/payment-calls/{id}` | Read one payment call | `payment/api/PaymentCallController#getCall` | Contract schedule lookup by `itemId` | No strict 1:1 endpoint by call ID |
| `GET /api/payment-calls/{id}/documents/appel-de-fonds.pdf` | Download call PDF | `payment/api/PaymentCallController#downloadPdf` | `GET /api/schedule-items/{itemId}/pdf` | Direct mapping |
| `GET /api/payment-calls/{id}/payments` | List payments for call | `payment/api/PaymentCallController#listPayments` | `GET /api/schedule-items/{itemId}/payments` | Direct mapping |
| `POST /api/payment-calls/{id}/payments` | Record payment | `payment/api/PaymentCallController#recordPayment` | `POST /api/schedule-items/{itemId}/payments` | Direct mapping |

### 2.2 Internal code dependencies currently using v1
- Legacy frontend module: `hlm-frontend/src/app/features/payments/`.
- Legacy CRM route: `/app/contracts/:contractId/payments` in `hlm-frontend/src/app/app.routes.ts`.
- Legacy backend IT coverage: `hlm-backend/src/test/java/com/yem/hlm/backend/payment/`.

### 2.3 Workflows and integrations currently affected
- Contract-level payment scheduling.
- Issuing call-for-funds.
- Recording tranche/call payments.
- Downloading legacy call PDF endpoint.
- Any external integration still calling `/payment-schedule` or `/payment-calls`.

Not in scope: portal route `/api/portal/contracts/{id}/payment-schedule` (portal endpoint, separate contract).

## 3. Retirement Timeline (Concrete Dates)
Sunset header currently exposed by v1 API: **2026-12-31 23:59:59 GMT**.

| Milestone | Date | Owner | Deliverables |
|---|---|---|---|
| Preparation complete | 2026-03-10 | Backend Lead | v1 deprecation headers on all v1 payment endpoints, telemetry counters/logs active, migration docs published |
| Client announcement sent | 2026-03-15 | Product + Customer Success | Announcement email to all client contacts + partner integrators |
| Migration window starts | 2026-03-16 | Integrations + Frontend Lead | v2 mapping support, office hours, migration checklist execution |
| New v1 onboarding freeze | 2026-05-01 | API Product Owner | No new client/integration approved on v1 |
| Midpoint control gate | 2026-07-01 | Architecture Board | Require >= 70% traffic moved to v2, top consumers identified |
| Final migration push | 2026-10-01 | Program Manager | T-90 campaign, escalation for remaining consumers |
| Sunset date | 2026-12-31 | Engineering + SRE | v1 endpoint shutdown release window |
| Code removal release | 2027-01-15 | Backend Lead | Remove `payment/api/*` controllers after post-sunset validation |

If migration KPI is below target by 2026-11-30, announce a revised sunset date and update the `Sunset` header before current date expires.

## 4. Exit Criteria, Rollback, and Contingency
### 4.1 Exit criteria before hard shutdown
- 30 consecutive days with v1 traffic <= 1% of total payment API requests.
- No production P1/P2 incidents linked to v2 payment APIs in last 14 days.
- All known internal clients migrated (frontend legacy module disabled).
- Final customer communication sent at T-30 and T-7.

### 4.2 Rollback strategy
- If shutdown causes critical client impact, roll back to previous backend release image within 30 minutes.
- Keep v1 database schema compatibility untouched during migration window (no destructive changes tied to v1 shutdown).
- Re-open support bridge and publish incident status update within 60 minutes.

### 4.3 Contingency options
- Partial extension (30 days) for specifically approved clients only.
- Temporary API gateway rewrite for a small subset of simple v1->v2 mappings.
- Fast-track addition of missing v2 read endpoint if a high-value integration requires `GET /api/payment-calls/{id}` semantics.

## 5. Client Communication Pack
Use these templates with exact dates and tenant/client-specific details.

### 5.1 Announcement email (T-291 to T-260)
Subject: Action Required — Payment API v1 Deprecation and Migration to v2

Hello {{ClientName}},

We are announcing the deprecation of legacy Payment API v1 endpoints (`/api/contracts/{contractId}/payment-schedule`, `/api/payment-calls*`).

- Deprecation active since: **March 2026**
- Sunset date: **December 31, 2026 (23:59:59 GMT)**
- Recommended target: Payment API v2 (`/api/contracts/{contractId}/schedule`, `/api/schedule-items/*`)

Why this change:
- clearer lifecycle model,
- improved reminder/cash workflow support,
- stronger long-term maintainability and consistency.

Next steps:
1. Review migration mapping guide (attached / linked).
2. Plan migration in your next sprint.
3. Confirm migration owner and ETA with us.

Support:
- Technical support: {{SupportEmail}}
- Migration office hours: {{OfficeHoursLink}}
- Escalation contact: {{CSMName}} ({{CSMEmail}})

Regards,  
{{SenderName}}  
{{Company}}

### 5.2 Migration instruction email (T-240 to T-120)
Subject: Payment API v1 to v2 Migration Steps and Validation Checklist

Hello {{ClientName}},

Your migration window for Payment API v1 -> v2 is now active.

Required endpoint changes:
- `GET /api/contracts/{contractId}/payment-schedule` -> `GET /api/contracts/{contractId}/schedule`
- `POST /api/contracts/{contractId}/payment-schedule` -> `POST /api/contracts/{contractId}/schedule`
- `PATCH /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}` -> `PUT /api/schedule-items/{itemId}`
- `POST /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}/issue-call` -> `POST /api/schedule-items/{itemId}/issue`
- `GET /api/payment-calls/{id}/documents/appel-de-fonds.pdf` -> `GET /api/schedule-items/{itemId}/pdf`
- `GET /api/payment-calls/{id}/payments` -> `GET /api/schedule-items/{itemId}/payments`
- `POST /api/payment-calls/{id}/payments` -> `POST /api/schedule-items/{itemId}/payments`

Validation checklist:
1. Authenticate with existing CRM JWT flow (unchanged).
2. Execute all payment flows in staging using v2 endpoints.
3. Validate RBAC behavior for ADMIN/MANAGER/AGENT.
4. Validate PDF generation and payment posting.
5. Confirm no v1 calls remain in logs/monitoring.

Please share your target cutover date by {{Date}}.

Regards,  
{{SenderName}}

### 5.3 Final reminder email (T-30 and T-7)
Subject: Final Reminder — Payment API v1 Shutdown on December 31, 2026

Hello {{ClientName}},

This is a reminder that Payment API v1 will be shut down on **December 31, 2026 (23:59:59 GMT)**.

If you are still using any v1 endpoint, migrate immediately to v2.  
Reference: {{MigrationGuideLink}}

If you need emergency support, contact:
- {{SupportEmail}}
- {{EscalationPhoneOrSlack}}

Regards,  
{{SenderName}}

## 6. Technical Migration Guide (Implementation-Ready)
### 6.1 cURL examples
Create schedule (v2):
```bash
curl -X POST "$BASE_URL/api/contracts/$CONTRACT_ID/schedule" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "label":"Fondations",
    "amount":300000,
    "dueDate":"2026-04-30",
    "notes":"Milestone 1"
  }'
```

Issue schedule item:
```bash
curl -X POST "$BASE_URL/api/schedule-items/$ITEM_ID/issue" \
  -H "Authorization: Bearer $TOKEN"
```

Record payment:
```bash
curl -X POST "$BASE_URL/api/schedule-items/$ITEM_ID/payments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount":120000,
    "paidAt":"2026-05-10",
    "channel":"BANK_TRANSFER",
    "paymentReference":"TRF-2026-0501",
    "notes":"First transfer"
  }'
```

Download PDF:
```bash
curl -L "$BASE_URL/api/schedule-items/$ITEM_ID/pdf" \
  -H "Authorization: Bearer $TOKEN" \
  -o call-for-funds.pdf
```

### 6.2 Known mapping gap
No strict v2 equivalent currently exists for:
- `GET /api/payment-calls` (global list),
- `GET /api/payment-calls/{id}` (single call by v1 call ID).

Mitigation:
- migrate UI/integrations to contract-scoped `GET /api/contracts/{contractId}/schedule`,
- use cash dashboard for cross-contract financial views,
- add a dedicated v2 read endpoint only if a migration blocker is confirmed.

## 7. Monitoring and Migration Support
### 7.1 Runtime telemetry (implemented)
- Counter: `payment_v1_requests_total` (tags: `endpoint`, `method`).
- Structured warning log marker: `payment_v1_endpoint_called`.
- HTTP deprecation headers returned on all v1 payment endpoints:
  - `Deprecation: true`
  - `Sunset: Wed, 31 Dec 2026 23:59:59 GMT`
  - `Warning: 299 ...`
  - `Link: </api/contracts/550e8400-e29b-41d4-a716-446655440000/schedule>; rel="successor-version"` _(the UUID is the actual contract ID from the request path — each v1 response carries the path-resolved URI, not a URI Template)_

### 7.2 Operational scripts
Static source scan for v1 usage:
```bash
./scripts/find-payment-v1-references.sh
```

Runtime usage summary from logs:
```bash
./scripts/report-payment-v1-usage.sh /var/log/hlm-backend/application.log
```

### 7.3 Weekly migration governance
- Publish top v1 callers by endpoint and tenant.
- Track migration KPI trend (weekly target: >= 10% drop in residual v1 traffic until <1%).
- Escalate accounts not moving by T-90.

## 8. Security, Compliance, and Performance Guardrails
- Keep tenant and RBAC behavior unchanged during migration (same authentication model).
- Do not log secrets/tokens; only endpoint, method, tenant ID, and principal identifier.
- Monitor v2 p95 latency and error-rate to prevent migration regressions.
- Keep auditability: migration and shutdown decisions should be recorded in release notes.

## 9. Responsibilities (RACI)
| Workstream | Responsible | Accountable | Consulted | Informed |
|---|---|---|---|---|
| API changes + telemetry | Backend Lead | Architecture Owner | DevSecOps | Product, Support |
| Frontend migration | Frontend Lead | Engineering Manager | QA | Product |
| Client communication | Customer Success | Product Owner | Engineering | All clients |
| Monitoring + reporting | SRE/DevOps | Operations Manager | Backend Lead | Program Manager |
| Final shutdown go/no-go | Architecture Board | CTO/Engineering Director | Product + Support | All stakeholders |

## 10. Future-Proofing Recommendations
1. Enforce API version lifecycle policy (`announce`, `deprecate`, `sunset`, `remove`) in release governance.
2. Require migration playbook + telemetry design before shipping any new major API surface.
3. Add contract tests for old->new endpoint parity during migration windows.
4. Prefer feature-flagged decommission switches for high-traffic endpoint retirement.
