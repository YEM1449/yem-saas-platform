# Business Rules Audit And Target Rulebook

This document audits the business rules currently implemented in the solution and proposes a more professional target rulebook for a real-world real-estate developer CRM. It is intended to be read by product, engineering, sales operations, finance, and leadership together.

## 1. Scope

This audit is based on the current implementation in the main business modules, especially:

- [functional-spec.md](./functional-spec.md)
- [requirements-spec.md](./requirements-spec.md)
- [../context/MODULES.md](../context/MODULES.md)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/service/ContactService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/service/ContactService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/property/service/PropertyService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/property/service/PropertyService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/reservation/service/ReservationService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/reservation/service/ReservationService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/deposit/service/DepositService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/deposit/service/DepositService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/service/VenteService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/service/VenteService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contract/service/SaleContractService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contract/service/SaleContractService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/payments/service/PaymentScheduleService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/payments/service/PaymentScheduleService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/InvitationService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/InvitationService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/dashboard/service/DashboardCockpitService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/dashboard/service/DashboardCockpitService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/service/Project3dService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/service/Project3dService.java)
- [../../hlm-frontend/src/app/modules/viewer-3d/components/project-viewer-3d/project-viewer-3d.component.ts](../../hlm-frontend/src/app/modules/viewer-3d/components/project-viewer-3d/project-viewer-3d.component.ts)
- [../../hlm-frontend/src/app/features/projects/building-view/building-view.component.ts](../../hlm-frontend/src/app/features/projects/building-view/building-view.component.ts)
- [../../hlm-frontend/src/app/features/dashboard/dashboard-cockpit.service.ts](../../hlm-frontend/src/app/features/dashboard/dashboard-cockpit.service.ts)

Reading guide:

- Current rule: what the code implements today.
- Audit: whether that rule is strong, incomplete, risky, or not realistic enough for a professional promoteur workflow.
- Professional target: the recommended business rule for a production-grade product.

## 2. Executive Summary

The current solution already has a serious business backbone:

- strong multi-societe isolation
- clean role separation between `SUPER_ADMIN`, `ADMIN`, `MANAGER`, `AGENT`, and portal buyer
- a real commercial chain from contact to reservation, deposit, sale, contract, and collections
- meaningful dashboards, auditability, notifications, portal access, and a 3D viewer connected to live stock

The main gaps are not technical immaturity. They are domain-maturity gaps:

1. `vente` creation marks a property as `SOLD` too early for many real-life developer workflows.
2. reservation and deposit deadlines are hard-coded to 7 days instead of being configurable by project, tranche, channel, or approval level.
3. legal timeline defaults use French residential-sale concepts such as `SRU` and a 10-day retractation model, while the product positioning is Moroccan real-estate CRM.
4. company quotas are modeled for users, properties, contacts, and projects, but only user quota is clearly enforced in the inspected service layer.
5. the 3D viewer currently maps `WITHDRAWN` and `ARCHIVED` to `LIVRE`, which is visually convenient but semantically incorrect.
6. the buyer model is still too simple for professional developer operations: no co-buyers, no family unit, no company decision chain, no financing proof workflow, and no structured cancellation/refund policy.

The recommendation is to keep the architecture, keep the workflow chain, and harden the business rulebook around real-world developer operations rather than redesign the platform.

## 3. Governance, Tenancy, And Identity

### 3.1 Multi-societe governance

Current rules:

- every core aggregate is scoped by `societe_id`
- request scope is propagated through `SocieteContext`
- `SUPER_ADMIN` can create, update, suspend, reactivate, inspect, and impersonate societes
- societe suspension deactivates memberships and bumps `tokenVersion` for active users
- the last active `ADMIN` of a societe cannot be removed or demoted
- only `SUPER_ADMIN` can assign the `ADMIN` role

Audit:

- this is a strong SaaS governance baseline
- role-escalation prevention is correctly encoded as a business rule, not only as UI logic
- quotas exist in the societe model for users, properties, contacts, and projects, but only user quota is clearly enforced in the inspected services

Professional target:

- enforce all commercial quotas consistently: users, contacts, properties, projects, storage, portal seats
- add per-plan feature flags, not just numeric quotas
- add explicit suspension impact rules: read-only grace period, portal freeze, message freeze, and document download policy
- add a societe operating profile with jurisdiction, currency, VAT mode, reservation policy, and legal template pack

### 3.2 Staff authentication and membership

Current rules:

- login is rate-limited before database access
- email enumeration is mitigated by constant-time password comparison
- locked and disabled accounts cannot log in
- multi-societe users receive a partial token and must pick a societe
- `SUPER_ADMIN` can operate without societe membership using a platform token
- invitation activation requires a strong password and consumes the invitation token once

