# Modules and Features v2

This document provides deep module-level details: purpose, inputs, outputs, dependencies, interactions, and usage scenarios.

## 1. Auth and Security
### Purpose
Authenticate users and enforce RBAC + tenant isolation.

### Inputs
- CRM login payload (`tenantKey`, `email`, `password`)
- Portal token verification request (`token`)

### Outputs
- CRM JWT (`sub=userId`, `tid`, `roles`, `tv`)
- Portal JWT (`sub=contactId`, `tid`, `roles=[ROLE_PORTAL]`)

### Dependencies
- `auth/*`, `user/*`, `tenant/*`, `security` filter chain

### Key interactions
- JWT claims populate `TenantContext`
- Role checks enforced via `SecurityConfig` and `@PreAuthorize`

### Use case
- Manager logs in and accesses portfolio operations under own tenant boundary.

## 2. Contacts and Prospects
### Purpose
Manage lead and client records, qualification statuses, interests, and timeline.

### Inputs
- contact create/update payloads
- status transition payloads
- interest add/remove actions

### Outputs
- paginated contact lists
- timeline events aggregated from audit/messages/notifications

### Dependencies
- `contact/*`, `audit/*`, `outbox/*`, `notification/*`

### Business logic highlights
- controlled status transitions
- tenant-safe lookups
- role-limited write actions

### Use case
- Agent reviews a prospect timeline before creating a reservation.

## 3. Projects and Properties
### Purpose
Structure commercial inventory and enforce lifecycle consistency.

### Inputs
- project/property CRUD payloads
- status updates

### Outputs
- project and property DTOs
- project-level KPI summaries

### Dependencies
- `project/*`, `property/*`, `dashboard/*`

### Business logic highlights
- project must be `ACTIVE` for commercial operations
- type-specific property validation
- commercial status transitions integrated with deposit/contract workflows

### Use case
- Manager creates a new project and adds inventory ready for sales operations.

## 4. Deposits (Reservation)
### Purpose
Reserve properties and handle reservation lifecycle events.

### Inputs
- deposit create/confirm/cancel requests

### Outputs
- deposit status transitions
- reservation PDF
- property state changes (`ACTIVE <-> RESERVED`)

### Dependencies
- `deposit/*`, `property/*`, `audit/*`

### Business logic highlights
- only `ACTIVE` property can receive new deposit
- reservation conflicts blocked
- cancellation/expiry releases inventory

### Use case
- Prospect pays reservation deposit; manager confirms reservation.

## 5. Contracts (Sales)
### Purpose
Model legal/commercial sale lifecycle and final sale event.

### Inputs
- create/sign/cancel actions

### Outputs
- contract lifecycle transitions (`DRAFT`, `SIGNED`, `CANCELED`)
- contract PDF
- buyer snapshot at signing

### Dependencies
- `contract/*`, `property/*`, `project/*`, `deposit/*`, `audit/*`

### Business logic highlights
- sale recognized at `SIGNED`
- signed contract cancellation performs controlled property rollback
- anti-double-sell enforced at service + DB level

### Use case
- Sales team closes transaction with signed contract and generated PDF.

## 6. Payments (Preferred v2)
### Purpose
Track payment schedule execution and post-sale cash-in behavior.

### Inputs
- schedule item create/update/issue/send/cancel requests
- payment recording actions

### Outputs
- schedule status transitions
- call-for-funds PDFs
- cash dashboard metrics

### Dependencies
- `payments/*`, `dashboard/*`, `outbox/*`, `reminder/*`

### Business logic highlights
- stateful item lifecycle
- reminder automation
- role-based write restrictions

### Use case
- Manager issues payment call and records received tranche payment.

## 7. Outbox Messaging and Notifications
### Purpose
Provide reliable asynchronous communication and in-app alerts.

### Inputs
- message composition requests
- scheduler dispatch cycles

### Outputs
- outbox state transitions (`PENDING`, `SENT`, `FAILED`)
- notification events

### Dependencies
- `outbox/*`, provider interfaces, `notification/*`

### Use case
- Contract event triggers customer communication and audit traceability.

## 8. Dashboards and Analytics
### Purpose
Deliver management-ready KPIs and drill-downs.

### Inputs
- date/project/agent filters

### Outputs
- commercial summary
- sales drill-down
- receivables summary
- cash dashboard

### Dependencies
- `dashboard/*`, contracts/deposits/payments repositories, cache layer

### Use case
- Director reviews monthly sales and overdue exposure by project.

## 9. Buyer Portal
### Purpose
Allow buyers to securely self-serve their own contract-related data.

### Inputs
- magic link request and verify calls

### Outputs
- portal JWT
- own contracts/payment schedule/property views

### Dependencies
- `portal/*`, `auth/*`, contract/payment services

### Use case
- Buyer receives magic link by email and checks payment schedule from portal.
