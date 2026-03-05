# Sales Test Plan — Implementation-Aligned
Date: 2026-03-05

## Purpose
Define minimum test coverage required to keep Sales workflows correct, tenant-safe, and regression-resistant.

## 1) Core Integration Scenarios

### Contract creation
- Create draft contract with active project -> success
- Create contract with archived project -> `400 ARCHIVED_PROJECT`
- Cross-tenant IDs (project/property/buyer/agent) -> `404`
- ADMIN/MANAGER without `agentId` -> `400`

### Contract signing
- Sign draft -> success, `status=SIGNED`
- Property transitions to `SOLD`
- Double sign / already sold race -> conflict handling (`409` semantics)
- Signing forbidden for AGENT callers -> `403`

### Contract cancellation
- Cancel signed contract -> success
- Signed cancel reverts property to:
  - `RESERVED` if active confirmed deposit exists
  - `ACTIVE` otherwise
- Cancel already canceled contract -> state conflict

### Document and access control
- Contract PDF download works for authorized caller
- AGENT cannot access another agent's contract/PDF (`404` or forbidden strategy)
- Cross-tenant contract access is blocked

## 2) KPI and Analytics Scenarios
- Signed contracts counted correctly (`salesCount`)
- Sales totals reflect `agreedPrice`
- Discount metrics computed only when `listPrice` exists
- Agent scoping enforced for AGENT dashboard callers
- Tenant isolation preserved in summary and drill-down endpoints

## 3) Audit Trail Scenarios
- `CONTRACT_CREATED` event is recorded
- `CONTRACT_SIGNED` event is recorded
- `CONTRACT_CANCELED` event is recorded
- Unauthorized roles cannot query commercial audit endpoint

## 4) Existing Integration Test Suites to Keep Green
- `ContractControllerIT`
- `ContractPdfIT`
- `CommercialDashboardIT`
- `CommercialAuditIT`
- `CommissionIT`
- `ReceivablesDashboardIT`

## 5) Unit Test Focus Areas
- Agent resolution and RBAC-dependent behavior in service layer
- Source deposit validation and mismatch paths
- Buyer snapshot capture logic on sign
- Discount/conversion helper calculations (where isolated)

## 6) Non-Functional Checks (Recommended)
- Concurrency stress around sign/cancel/deposit transitions
- Cache behavior sanity on dashboard endpoints after state transitions
- Error envelope consistency for all failure branches (`ErrorResponse` + `ErrorCode`)