Audit:

- the security model is mature for a B2B SaaS CRM
- invitation activation is strong enough for MVP and better than many line-of-business systems
- activation and switching are technically safe, but the business trail could be richer

Professional target:

- add session-level audit views for admins: last login, last failed login, active devices, impersonation trail
- allow optional MFA for `SUPER_ADMIN` and finance-sensitive roles
- add membership start/end dates and temporary access windows for consultants, agencies, and external brokers
- add a formal offboarding rulebook: disable, revoke, transfer tasks, transfer pipeline ownership, preserve audit

### 3.3 Buyer portal authentication

Current rules:

- portal access uses a one-time magic link
- request-link always returns a generic response to prevent enumeration
- only the SHA-256 hash of the raw token is stored
- token TTL is 48 hours and the resulting portal JWT TTL is 2 hours
- verification consumes the token once

Audit:

- this is a good buyer-portal baseline
- the flow is safe and intentionally separate from staff authentication
- business-grade buyer servicing still needs more than authentication: explicit ownership context, consent, and service expectations

Professional target:

- add portal access policy by sale stage, not just by contact existence
- add portal welcome state with buyer identity confirmation, preferred language, and communication consent
- add a portal entitlement model per document type: contract, payment call, receipt, handover file, technical notice
- add token throttling per societe and per contact, plus resend cooldown in the UI


## 4. CRM, Leads, And Customer Management

### 4.1 Contact lifecycle

Current rules:

- contact creation blocks records that have neither explicit consent nor a stated processing basis
- duplicate email within the same societe is blocked
- a contact must keep at least one of phone or email
- the status machine is explicit:
  `PROSPECT -> QUALIFIED_PROSPECT -> CLIENT -> ACTIVE_CLIENT -> COMPLETED_CLIENT -> REFERRAL`
  with `LOST` as terminal/reopenable branch
- adding an interest or reservation publishes events that can auto-promote the contact
- conversion to client is currently implemented through the deposit workflow

Audit:

- the platform already treats contacts as operational entities, not as a flat address book
- GDPR and workflow rules are encoded in the service layer, which is good
- the model is still too individual-person oriented for real estate sales

Professional target:

- support buying parties, not just single contacts: co-buyer, spouse, guarantor, corporate buyer, legal representative
- add structured loss reasons, objection reasons, financing readiness, source campaign, and assigned sales stage owner
- add duplicate detection on phone, national ID, and fuzzy full-name matching
- add household-level notes and relationship mapping between contacts
- distinguish commercial qualification from legal/KYC qualification

### 4.2 Tasks, notifications, and outbound communication

Current rules:

- tasks default to the creator when no assignee is provided
- task completion stamps `completedAt`
- urgent task polling covers overdue and next-24h items
- notifications are recipient-scoped and deduplicated on insert
- outbound messages validate channel/recipient, persist as `PENDING`, then dispatch asynchronously with retry backoff

Audit:

- this is operationally useful and already good enough for day-to-day sales follow-up
- communication is still generic and not yet fully aligned with developer lifecycle events

Professional target:

- add SLA-driven task templates for reservation expiry, bank follow-up, notary coordination, call-for-funds, and delivery readiness
- add message template governance by event type and audience
- add conversation outcome tracking: reached, no answer, wrong number, callback requested, document requested
- add communication restrictions by consent, legal basis, quiet hours, and channel preference

## 5. Inventory, Projects, And Real-Estate Structure

### 5.1 Projects, immeubles, and tranches

Current rules:

- project name must be unique inside a societe
- downstream workflows that create properties or contracts use `ProjectActiveGuard`
- immeuble names are unique per project
- tranches advance forward-only and cannot skip stages
- tranche KPIs are computed from live unit statuses
- a 2D plan de commercialisation view renders each building as a floor stack with unit cards colour-coded by status; tranche pager navigates between tranches; absorption rate is computed live as `(SOLD + RESERVED) / (total − DRAFT)`

Audit:

- the hierarchy is suitable for developer inventory management
- tranche progression is a strong start, but still operationally light
- the 2D view provides immediate stock visibility without requiring a 3D model upload

Professional target:

- add project-level commercial settings: reservation validity, deposit minimum, currency, allowed property types, approval rules
- add tranche readiness gates: legal readiness, construction readiness, marketing readiness, delivery readiness
- expose absorption trend over time (not only current snapshot) so sales ops can track velocity per tranche
- add building metadata useful in real sales operations: entrance, block, orientation, elevator, parking stack, annexes
- add project and tranche closure rules so teams cannot sell from a non-commercial tranche by mistake

### 5.2 Properties and unit inventory

