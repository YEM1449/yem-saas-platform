# Deep Audit — Wave 14 (Principal-Level Cross-Functional)

**Mode:** AUDIT ONLY. No code changes. Findings + reproduction only. Fixes deferred to Wave 15.
**Date started:** 2026-06-14
**Premise:** surface issues assumed resolved (audit-report-2026-06-13). This hunts second-order
and systemic risk — concurrency, scale, cross-tenant, time, and legal-evidence failures.

Finding IDs are `DA-NNN` (sequential). Severity ∈ {🔴 Critical, 🟠 High, 🟡 Medium, ⚪ Low}.
Each finding records: what, evidence (file:line), repro, impact, and a "verified safe" note where
the obvious failure mode is actually already handled.

---

## TRACK 1 · Concurrency & Transaction Integrity (Rachid)

### ✅ Verified safe (no finding)

- **T1.2 — OPTION expiry vs manual confirmation race is SAFE.** `Vente` carries `@Version`
  (`vente/domain/Vente.java:45`). If the hourly `expireOverdueOptions()` sweep loads an OPTION at
  the same instant an agent `confirmReservation()`s it, the second writer hits an
  `OptimisticLockException` and loses. A confirmed reservation cannot be silently cancelled by a
  stale scheduled task. (See DA-002 for the *batch-abort* side effect, which is the only residual.)
- **T1.1 invariant integrity is SAFE.** The "one active vente per property" rule is backed by a DB
  partial unique index `uk_vente_active_property` (changeset `075-vente-active-unique.yaml`),
  not only by the application check at `VenteService.java:220`. Data cannot be corrupted. (See
  DA-001 for the error-contract gap.)
- **T1.5 — Contact↔Vente divergence is SAFE.** Contact status is advanced *synchronously inside the
  same transaction* (`VenteService.java:239-240, 342-343`), not via `AFTER_COMMIT`. It commits
  atomically with the vente. The `AFTER_COMMIT` listeners that do exist (`KpiComputationService`)
  use `REQUIRES_NEW` and only recompute a recomputable snapshot; failure → stale KPI, not divergence.

### DA-001 🟡 Medium — Concurrent vente creation surfaces as HTTP 500, not 409

**Where:** `VenteService.create()` / `createOption()` (`vente/service/VenteService.java:220, 283`);
global handler `common/error/GlobalExceptionHandler.java` (no `DataIntegrityViolationException` mapping).

The application guard `existsBySocieteIdAndPropertyIdAndStatutNot(...)` is a SELECT, and the INSERT
follows in the same transaction. Two simultaneous requests both pass the SELECT; the DB unique index
(changeset 075) correctly rejects the second INSERT with `DataIntegrityViolationException`. **But that
exception is not handled** — `GlobalExceptionHandler` maps `jakarta.validation.ConstraintViolationException`
(line 146), not Spring's `org.springframework.dao.DataIntegrityViolationException`. So the loser of the
race receives a generic **500** (with whatever stack-trace exposure the prod config allows — see T2.6)
instead of the intended **409 `PROPERTY_ALREADY_ENGAGED`** with the actionable message "bien déjà réservé".

Contrast: `SaleContractService.java:155` and `DepositService.java:189` *do* catch
`DataIntegrityViolationException` and translate it. `VenteService` does not — it relies on the now-bypassed
application check for the friendly error.

**Repro:** fire two `POST /api/ventes` for the same `propertyId` concurrently → one 201, one 500.
**Impact:** broken error contract under concurrency; misleading 500 to the agent. (No info leak — the
catch-all handler returns a generic message, see T2.6.)
**Fix effort:** S (catch + translate, or add a global handler).

### DA-002 ⚪ Low — VEFA sweep is all-or-nothing per batch

**Where:** `VenteService.expireOverdueOptions()` / `closeExpiredRetractations()` (`VenteService.java:384-410`).