Current rules:

- property reference code is unique inside a societe
- type-specific validation is enforced for `VILLA`, `DUPLEX`, `APPARTEMENT`, `T2`, `T3`, `STUDIO`, `COMMERCE`, `LOT`, `TERRAIN_VIERGE`, and `PARKING`
- commercial statuses `RESERVED` and `SOLD` cannot be set manually through editorial endpoints
- editorial bulk status updates skip already-reserved or sold units
- projects must be active before units can be created under them
- CSV import is all-or-nothing and requires a proper `.csv` file

Audit:

- the split between editorial status control and commercial workflow control is correct
- type validation is useful, but real-world developer stock needs more structured unit metadata
- the current status set is too small for professional stock governance

Professional target:

- expand unit governance to include `MARKETING_READY`, `OPTIONED`, `UNDER_REVIEW`, `BLOCKED_LEGAL`, `UNDER_CONTRACT`, and `DELIVERED`
- model annex products and dependencies: parking, cave, box, terrace, garden, package sales
- add pricing governance: catalog price, approved discount corridor, floor premium, orientation premium, campaign price, manager override reason
- add legal-readiness fields: title readiness, permit readiness, notary pack completeness, delivery pack completeness
- add unit-level sales restrictions: staff-hold, legal-block, technical-block, finance-block

### 5.3 Documents and media

Current rules:

- project logos, generic documents, and 3D assets are stored through the storage abstraction
- document uploads enforce allowed MIME types and file-size limits
- filenames are sanitized before persistence and download

Audit:

- the file-handling baseline is sound
- business semantics for documents are still generic

Professional target:

- classify documents by business meaning, not only by attachment target
- add document completeness rules by lifecycle stage: reservation pack, financing pack, contract pack, delivery pack
- add mandatory-vs-optional document policy per project and buyer type
- add versioning and approval status for legal templates and generated documents

## 6. Commercial Lifecycle

### 6.1 Reservation

Current rules:

- a reservation can only be created on a property currently `ACTIVE`
- property locking uses pessimistic write locks
- an active reservation on the same property blocks another reservation
- default reservation expiry is `now + 7 days`
- canceling an active reservation releases the property back to `ACTIVE`
- converting a reservation to deposit requires the reservation to still be `ACTIVE` and the property to still be `RESERVED`

Audit:

- concurrency handling is good
- reservation is modeled as a real hold, not a note
- the default 7-day window is too rigid for real operations

Professional target:

- make reservation validity configurable by project, tranche, sales channel, and unit type
- add reservation reason, extension count, extension approver, and auto-escalation before expiry
- add a distinction between soft option, reservation, and signed reservation
- support reservation fee, refundability, and reservation document checklist
- require a cancellation reason taxonomy for analytics

### 6.2 Deposit

Current rules:

- a deposit requires a property and positive amount
- only one deposit per contact/property pair is allowed
- a property with an active reservation or active deposit cannot receive a new deposit
- the property must still be `ACTIVE` when the deposit is created
- default due date is `now + 7 days`
- default currency is `MAD`
- creating a deposit reserves the property and promotes the contact to a temporary client-like state
- confirming a deposit sets the contact to `CLIENT`
- canceling or expiring a non-confirmed deposit can release the unit and revert the contact to a qualified prospect

Audit:

- deposit is already the strongest real-world workflow in the platform
- the workflow still mixes commercial qualification and legal commitment a bit too early
- the model lacks refund, forfeiture, finance validation, and proof-of-payment logic

Professional target:

- add deposit policy by project: minimum amount, allowed payment method, confirmation SLA, refund rules, forfeiture rules
- add deposit proof workflow: receipt uploaded, finance validation, cashier reconciliation
- distinguish `PENDING_BANK_CLEARANCE` from `CONFIRMED`
- add approval rules for late confirmation, overdue extension, amount override, and refund exception
- add structured cancellation reasons: financing failure, customer withdrawal, stock issue, legal issue, seller decision

### 6.3 Vente pipeline

Current rules:

- a vente can be created directly or from a reservation
- when created from a reservation, the final sale price can be calculated from property price minus reservation advance and optional reduction
- creating a vente keeps the property in `RESERVED` (fixed — was incorrectly `SOLD` before P1 Wave 14 fix)
- advancing to `ACTE_NOTARIE` marks the property as `SOLD` (correct legal milestone for Moroccan immobilier)
- cancellation (`ANNULE`) releases the property back to `ACTIVE`/`RESERVED` as appropriate (fixed in P1 Wave 14)
- creating a vente advances the contact to `ACTIVE_CLIENT`
- legal dates are validated for coherence
- `dateCompromis` automatically derives `dateFinDelaiReflexion = +N days` and `dateLimiteFinancement = +M days` (both configurable via `app.vente.default-reflection-period-days` and `app.vente.default-financing-period-days`; field names updated from French SRU nomenclature in changeset 073)
- annulation requires a cancellation reason from the `MotifAnnulation` enum
- delivery (`LIVRE`) moves the contact to `COMPLETED_CLIENT`

Audit:

- the vente pipeline is rich enough to power dashboards and workflow screens
- the property SOLD-too-early bug has been corrected: RESERVED at creation, SOLD at ACTE_NOTARIE, released at ANNULE
- the SRU/French legal naming has been replaced with jurisdiction-neutral field names (`dateFinDelaiReflexion`, `dateLimiteFinancement`) and `MotifAnnulation.DESISTEMENT_ACHETEUR` replaces `DESISTEMENT_SRU`
- period defaults (10 days reflection, 45 days financing) are now overridable per deployment without code changes

Professional target:

- keep `vente` as the operational pipeline, but do not mark inventory `SOLD` at pipeline creation
- recommended inventory behavior keeps reservation/deposit in `RESERVED`
- signed contract can move the unit to `UNDER_CONTRACT`
- notarized deed or legally final sale can move the unit to `SOLD`
- handover can move the unit to `DELIVERED`
- make legal milestone names, deadlines, and rules jurisdiction-configurable and validated with legal counsel
- add structured financing workflow: own funds, mortgage, mixed financing, subsidy, employer support, installment plan
- add stage-entry exit criteria so users cannot advance without required data

### 6.4 Sale contracts

Current rules:

- contracts start in `DRAFT`
- only `DRAFT` contracts can be signed
- only one active signed contract may exist for a property
- contract signing captures a buyer snapshot and marks the property as sold
- canceling a signed contract returns the property either to `ACTIVE` or `RESERVED` depending on whether a confirmed deposit still exists
- an agent can only create contracts for themselves
- source deposits must be `CONFIRMED` and must match property, buyer, and agent

Audit:

- this is a strong contract-control model
- the contract module is more conservative than the vente module, which creates semantic tension

Professional target:

- align vente and contract semantics so inventory status changes are driven by the stricter legal milestone
- add contract versioning, amendment workflow, signature channel, and legal signer roles
- add company-buyer support with corporate legal documents and signatory authority
- add cancellation policy with legal and financial consequences, not only status rollback

### 6.5 Payment schedule and collections

Current rules:

- schedule items require positive amounts
- sequence order is incremental per contract
- only `DRAFT` schedule items are editable
- fully paid items cannot be deleted
- receivables dashboards compute outstanding, overdue, aging buckets, recent payments, and collection rate

Audit:

- the receivables foundation is useful for finance and management
- collection operations are still closer to planning than to full cash-management reality

Professional target:

- support partial payment, overpayment, rejected payment, write-off, penalty, waiver, and rescheduling
- add expected-vs-received reconciliation per payment method
- add call-for-funds generation, receipt numbering, and payment evidence
- add collection ownership and dunning stages: reminder 1, reminder 2, formal notice, legal escalation

### 6.6 Commissions

Current rules:

- rule priority is project-specific first, then societe default
- commission formula is `agreedPrice * ratePercent / 100 + fixedAmount`
- commission is computed on signed contracts

Audit:

- this is a clean and understandable MVP rulebook
- real commercial organizations usually need a richer payout model

Professional target:

- support split commissions between agent, manager, partner, or broker
- support payment-on-collection or payment-on-notarization instead of payment-on-signature only
- add clawback rules when sales are canceled or heavily re-negotiated
- add rule validity by product family, tranche, campaign, and agent seniority

## 7. Dashboards, Cockpit, Portal, And 3D

### 7.1 Executive and commercial dashboards

Current rules:

- agent scoping is enforced in dashboard services
- commercial dashboard exposes sales, deposits, trend lines, inventory status/type, active prospects, and discounts
- cockpit exposes KPI comparison, funnel, alerts, forecast, pipeline analysis, agent performance, inventory intelligence, discount analytics, and smart insights
- alert rules include conversion drop, stalled deals, cancellation spikes, and expiring legal deadlines

Audit:

- the dashboard surface is already above average for a CRM MVP
- some metrics are technically correct but commercially misleading because inventory is marked sold too early in the vente flow
- legal alert vocabulary currently contains French concepts such as `SRU`, which should not be hard-coded for a Moroccan-oriented product

Professional target:

- align dashboards with a stricter stock-status model first
- add executive metrics that matter to developers, including absorption rate by tranche
- add stock-at-risk, reservation aging, and deposit conversion by source
- add cancellation-reason analytics and discount leakage tracking
- add collection risk by project and delivery-readiness by tranche
- distinguish management KPIs from agent-operational KPIs