Both sweep methods iterate all overdue rows and `save()` each inside one `@Transactional`. An
`OptimisticLockException` on a single contended row (the safe outcome of DA-002's parent T1.2) aborts the
**entire** transaction, so *no* options expire that run. `VenteVefaScheduler.runVefaSweep()` catches the
exception and logs it; the next hourly run retries. Self-healing but means legal deadlines can slip up to
an hour under contention, and a single permanently-poisoned row would stall the whole sweep indefinitely.

**Impact:** delayed legal-deadline processing; potential permanent stall on a bad row.
**Fix effort:** S (per-row try/catch or `REQUIRES_NEW` per row).

### DA-003 🟠 High — Cumulative échéance cap (Art. 618-17, 100%) is not concurrency-safe — ✅ RESOLVED

> **Resolved 2026-06-14.** Both write paths now load the parent vente with a pessimistic write lock via the
> new `VenteRepository.findBySocieteIdAndIdForUpdate(...)` (`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR
> UPDATE`), exposed through the `requireVenteForUpdate(...)` helper. Concurrent `addEcheance` /
> `generateEcheancierLegal` calls serialize on the vente row, so the cumulative-cap check and the
> legal-échéancier idempotency guard are atomic — the second caller blocks, then re-reads the committed sum
> and is correctly rejected (no >100% schedule, no duplicate full schedule). Lock-ordering: these methods take
> only the vente write lock (property is read unlocked), so no new deadlock path. Covered by `VenteServiceTest`
> (cap + idempotency tests now assert the locking finder is used and the unlocked one never is). Full unit
> suite green (215). Original analysis below for the record. _(Rounding remainder is the separate DA-015.)_


**Where:** `VenteService.assertCumulWithinPrice()` (`VenteService.java:707-716`), `addEcheance()`
(`:639`), `generateEcheancierLegal()` (`:668-705`). `VenteEcheance` has **no `@Version`**
(`vente/domain/VenteEcheance.java`), the parent `Vente` is loaded without a pessimistic lock, and there is
**no DB constraint** enforcing `SUM(montant) ≤ prix`.

`assertCumulWithinPrice` does `SELECT SUM(montant)` then the INSERT follows. At READ_COMMITTED (the
default), two concurrent `addEcheance` calls both read the old sum `S`, both pass `S+mᵢ ≤ prix`, and both
commit → `S+m₁+m₂` can exceed the agreed price, **breaching the legal 100% cap (Art. 618-17 Loi 44-00).**

Worse: `generateEcheancierLegal` guards idempotency with `existsByVente_IdAndEtapeIsNotNull` — also a
check-then-insert with no lock. Two concurrent calls both pass the `exists` check and each create a full
7-step 100% schedule → **200% of price** in échéances.

**Repro:** two concurrent `POST /api/ventes/{id}/echeances` (or two concurrent
`generateEcheancierLegal`) → cumulative montant > prix_vente.
**Impact:** legally invalid VEFA échéancier; over-billing the buyer; financial reconciliation breaks (T4).
**Fix effort:** M (pessimistic lock on Vente in these methods, or a DB CHECK/trigger, or SERIALIZABLE).

### DA-004 🟡 Medium — Payment recording has no idempotency (double-submit double-records)

**Where:** `CallForFundsWorkflowService.addPayment()` (`payments/service/CallForFundsWorkflowService.java:164-200`).

`addPayment` stores `paymentReference` but never checks it for uniqueness, and there is no dedup token.
An agent double-clicking "enregistrer paiement" (or a retried request) inserts two `SchedulePayment`
rows. Because `totalPaid` is a derived `SUM` (good — no stored-field drift), it simply inflates and may
flip the item to PAID on phantom money. Also note **overpayment is not capped**: the only guards are
`amount > 0` and `status != PAID/CANCELED`; a single payment (or concurrent payments) exceeding the
remaining due is accepted (cross-ref Track 4 / DA-T4).

**Repro:** POST the same payment twice → two rows, `totalPaid` doubled.
**Impact:** inflated cash records; false PAID status; reconciliation drift; audit confusion.
**Fix effort:** S–M (unique idempotency key, or unique `(societeId, scheduleItemId, paymentReference)`).

### DA-005 🟠 High — External I/O inside a DB transaction + no HTTP timeout → Neon pool exhaustion

**Where:** `PortalAuthService` is class-level `@Transactional` (`portal/service/PortalAuthService.java:39`);
`requestLink()` saves the token (`:117`) then calls `emailSender.send(...)` (`:147`) — a network call —
**still inside the transaction**, holding a pooled DB connection for the duration of the mail round-trip.
Compounding it, `BrevoHttpEmailSender` builds its `RestClient` with **no connect/read timeout**
(`outbox/service/provider/BrevoHttpEmailSender.java:55-60` — no `requestFactory`/timeout set).

On Neon serverless (low connection ceiling, cold starts) a slow or hung Brevo endpoint pins a DB
connection per in-flight request indefinitely. A mail-provider incident becomes a **full app outage** via
connection-pool exhaustion, not a degraded-email situation. The `try/catch` at `:148` only catches a
*thrown* `RuntimeException` — it does nothing for a *hung* call.

Note: the contract-PDF path is the *good* pattern — the controller does R2 `store()` then calls the DB
service afterward (`VenteController.java` generateContract), so R2 I/O is outside the DB tx.

**Repro:** point Brevo at an unresponsive host; issue magic-link requests → DB connections pile up until the pool is exhausted; unrelated requests then fail.
**Impact:** mail-provider outage → app outage; latency amplification.
**Fix effort:** M (move send to outbox/`AFTER_COMMIT`, and set RestClient connect+read timeouts).

---

## TRACK 2 · Adversarial Security / Tenant Isolation (Imane)

### ✅ Verified safe (no finding)

- **T2.2 — `societeId`/`tid` cannot be forged or overridden.** `JwtAuthenticationFilter` derives
  `societeId` solely from `jwtProvider.extractSocieteId(token)` *after* `jwtProvider.isValid(token)`
  (signature check). No header, query param, or body is ever consulted
  (`auth/security/JwtAuthenticationFilter.java:68,78,172-178`). ThreadLocal cleared in `finally`.
- **T2.4 — `statut` is not mass-assignable.** `CreateVenteRequest` has no `statut`/`societeId` field
  (`vente/api/dto/CreateVenteRequest.java`); the service sets the initial state. An agent cannot POST a
  `LIVRE_DEFINITIF` vente to skip the pipeline. (`agentId`/`prixVente`/`reduction` are settable by design.)
- **T2.6 — No sensitive leakage.** Catch-all `Exception` handler returns a static
  "An unexpected error occurred" with no `ex.getMessage()` (`GlobalExceptionHandler.java:1066-1082`);
  Spring's default stacktrace/message exposure is off (no `server.error.include-*` overrides). No raw
  tokens/passwords/CINs logged (magic-link `rawToken` is never logged).
- **T2.7 — Auth throttling and token strength are adequate.** Login is rate-limited by IP+identity
  (`LoginRateLimiter`), portal magic-link by `checkPortalLink`, invitations by `checkInvitation`.
  Magic-link tokens: 32-byte `SecureRandom` → only the SHA-256 hash stored, 48h TTL, single-use.
- **IDOR — the dominant pattern is safe.** Domain reads/writes scope by `findBySocieteIdAndId` /
  `requireSocieteId()` (e.g. document download `DocumentService.requireDocument`; `Project3dService`
  filters `societeId.equals(p.getSocieteId())`; portal vente via `requireOwnedVente` checks contactId).

### DA-006 🔴 Critical — Portal cross-contact document download (broken access control) — ✅ RESOLVED

> **Resolved 2026-06-14.** `getDocumentKey` now looks the document up scoped to `(societeId, venteId, docId)`
> via the new `VenteDocumentRepository.findBySocieteIdAndVente_IdAndId(...)`, so a document is only reachable
> through the vente it is attached to; a foreign `docId` yields 404. The misleading "Enforces cross-contact
> isolation" doc-comment was corrected to describe the actual two-level enforcement. Covered by
> `PortalVenteServiceTest` (3 tests: owned-doc happy path, foreign-doc → 404, vente-owned-by-another-contact
> → 404, asserting the société-only fallback is never used). Original analysis below for the record.


**Where:** `PortalVenteService.getDocumentKey()` (`portal/service/PortalVenteService.java:67-73`), exposed at
`GET /api/portal/ventes/{id}/documents/{docId}/download` (`portal/api/PortalVenteController.java:58-68`).

```java
public String getDocumentKey(UUID venteId, UUID docId) {
    requireOwnedVente(venteId);                 // verifies *venteId* belongs to caller — OK
    UUID societeId = requireSocieteId();
    return documentRepository.findBySocieteIdAndId(societeId, docId)  // docId scoped to SOCIÉTÉ ONLY
            .map(Document::getStorageKey) ...    // never bound back to venteId / contact
}
```

The doc-comment claims *"Enforces cross-contact isolation"* — **it does not.** The `docId` is fetched by
`(societeId, docId)` and is never verified to belong to the owned `venteId` (or to the calling contact).
An authenticated portal buyer supplies **their own** `venteId` (passes the ownership gate) plus **any**
document UUID in the whole société, and the controller streams the file back.

**Attack (Imane):** log in as buyer A via magic link → call the download endpoint with A's venteId and
enumerated `docId` values → exfiltrate every document in the société: other buyers' contracts, CIN scans,
financing dossiers, PV de livraison. Cross-tenant is blocked (società scope holds), but **cross-contact
within a société is wide open** — the exact thing the portal must prevent.

**Impact:** mass PII / contract exfiltration by any portal user → **Loi 09-08 personal-data breach** and
**Loi 44-00 contract-confidentiality breach**. CNDP-reportable.
**Fix effort:** S (resolve the document via the vente's own documents, or assert
`doc.entityType==VENTE && doc.entityId==venteId`).

### DA-007 🟡 Medium — Pre-signed GLB upload: no size cap + shallow validation (stored client-DoS)

**Where:** `ObjectStorageMediaStorage.generatePresignedPutUrl()` (`media/service/...:241`); validation
`GlbValidator.validate()` (header magic + version 2 + `KHR_draco_mesh_compression` presence only).

The pre-signed PUT signs `bucket+key+contentType` but **no `Content-Length` range**, so R2 does not
enforce any size limit — the 50 MB cap is client-side only (Angular). A portal/agent user can upload an
arbitrarily large object to their key (storage-cost / DoS). The `fileKey` itself *is* server-generated and
scoped (`models/{societeId}/{projetId}/{uuid}.glb`) so arbitrary-object overwrite is **not** possible (good).

Separately, `GlbValidator` only checks the container header and that the Draco extension is *named* — it
does not validate structural integrity. A crafted GLB can pass validation yet be malformed/oversized and
**crash the Three.js client of every user (including portal buyers) who opens that project** — a stored,
broadcast client-side DoS from a single upload.

**Impact:** storage abuse; one bad upload bricks a project's 3D view for all viewers.
**Fix effort:** M (sign a content-length range / enforce size on confirm; deepen GLB validation).

### DA-008 ⚪ Low — Agent assignment accepts a cross-société userId (no membership check)

**Where:** `VenteService.create()` (`:192`), `SaleContractService.java:98`, `CommissionService.java:124` —
all do `userRepository.findById(agentId)` on a **request-supplied** `agentId` without verifying the user is
a member (`AppUserSociete`) of the current société.

An agent in société A who knows a user UUID from société B can set them as the `agent` on a vente/contract
in A. Effect: a foreign user is referenced on A's records and their `displayName` leaks into A's UI.
Low impact (needs a valid foreign UUID; no data mutation in B) but it's a soft tenant boundary.

**Fix effort:** S (validate `appUserSocieteRepository.findByIdUserIdAndIdSocieteId(agentId, societeId)`).

### ℹ️ Informational

- **T2.3 — 3D `prix` exposure is acceptable.** `Lot3dStatusDto.prix` comes from `Property.getPrice()`
  (catalogue list price, `Project3dService.java:151`), not the negotiated `Vente.prixVente`. Portal access
  is gated by `portalUserHasAccess(projetId, contactId)` → 404 otherwise
  (`PortalProject3dController.java:41,51`). No other buyer's negotiated price is exposed.

---

## TRACK 3 · Legal & Regulatory Exposure (Nadia)

### ✅ Verified safe / present

- **GDPR / Loi 09-08 machinery exists.** Consent fields on `Contact` (`consent_given`, `consent_date`,
  `consent_method`, `processing_basis`, `data_retention_days`, `anonymized_at`), a `gdpr/` module with
  `DataExportBuilder`, `AnonymizationService`, `DataRetentionScheduler`, `ProcessingRegisterService`, plus
  `docs/legal/cndp-declaration.md` and `data-retention.md`. Export/erasure paths for a data subject exist.
- **5% deposit cap (Art. 618-4) — single enforcement point, no bypass.** `setMontantDepot` is only ever
  called inside `confirmReservation` after the cap check (`VenteService.java:321-331`). No other write path
  sets the VEFA deposit. (Edge case in DA-012b note.)

### DA-009 🟠 High — Generated VEFA reservation contract omits mandatory Loi 44-00 mentions

**Where:** `resources/templates/documents/contrat-reservation-vefa.html`.

The rendered *contrat préliminaire de réservation* contains: parties (société name; buyer name + CIN),
bien (référence, projet, prix TTC, dépôt), the rétractation/5% legal note, and signatures. Measured against
Art. 618-3 Loi 44-00, it is **missing several mandatory elements**:

1. **Délai/date de livraison prévue** — absent. A VEFA preliminary contract without the delivery date is
   challengeable. (The data exists — `Vente.dateLivraisonPrevue` — it just isn't in the template.)
2. **Garantie d'achèvement / de remboursement (GFA)** — the statutory guarantee mechanism is not mentioned.
3. **Description précise du bien** — only "référence/projet"; no surface, consistance, situation dans
   l'immeuble, lot/étage, plans annexés.
4. **Identité complète des parties** — buyer address/état civil and vendor legal identity (ICE, RC, capital,
   siège) absent.
5. **Prix HT + TVA décomposé** — only TTC shown; the HT/TVA breakdown that the buyer is entitled to is not
   rendered.
6. **Échéancier des paiements** annexed to the preliminary contract.

`docs/legal/pdf-review-checklist.md` (B-003) enumerates 27 such items — but the **template does not render
them**, so the checklist is aspirational, not enforced. Any contract produced today carries voidability risk.

**Impact:** generated preliminary contracts may be challenged/voided; promoteur liability.
**Fix effort:** M (extend template + bind the already-present fields).

### DA-010 🟠 High — Loi 09-08: CIN / passport / financial data stored in plaintext; no portal privacy notice

**Where:** `contact/domain/Contact.java:103-123` — `national_id` (CIN), `passeport_numero`, `date_naissance`,
`lieu_naissance`, `situation_matrimoniale`, `nationalite`, `apport_personnel` are plain columns. No
`@Convert`/`AttributeConverter`, no field-level encryption anywhere in the contact domain.

Under Loi 09-08 these are regulated personal data (CIN is a national identifier; `apport_personnel` is
financial). They are persisted in clear to Neon (a serverless Postgres — also raising a **cross-border
transfer** question for CNDP depending on region). Neon's storage-level encryption-at-rest provides baseline
protection, but there is no application-level protection for the most sensitive identifiers, so a leaked DB
snapshot, a logging accident, or a read-IDOR (cf. DA-006) exposes them directly.

Also: no evidence of a **privacy notice / information de la personne concernée** surfaced to the acquéreur in
the portal (consent is recorded as staff-entered fields, not captured from the data subject with a notice).

**Impact:** CNDP non-compliance; high-blast-radius exposure of national IDs + financials.
**Fix effort:** M (field-level encryption for CIN/passport/financials; portal privacy notice + consent capture).

### DA-011 🟠 High — Rétractation right (Art. 618-3) is defeatable: no temporal guard on leaving cooling-off

**Where:** `VenteService.validateTransition()` (`vente/service/VenteService.java:954-961`). The allowed set
for `EN_RETRACTATION` is `{ACOMPTE, ANNULE}` — and **nothing checks that `dateFinDelaiReflexion` has
passed** before permitting `EN_RETRACTATION → ACOMPTE`.

So an agent can advance a buyer from the cooling-off state to ACOMPTE (collecting the down-payment) **during
the protected 7-day window**, before the legal reflection period expires. The buyer's
`exerciseRetractation()` guard protects a buyer-*initiated* withdrawal, but the system does nothing to stop
the promoteur from financially entangling the deal mid-window — i.e. the software actively enables
undermining the statutory protection.

**Repro:** `confirmReservation` → immediately `PATCH /{id}/statut` to ACOMPTE on day 1 → accepted.
**Impact:** facilitates a Loi 44-00 violation; payments taken during a period when the buyer can still walk.
**Fix effort:** S (block `EN_RETRACTATION→ACOMPTE` until `today ≥ dateFinDelaiReflexion`, or require explicit
documented waiver).

### DA-012 🟡 Medium — TVA rate is user-settable without legal validation; thresholds need CGI re-verification

**Where:** `PropertyService.java:166-171` — `taux = req.tvaTaux(); if (taux == null) taux = suggestTaux(...)`.
When the client **provides** `tvaTaux`, it is stored verbatim with **no check against the legal
classification**. A user can set `tvaTaux = 0` (or 0.10) on a unit that is legally 20%, understating VAT.

`TvaCalculator` (`legal/TvaCalculator.java:17-24`) hardcodes: social = surface ≤ 100 m² **and** prix ≤
250 000 MAD → 0%; moyen = ≤ 150 m² **and** ≤ 700 000 MAD → 10%; else 20%. **⚠ REQUIRES CURRENT CGI
VERIFICATION** — the statutory *logement social* definition (Art. 92 CGI) historically uses different
surface/price bounds and the 0% vs exoneration-with-state-payment treatment is nuanced. Do not assume these
constants are currently legal.

Because these values can feed an accountant's CGI declaration, a wrong or manipulated `tvaTaux` produces an
**incorrect tax return**.

**Impact:** incorrect tax filings; deliberate VAT under-declaration possible.
**Fix effort:** S–M (validate provided `tvaTaux` against `suggestTaux` / lock it; re-verify thresholds with counsel).

### DA-013 🟠 High — Core VEFA pipeline actions are not audited; audit log is not tamper-evident

**Where:** `VenteService` records **no** `CommercialAuditService` events for `confirmReservation` (deposit
taken), `exerciseRetractation`, `updateStatut` (every pipeline transition incl. ACTE/LIVRE), `recordDelivery`,
`addEcheance`, price changes, or `generateContract` (legal document produced). Audit calls exist only in
`RemboursementService`, `SaleContractService`, `ContactService`, `ReservationService`, `DepositService`,
`AnonymizationService` — the *new VEFA pipeline is a blind spot*.

In a dispute the promoteur must show who advanced the sale, when the deposit was taken, who generated which
document. `updated_at` is not an audit trail. Additionally, `CommercialAuditEvent` rows have **no
append-only enforcement** (no revoked UPDATE/DELETE, no hash-chain / sequence integrity), so the log is not
tamper-evident as legal evidence.

**Impact:** no defensible history of legally significant actions; weak evidentiary value.
**Fix effort:** M (emit audit events on pipeline transitions + document generation; harden the audit table).

### DA-014 ⚪ Low — MRE / Office des Changes fund-origin obligations unmodeled

**Where:** `Contact` captures `nationalite` / `pays_residence` and `TypeAcquereur` distinguishes buyer types,
but `dossier_financement` and the payment model carry **no currency of funds, no proof-of-foreign-transfer,
no Office des Changes reference**. An MRE sale (foreign-currency transfer constraints, repatriation rights)
is processed identically to a resident sale; the data needed to evidence compliant fund origin is absent.

**Impact:** cannot demonstrate Office des Changes compliance for non-resident buyers. Overlaps T5 (DA-domain).
**Fix effort:** M.

---

## TRACK 4 · Financial Correctness & Audit Trail (Omar)

### ✅ Verified safe

- **T4.1 — money is `BigDecimal`, never `double`/`float`.** The only `Double` usages are dashboard
  *analytics averages* (`avgPricePerSqm`, `avgDaysToClose`) — not stored or transactional money.
- **T4.2 — legal percentages sum to 100%.** `EcheancierLegal.MA` = 5+10+15+20+20+20+10 = 100
  (`legal/EcheancierLegal.java:18-25`). (Rounding of the *amounts* is the issue — DA-015.)
- **T4.3 — `montant_paye` is derived, not stored.** Totals come from `SUM(amountPaid)`
  (`SchedulePaymentRepository.sumPaidForItem`) → no stored-field drift. (Overpayment cap is the gap — DA-017.)
- **T4.6 — treasury forecast excludes cancelled rows.** `sumDueAll`/`sumPaidAll`/overdue all filter
  `statut NOT IN (PAYEE, ANNULEE)` (`VenteEcheanceRepository.java:103-137`); annulled ventes cancel their
  pending échéances to `ANNULEE` (A-001). Minor residuals only (below).

### DA-015 🟡 Medium — Échéancier amounts rounded independently with no remainder reconciliation

**Where:** `VenteService.generateEcheancierLegal()` (`:686-697`):
`montant = prix.multiply(pct).divide(100, 2, HALF_UP)` for each of 7 stages, each rounded independently and
**no final stage absorbs the rounding remainder**. The sum of the 7 rounded amounts can differ from
`prix` by a centime or two, so the legally-mandated "100% of price" schedule can total 99.99% or 100.01%.
Combined with DA-003's non-atomic cap check, the échéancier is not provably equal to the price.

**Impact:** échéancier doesn't reconcile to the contract price to the centime; over/under-billing by rounding.
**Fix effort:** S (compute n−1 stages, last = `prix − sum(previous)`).

### DA-016 ⚪ Low — Currency is implicit on transactional money (EUR-expansion time bomb)

**Where:** `PropertyCreateRequest` accepts a 3-char `currency` (defaulting to `"MAD"`), but
`Vente.prixVente`, `VenteEcheance.montant`, `SchedulePayment.amountPaid` and the dashboards carry **bare
`BigDecimal` with no currency**; `"MAD"` is hardcoded in formatting/penalty
(`DashboardCockpitService.java:724`, `MarketConfig` penalty). The documented France/EUR expansion would
require threading currency through every money-bearing entity and every formatter.

**Impact:** no per-amount currency; cross-currency reporting impossible; costly future migration.
**Fix effort:** M–L (model currency as a first-class attribute).

### DA-017 🟡 Medium — Overpayment is not capped on call-for-funds payments

**Where:** `CallForFundsWorkflowService.addPayment()` (`payments/service/...:176-197`). Guards are only
`amount > 0` and `status != PAID/CANCELED`. There is **no check that `amount ≤ remaining`**
(`item.getAmount() − totalPaid`). A single payment larger than the outstanding due, or concurrent payments
(cf. DA-004), pushes `totalPaid` above `montant appelé`. The item is then marked PAID on inflated money.

**Impact:** cash records exceed amounts due; reconciliation against bank statements breaks; refund mess.
**Fix effort:** S (reject `totalPaid + amount > item.getAmount()`, or clamp).

### DA-018 ⚪ Low — No post-creation price-mutation path (RG-B07 "lock" = "never editable"); recompute machinery absent

**Where:** `Vente.prixVente` is set only at creation (`VenteService.java:245, 301`). There is no
price-update endpoint/service method. So the "price locked after ACOMPTE" rule is effectively "price never
changes," and **legitimate pre-ACOMPTE renegotiation has no path** (defer to T5/DA-domain). Should a price
edit ever be added, there is currently no machinery to recompute the already-generated échéances/appels, so
they would silently go stale.

**Impact:** real renegotiations can't be represented; latent stale-children risk if an edit is added later.
**Fix effort:** M (model a guarded price-change that recomputes financial children pre-lock).

### ℹ️ Treasury residuals (low)

- `sumPaidAll` (encaissé) still counts a `PAYEE` échéance on a later-annulled vente (PAYEE rows are not
  cancelled, by design); the matching refund obligation lives in `RemboursementService` but is **not
  subtracted from "encaissé"**, so net cash is slightly overstated when paid sales are cancelled.
- VEFA échéances have no "appel émis vs échéance future contractuelle" distinction — `aEncaisser` is the full
  remaining contractual amount, not only issued/called appels. Acceptable as a *total receivable* but not as
  *imminent cash*.

---

## TRACK 5 · Domain Completeness — states the system can't represent (Salma)

### ✅ Present

- Late-**delivery** penalty (promoteur late to buyer) IS modeled — `joursRetard` + `penaliteAccumulee`
  from `dateLivraisonPrevue` (B-001, `VenteService.java:981-989`). The *promoteur-side* delay has consequence.
- A `groupe/` module (group client/dashboard) gives partial multi-entity reporting.

### DA-019 🟡 Medium — No "défaut de paiement" state for a buyer who stops paying appels

`VenteStatut` has no defaulting/impayé working state and `MotifAnnulation` (`CREDIT_REFUSE`,
`DESISTEMENT_ACHETEUR`, `CSP_NON_REALISEE`, `ACCORD_PARTIES`, `LITIGE`, `AUTRE`) are *cancellation reasons*,
not a live workflow. A buyer who stops paying an appel de fonds: the échéance shows as overdue on the
dashboard, but the **vente has no representable "en défaut" state, no mise-en-demeure / relance workflow, no
penalty-interest accrual, and no governed path to forced résiliation + resale.** This is one of the most
common real VEFA situations and the software cannot run it.

**Fix effort:** M.

### DA-020 🟡 Medium — No transfert / cession de contrat (reservation reassignment pre-livraison)

A VEFA buyer reselling their reservation to a new buyer before delivery (very common in the Moroccan market)
has **no path**: a vente binds one `contact` set at creation with no substitution/cession mechanism. The
only way to model it today is cancel + recreate, which destroys continuity (refs, deposit, échéancier,
history). **Fix effort:** M.

### DA-021 ⚪ Low — No modification de vente / avenant (add parking, cellier, annexe mid-contract)

A `Vente` references exactly one `propertyId`; there are no line items or avenants. Adding a parking or
storage lot to an existing sale (routine) cannot be represented — it forces a second separate vente.
**Fix effort:** M.

### DA-022 🟡 Medium — Document lifecycle has no versioning / supersession / signed state

`VenteDocument` (`vente/domain/VenteDocument.java`) stores `nom_fichier`, `storage_key`, `document_type`,
`created_at` — **no version, no supersession link, no signed/unsigned/superseded state.** Real deals reissue
documents after corrections; here a regenerated contract becomes an ambiguous second row and nothing records
which is the current legal version or whether it was signed. Combined with DA-013 (no generation audit),
the system cannot prove the authoritative document in a dispute. **Fix effort:** M.

### DA-023 ⚪ Low — Cross-société person identity is not unified

`Contact` is per-société; a person who exists in two sociétés (the group-owner reality) is two unlinked
rows. There is no first-class person/holding entity tying them. The group dashboard aggregates sociétés but
not *people across them*. **Fix effort:** L.

### DA-024 ⚪ Low (roadmap) — Analytics blind spots from missing pipeline-transition history

Because transitions aren't event-logged (DA-013), these routine promoteur questions have **no answer**:
(1) average days OPTION→ACTE *by agent*; (2) cancellation rate *by project, over time, by motif*;
(3) which orientation/typology sells fastest (needs time-to-sell); (4) reservation-cohort progression;
(5) total outstanding exposure from defaulting buyers (needs DA-019). A pipeline-transition event table
would unlock all five. **Fix effort:** M.

---

## TRACK 6 · Resilience, Failure Modes, Observability (Yassine)

### ✅ Verified safe / corrected hypothesis

- **Scheduled legal deadlines do NOT vanish on restart.** The VEFA sweeps are cron-triggered **DB-query**
  scans (`findByStatutAndOptionExpireAtBefore(now())`, `...DateFinDelaiReflexionBefore(today)`), not
  in-memory per-deadline timers. A run missed during downtime is backfilled at the next hourly tick because
  the query re-scans by date. (The original T6.1 hypothesis does not apply here.) The residual is DA-025.
- Actuator exposes `health, info, metrics, prometheus, loggers`; OTLP/Prometheus metrics wired (Wave 4).

### DA-025 🟡 Medium — Most schedulers lack ShedLock → multi-instance double execution

Only `OutboundDispatcherScheduler` and `NotificationDigestScheduler` carry `@SchedulerLock`. **No lock** on
`VenteVefaScheduler`, `ReservationExpiryScheduler`, `DataRetentionScheduler`, `DepositWorkflowScheduler`,
`PortalTokenCleanupScheduler`, `ReminderScheduler` (×2). On more than one instance (Render scaling or a
rolling deploy's overlap window) each fires on **every** instance simultaneously: concurrent VEFA sweeps
amplify the optimistic-lock batch-abort (DA-002) and double-send notifications; concurrent
`DataRetentionScheduler` runs duplicate deletion passes. **Fix effort:** S (`@SchedulerLock` on each; the
ShedLock table already exists — changeset 052).

### DA-026 🟡 Medium — No error tracking / alerting; failures are invisible until a user calls

There is no Sentry-equivalent error aggregation or alerting (none in config or code). Metrics exist but no
evidence of alert rules. Combined with the codebase's many **catch-log-swallow** sites
(`VenteVefaScheduler` swallows sweep errors, `notifyAgent` swallows, `PortalAuthService` swallows email
failures, KPI `AFTER_COMMIT` failures are silent), a sale or scheduled legal action that fails in production
produces no proactive signal — the first indication is an angry phone call. **Fix effort:** M (error
tracking + alerts on 5xx rate, swallowed-exception counters, scheduler failure).

### DA-027 ⚪ Low–Med — Startup-coupled Liquibase on Render can wedge deploys

Liquibase runs on app startup. Postgres makes each changeSet transactional (so a single failure rolls back
that changeSet — good), but if the Render process is **killed mid-migration** (deploy timeout / OOM), the
`DATABASECHANGELOGLOCK` row stays held and **every subsequent deploy hangs** waiting for the lock until it is
cleared manually. Migrations are not run out-of-band from the app boot, so a bad migration also means the
service is fully down (not degraded) during recovery. **Fix effort:** M (separate migration job; lock-timeout
runbook).

### DA-028 ⚪ Low (verify) — Recovery path for data corruption is unproven from the repo

Neon point-in-time recovery and R2 object versioning are infra settings not evidenced in the repo. Given
DA-003 (échéancier can exceed 100%) and bulk operations (`ProjectGenerationService`, retention deletes),
there should be a *tested* restore-to-5-minutes-ago path and R2 versioning so deletes are recoverable.
**Action:** confirm Neon PITR retention + R2 versioning; document a restore runbook.

### DA-029 ⚪ Low — Unmitigated cold-start on a daily-use B2B product

Neon scales to zero and Render cheap tiers sleep; no keep-warm ping is evident for the backend/DB. The first
request each morning (the promoteur opening the tool at 9am) eats a cold start and may look broken. The
existing keep-alive is the frontend SSE channel, not a DB/backend warmer. **Fix effort:** S (scheduled
keep-warm hitting a cheap query during business hours).

---

## TRACK 7 · Synthesis & Risk Register (whole team)

**29 findings** across 6 tracks: 1 Critical, 6 High, 11 Medium, 11 Low. The single most important signal:
the *surface* is hardened (tenant scoping, JWT, deposit cap, state machine), but the **second-order layer is
thin** — concurrency invariants lean on READ_COMMITTED check-then-insert, the new VEFA pipeline has no audit
trail, one portal access check is broken, and the legal documents/data-protection posture has real gaps.

### 1. Risk register (sorted by Severity × Likelihood)

| ID | Track | Sev | Likelihood | Business / Legal impact | Exploitability | Owner | Fix |
|----|-------|-----|-----------|-------------------------|----------------|-------|-----|
| DA-006 ✅ | T2 | 🔴 Crit | High | Cross-contact PII/contract exfiltration; CNDP-reportable (09-08/44-00) | Trivial (authn'd portal user, enumerate UUIDs) | BE/Security | **DONE** |
| DA-011 | T3 | 🟠 High | High | System enables defeating the 7-day rétractation (44-00) | Trivial (one PATCH) | BE/Legal | S |
| DA-003 ✅ | T1 | 🟠 High | Med | Échéancier breaches legal 100% cap; over-billing | Concurrent requests | BE | **DONE** |
| DA-010 | T3 | 🟠 High | Med | Plaintext CIN/financials; breach blast radius; CNDP | Needs DB access or DA-006 | BE/Data | M |
| DA-013 | T3 | 🟠 High | High | No defensible history of legally significant actions | n/a (evidentiary) | BE | M |
| DA-005 | T1 | 🟠 High | Med | Mail-provider hiccup → app outage (pool exhaustion) | External dependency | BE/SRE | M |
| DA-009 | T3 | 🟠 High | High | Generated reservation contracts voidable (missing 44-00 mentions) | n/a (legal) | Legal/BE | M |
| DA-026 | T6 | 🟡 Med | High | Prod failures invisible until user complains | n/a | SRE | M |
| DA-017 | T4 | 🟡 Med | Med | Overpayment recorded; reconciliation breaks | Single request | BE | S |
| DA-004 | T1 | 🟡 Med | Med | Double-submit double-records payment | Double-click | BE | S |
| DA-025 | T6 | 🟡 Med | Med | Multi-instance double execution of sweeps/deletes | Deploy/scale | SRE | S |
| DA-012 | T3 | 🟡 Med | Med | Wrong/under-declared TVA → incorrect tax filing | User-set field | BE/Legal | S |
| DA-001 | T1 | 🟡 Med | High | 500 instead of 409 under race (no integrity loss) | Concurrent create | BE | S |
| DA-015 | T4 | 🟡 Med | High | Échéancier ≠ price to the centime | Every legal schedule | BE | S |
| DA-007 | T2 | 🟡 Med | Med | Unbounded upload; one bad GLB bricks a project's 3D | Authn'd user | BE | M |
| DA-019 | T5 | 🟡 Med | High | Cannot run buyer payment-default (very common) | n/a (missing) | Product/BE | M |
| DA-020 | T5 | 🟡 Med | Med | Cannot represent contrat cession (common in MA) | n/a (missing) | Product/BE | M |
| DA-022 | T5 | 🟡 Med | Med | No authoritative document version in disputes | n/a (missing) | Product/BE | M |
| DA-002 | T1 | ⚪ Low | Med | Sweep batch aborts on contention (self-heals) | Concurrent | BE | S |
| DA-008 | T2 | ⚪ Low | Low | Foreign-société user assignable as agent | Needs foreign UUID | BE | S |
| DA-027 | T6 | ⚪ Low–Med | Low | Killed migration wedges all future deploys | Deploy mishap | SRE | M |
| DA-018 | T4 | ⚪ Low | Low | No price-edit path; latent stale-children if added | n/a | BE | M |
| DA-016 | T4 | ⚪ Low | Low | Implicit currency; EUR-expansion cost | n/a | BE | L |
| DA-014 | T3 | ⚪ Low | Low | MRE Office des Changes obligations unmodeled | n/a | Product | M |
| DA-021 | T5 | ⚪ Low | Med | No avenant/line-items (add parking) | n/a (missing) | Product | M |
| DA-023 | T5 | ⚪ Low | Low | Cross-société person identity not unified | n/a | Product | L |
| DA-024 | T5 | ⚪ Low | High | Key promoteur analytics unanswerable | n/a | Product/BE | M |
| DA-028 | T6 | ⚪ Low | Low | Unproven recovery from data corruption | n/a (ops) | SRE | — |
| DA-029 | T6 | ⚪ Low | High | Daily cold-start looks broken | Every morning | SRE | S |

### 2. The five that keep us up at night

1. **DA-006 — Portal document IDOR (🔴).** Any authenticated buyer can download every document in the
   société by enumerating UUIDs. It's trivial, it's PII + contracts, and it's CNDP-reportable. *Fix first,
   today.* (One-line scope fix.)
2. **DA-011 — Rétractation defeatable (🟠).** The software lets an agent take the down-payment *during* the
   legally protected cooling-off window. This isn't a missing feature — it's the system actively enabling a
   Loi 44-00 violation. One judge, one annulled deal.
3. **DA-003 — Échéancier can exceed 100% under concurrency (🟠).** A legal cap (Art. 618-17) enforced only
   by a check-then-insert at READ_COMMITTED. Two clicks and the VEFA payment schedule is illegal and the
   buyer is over-billed. Money + law in one race.
4. **DA-010 — Plaintext CIN/financials (🟠).** The crown-jewel personal data sits in clear. On its own it's
   a posture problem; *combined with DA-006* it's a one-request mass exfiltration of national IDs.
5. **DA-013 — No audit trail on the VEFA pipeline (🟠).** When any of the above happens, there is no
   tamper-evident record of who did what when. You cannot defend yourself, reconstruct the incident, or
   satisfy a regulator. The absence of this is what turns the others from "incident" into "unprovable."

   *Dishonourable mention:* **DA-005** — a Brevo outage taking down the whole app via DB-pool exhaustion is
   the one most likely to page someone at 3am.

### 3. Suggested Wave-15 sequencing

- **Hotfix (hours):** DA-006 (scope doc to vente), DA-011 (block `EN_RETRACTATION→ACOMPTE` before deadline),
  DA-017/DA-004 (overpayment + idempotency), DA-001 (translate `DataIntegrityViolationException`).
- **Sprint 1 — integrity & law:** DA-003 (lock/serialize échéance writes), DA-013 (pipeline audit events +
  hardened table), DA-009 (contract mandatory mentions), DA-012 (validate TVA), DA-015 (rounding remainder).
- **Sprint 2 — resilience & data protection:** DA-005 (async email + timeouts), DA-025 (ShedLock),
  DA-026 (error tracking/alerts), DA-010 (field encryption + portal notice), DA-007 (upload limits).
- **Roadmap — domain reality:** DA-019 (payment default), DA-020 (cession), DA-022 (doc versioning),
  DA-024 (transition history → analytics), DA-014/DA-021/DA-023/DA-016 (MRE, avenants, identity, currency).

---

## SAFE STOP POINT — AUDIT COMPLETE

RESUME FROM: — (all tracks T1–T7 done) | findings: 29 (1 Critical · 6 High · 11 Medium · 11 Low)

_Audit only — no code was modified. Fixes are proposed for Wave 15. Items flagged "REQUIRES CURRENT CGI
VERIFICATION" (DA-012) and "verify" (DA-028) need counsel / infra confirmation, not code reading._