### 7.2 Buyer portal

Current rules:

- buyers can access portal ventes, contracts, payment data, property data, and 3D view
- portal is read-only and protected by separate auth
- access is ownership-scoped

Audit:

- the portal is a disciplined extension of the CRM, not a second product
- it still behaves like a document window more than a service portal

Professional target:

- add buyer-specific timeline: reservation, deposit, financing, signature, calls for funds, delivery milestones
- add document acknowledgment and upload checklist
- add payment promise / dispute / request-for-help workflow
- add personalization by project branding, language, and buyer role

### 7.3 3D visualizer

Current rules:

- 3D models are delivered by pre-signed URLs, not streamed through the backend
- uploads require Draco-compressed assets
- mesh-to-lot mapping connects the model to live inventory
- color updates are polled every 30 seconds without rebuilding the scene
- portal 3D access is read-only
- display mapping (fixed in P1 Wave 14, changeset applied in service code):
  `DRAFT` and `ACTIVE` as `DISPONIBLE`
  `RESERVED` as `RESERVE`
  `SOLD` as `VENDU`
  `ARCHIVED` as `LIVRE`
  `WITHDRAWN` as `RETIRE` (was incorrectly `LIVRE` before fix — withdrawn units are not delivered)

Audit:

- this is a very good architectural implementation of a 3D sales tool
- the `WITHDRAWN` → `RETIRE` fix eliminates the misleading display of off-market units as delivered
- `RETIRE` has a distinct red color (#EF4444) and label "Retiré" in the legend and KPI panel
- the main remaining business issue is semantic richness: `UNDER_CONTRACT` and `DELIVERED` are still not separate display states

Professional target:

- separate business display states for `AVAILABLE`, `RESERVED`, `UNDER_CONTRACT`, `SOLD`, `DELIVERED`, `WITHDRAWN`, and `BLOCKED`
- add filters by building, floor, tranche, orientation, typology, budget, and availability
- add reserved-by-me and sold-today overlays for sales teams
- add manager overlays for stock health, discount heatmap, and unsold value
- add portal highlight for the buyer's own lot and related annexes

## 8. Recommended Target Rulebook

The following target rulebook keeps the current architecture but makes the business layer more professional.

### 8.1 Recommended stock lifecycle

Recommended inventory statuses:

- `DRAFT`
- `MARKETING_READY`
- `AVAILABLE`
- `OPTIONED`
- `RESERVED`
- `UNDER_CONTRACT`
- `SOLD`
- `DELIVERED`
- `WITHDRAWN`
- `BLOCKED_LEGAL`
- `ARCHIVED`

Recommended ownership of status changes:

- editorial users may control `DRAFT`, `MARKETING_READY`, `AVAILABLE`, `WITHDRAWN`, `ARCHIVED`
- reservation and deposit workflows may control `OPTIONED` and `RESERVED`
- legal/contract workflow may control `UNDER_CONTRACT` and `SOLD`
- delivery workflow may control `DELIVERED`
- compliance or management workflows may control `BLOCKED_LEGAL`

### 8.2 Recommended buyer-party model

Add first-class support for:

- single buyer
- co-buyers
- married couple / household
- company buyer
- legal representative
- guarantor

Each party should carry its own KYC status, identity fields, document checklist, and signature role.

### 8.3 Recommended reservation and deposit policy

- reservation duration must be configurable
- deposit duration must be configurable
- extensions must be tracked and approved
- cancellation reasons must be mandatory
- refundability and forfeiture rules must be explicit
- finance validation must exist between deposit receipt and full confirmation

### 8.4 Recommended vente and legal policy

- `vente` is the commercial pipeline, not the final stock ownership event
- stock should not move to `SOLD` on vente creation
- legal milestones must be configurable by jurisdiction and project template
- required data per stage should be enforced by exit criteria
- cancellation analytics must separate commercial, financing, legal, stock, and customer reasons

### 8.5 Recommended collections policy

- a schedule item can be issued, partially paid, fully paid, overdue, disputed, waived, or written off
- finance should see payment proof, receipt number, bank reference, and reconciliation status
- dunning should be stage-based and auditable

### 8.6 Recommended BI and UX policy

- all KPIs must use the same business definitions across CRM, portal, exports, and 3D
- sales, stock, collection, and delivery dashboards must share one controlled metric glossary
- agent screens should optimize action
- manager screens should optimize supervision
- executive screens should optimize decision quality

## 9. Priority Roadmap

### P1 - Correct business mismatches (completed Wave 14)

- ✅ stop marking a property as `SOLD` at vente creation — property stays `RESERVED`; `SOLD` only at `ACTE_NOTARIE`; `ANNULE` releases back to `ACTIVE`
- ✅ replace hard-coded French legal deadline semantics — fields renamed to `dateFinDelaiReflexion` / `dateLimiteFinancement`; periods injected via `@Value`; `DESISTEMENT_SRU` renamed to `DESISTEMENT_ACHETEUR` (changeset 073)
- ✅ separate `WITHDRAWN` from `LIVRE` in 3D — `WITHDRAWN` now maps to `RETIRE` (#EF4444); `ARCHIVED` keeps `LIVRE`
- ✅ enforce non-user quotas — `QuotaService.enforceBienQuota/enforceContactQuota/enforceProjectQuota` wired into `PropertyService`, `ContactService`, `ProjectService`, and `ProjectGenerationService`
- ✅ mandatory reservation cancellation reason — `CancelReservationRequest.raisonAnnulation` persisted on `property_reservation.raison_annulation` (changeset 074); expiry-warning notifications added (48h window, flag-gated dedup)

### P2 - Add professional real-estate workflow depth

- add co-buyer and company-buyer support (M13: buyer type hardcoded to `PERSON`)
- add configurable reservation/deposit policies per project (M3, M18)
- add reservation extension workflow with approval tracking (M3)
- add structured deposit cancellation + refund path (M5)
- add proof-of-payment tracking on deposits (M6)
- add deposit minimum amount rule by project (M7)
- commission clawback on contract cancellation (M8)
- split commission model: agent + manager + partner/broker (M9)
- late-payment penalty model (M10)
- payment schedule total validated against `Vente.prixVente` (M12)
- tranche readiness gates before advancing: permit check, min units sold (M16)
- sales block on `ARCHIVED` projects (M17)
- per-project commercial settings: reservation validity, deposit minimum, cancellation fees (M18)
- `Property.commissionRate` wired into `CommissionService` (M19)

### P3 - Raise management and buyer experience

- align all KPI definitions after status model correction
- enrich executive cockpit: stock-at-risk, reservation aging, discount leakage, collection risk (M20, M21)
- alert thresholds configurable per societe — remove hardcoded 15%/10%/30d/5 ventes constants (H5–H11)
- budget-to-property price alignment warning on reservation creation (M21)
- KYC/financing readiness on contacts (M14)
- phone/national-ID-based duplicate detection on contacts (M15)
- `Property.estimatedValue` surfaced in analytics (M20)
- Moroccan VEFA legal timeline labels and `titre foncier` enforcement (M22, M23)
- reservation extension workflow (M3)
- turn the portal into a guided buyer-service space
- make the 3D viewer a decision tool: `UNDER_CONTRACT` display state, manager overlays, reserved-by-me

## 10. Bottom Line

The solution already implements the right product skeleton for a serious real-estate CRM. The best next move is not to add random features. It is to tighten the business semantics around inventory status, reservation/deposit policy, legal milestones, buyer-party modeling, and KPI definitions. Once those rules are aligned, the dashboards, portal, and 3D viewer become much more credible for real developer operations.

---

## 11. Financial and Commercial Controls

This section documents gaps in payment reconciliation, commission governance, and deposit lifecycle uncovered during the deep-code audit.

### 11.1 Payment reconciliation gaps

Current behavior:

- schedule items have statuses `DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `FULLY_PAID`, `OVERDUE`, `CANCELLED`
- `OVERDUE` status exists but triggers no financial consequence (no penalty, no dunning escalation)
- no overpayment protection — a payment item can theoretically receive more than its scheduled amount
- no payment schedule total validation against `Vente.prixVente` — the schedule could sum to a different amount than the agreed sale price
- payment email template is hardcoded in `CallForFundsWorkflowService.java` as a Java string (M25)

Target:

- validate payment schedule total against `Vente.prixVente` on schedule creation and item modification
- add overpayment guard: reject or flag a payment exceeding the item amount
- add penalty model: configurable late-fee rate per project, triggered after N days `OVERDUE`
- move email templates to the template store (same system used for sale contracts)
- add dunning stage: reminder 1 → reminder 2 → formal notice → legal escalation, each auditable

### 11.2 Commission governance gaps

Current behavior:

- `Property.commissionRate` field exists and is stored, but `CommissionService` ignores it entirely — it uses only `CommissionRule` table rules (M19)
- no clawback rule when a sale is cancelled after commission is accrued (M8)
- no split commission model: one agent gets the full commission; managers, partners, and brokers are not modeled (M9)

Target:

- wire `Property.commissionRate` as an override when explicitly set (overrides rule table for that unit)
- add `CommissionSplit` entity: per-vente split records with role, amount, and payout trigger
- add clawback: when `ANNULE` is recorded after commission payout, create a negative commission entry and notify manager

### 11.3 Deposit lifecycle gaps

Current behavior:

- a `CONFIRMED` deposit cannot be cancelled — funds are locked with no refund path (M5)
- no minimum deposit amount rule below the hardcoded `> 0` check (M7)
- no proof-of-payment tracking: no receipt upload, no bank reference, no finance validation step (M6)

Target:

- add `CONFIRMED → CANCELLATION_REQUESTED → REFUND_PENDING → REFUNDED` lifecycle with manager approval gate
- add per-project minimum deposit amount and payment method allowlist
- add `proof_of_payment_document_id` FK and `bank_reference` free-text field on deposit
- add finance validation step: `PENDING_BANK_CLEARANCE` before `CONFIRMED`

---

## 12. Moroccan Legal Framework Alignment

This section documents how the current implementation diverges from Moroccan real-estate law (primarily VEFA under Loi 44-00) and what alignment work is needed.

### 12.1 Current French law contamination

The codebase was originally designed with French residential sale law concepts. As of Wave 14 the most egregious references have been cleaned up:

| Before | After | Notes |
|--------|-------|-------|
| `date_fin_delai_sru` | `date_fin_delai_reflexion` | Field renamed in changeset 073 |
| `date_limite_condition_credit` | `date_limite_financement` | Field renamed in changeset 073 |
| `MotifAnnulation.DESISTEMENT_SRU` | `DESISTEMENT_ACHETEUR` | SRU is a French law concept (Art. L271-1) |
| Dashboard alert label `Art. L271-1` | Neutral label | Service-level label (M22) |

Remaining alignment work:

- `DashboardCockpitService.java` still references `Art. L271-1` in alert payload text (M22)
- The 10-day reflection period is the French SRU period; the Moroccan equivalent under Loi 44-00 VEFA may differ — consult legal counsel per project type
- The 45-day mortgage condition period is a French market default; Moroccan bancaire timelines vary significantly (typically 60–90 days for CIH, Attijari, etc.) — this must be configurable per financing institution (H4)

### 12.2 Moroccan VEFA (Loi 44-00) requirements

Key legal milestones in the Moroccan VEFA chain that the system should enforce explicitly:

| Milestone | Field / Status | Current state |
|-----------|---------------|---------------|
| Contrat de réservation | `COMPROMIS` stage entry | ✅ modeled |
| Virement de réservation (arrhes) | deposit workflow | ✅ modeled |
| Attestation de pré-commercialisation | project-level field | ❌ not enforced |
| Délai de rétractation acheteur | `dateFinDelaiReflexion` | ✅ stored (not enforced as gate) |
| Contrat de vente ADOUL / notarié | `ACTE_NOTARIE` stage | ✅ now drives SOLD (fixed Wave 14) |
| Titre foncier | `dateTitreFoncier` | ❌ field exists, no workflow enforcement (M23) |
| PV de réception | `datePvReception` | ✅ stored, not required before LIVRE |
| Mainlevée hypothèque | post-delivery | ❌ not modeled |

Priority: enforce `titre foncier` as a required document before marking a unit `DELIVERED` in the post-LIVRE workflow.

### 12.3 Default currency

Currency is hardcoded to `MAD` in `Property.java` (line 352) and `DepositService.java` (H16). This is acceptable for a single-market product but must be a per-société configuration for multi-country expansion.

---

## 13. Alert Governance and Dashboard Thresholds

All alert thresholds and intelligence parameters are currently hardcoded in `DashboardCockpitService.java`. This section documents each constant, its current value, and what it should become.

### 13.1 Hardcoded constants inventory

| Ref | Constant | Current value | Location | Recommended |
|-----|----------|---------------|----------|-------------|
| H5 | Cancellation spike CRITICAL | 15% | `DashboardCockpitService.java:227` | Per-societe setting |
| H6 | Cancellation spike WARNING | 10% | `DashboardCockpitService.java:236` | Per-societe setting |
| H7 | Conversion drop alert | 10% | `DashboardCockpitService.java:189` | Per-societe setting |
| H8 | Stalled deal window | 30 days | `DashboardCockpitService.java:201` | Configurable |
| H9 | Stalled deal CRITICAL count | 5 ventes | `DashboardCockpitService.java:207` | Configurable |
| H10 | SRU/reflection alert trigger | 3 days remaining | `DashboardCockpitService.java:264` | Jurisdiction-configurable |
| H11 | Credit/financing alert trigger | 7 days remaining | `DashboardCockpitService.java:269–278` | Configurable |
| H12 | Default vente probability | 25% | `Vente.java:104` | Configurable by stage |
| H13 | Expiring reservation window | 48 hours | `ReservationService.java:353` | Configurable |
| H14 | Absorption rate top-project threshold | 60% | `DashboardCockpitService.java:558` | Configurable |
| H15 | Discount pressure alert | 10% avg discount | `DashboardCockpitService.java:608` | Configurable |
| H16 | Default currency | MAD | `Property.java:352`, `DepositService.java:143` | Per-societe setting |
| H1 | Reservation expiry default | 7 days | `ReservationService.java:113` | Configurable per project |
| H2 | Deposit due date default | 7 days | `DepositService.java:142` | Configurable per project |
| H3 | Reflection period default | 10 days | `VenteService.java` (configurable as of Wave 14) | ✅ now `app.vente.default-reflection-period-days` |
| H4 | Financing condition default | 45 days | `VenteService.java` (configurable as of Wave 14) | ✅ now `app.vente.default-financing-period-days` |

### 13.2 Recommended governance model

The cleanest production model stores all thresholds in the `societe` table extended with a `dashboard_config JSONB` column, defaulting to the current values if null. This allows each company to tune its own alert sensitivity without code changes or redeployment.

Short-term (P3): move H5–H11, H14, H15 to `@Value("${app.dashboard.*:default}")` Spring Boot properties, readable per environment.

Long-term (roadmap): store per-societe dashboard config in the `societe` table and expose a management UI for threshold tuning.

---

## 14. Implementation Log (P1 Wave 14)

This section tracks the status of all P1 fixes implemented in the Wave 14 business-rules hardening sprint.

### 14.1 Fix status

| Fix | Description | Status | Key files | Changeset |
|-----|-------------|--------|-----------|-----------|
| B1 | Property stays `RESERVED` at vente creation (was `SOLD`) | ✅ Done | `VenteService.java:create()` | — |
| B2 | `ANNULE` releases property back to `ACTIVE`/`RESERVED` | ✅ Done | `VenteService.java:updateStatut()` | — |
| B3 | `WITHDRAWN` maps to `RETIRE` in 3D viewer (was `LIVRE`) | ✅ Done | `Project3dService.java:236`, `lot-3d-status.model.ts` | — |
| H3/H4 | Reflection + financing periods are `@Value`-injectable | ✅ Done | `VenteService.java` `@Value` fields | — |
| F2-rename | `date_fin_delai_sru` → `date_fin_delai_reflexion` | ✅ Done | `Vente.java`, `VenteResponse.java` | 073 |
| F2-rename | `date_limite_condition_credit` → `date_limite_financement` | ✅ Done | `Vente.java`, `UpdateFinancingRequest.java` | 073 |
| F2-enum | `DESISTEMENT_SRU` → `DESISTEMENT_ACHETEUR` | ✅ Done | `MotifAnnulation.java`, frontend TS types | — |
| M1 | `enforceBienQuota`, `enforceContactQuota`, `enforceProjectQuota` wired | ✅ Done | `QuotaService.java`, `PropertyService`, `ContactService`, `ProjectService`, `ProjectGenerationService` | — |
| M2 | Reservation cancellation reason persisted | ✅ Done | `Reservation.java`, `CancelReservationRequest.java`, `ReservationService.cancel()` | 074 |
| M4 | Expiry-warning notification (48h window, `RESERVATION_EXPIRING_SOON`) | ✅ Done | `ReservationService.runExpirySoonCheck()`, `NotificationType`, `ReservationRepository` | 074 |

### 14.2 Items not yet implemented (documented in §9 P2/P3)

- M3 Reservation extension workflow
- M5 Deposit cancellation + refund path
- M6 Proof-of-payment tracking
- M7 Deposit minimum amount by project
- M8 Commission clawback on cancellation
- M9 Split commission model
- M10 Late-payment penalty model
- M12 Payment schedule total validation
- M13 Company buyer support
- M14 KYC/financing readiness on contacts
- M15 Phone/national-ID duplicate detection
- M16 Tranche readiness gates
- M17 Sales block on ARCHIVED projects
- M18 Per-project commercial settings
- M19 `Property.commissionRate` wired into CommissionService
- M20 `Property.estimatedValue` in analytics
- M21 Budget-to-property alignment warning
- M22 Moroccan legal framework labels (DashboardCockpitService alerts)
- M23 `dateTitreFoncier` workflow enforcement
- M24 `agreedPrice ≤ listPrice` validation hardening
- M25 Payment email template externalization
- H5–H11, H14, H15 Configurable dashboard thresholds
- H16 Per-societe currency setting
