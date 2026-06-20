# YEM HLM — Exhaustive Audit · Wave 15

**Mode:** READ-ONLY. Findings only, zero code changes.
**Date:** 2026-06-14
**Scope:** The blind spots of `deep-audit-wave14.md` — memory/resource lifecycle, ergonomics
under real conditions, state consistency, unhandled edge cases, scale walls, architecture drift,
i18n/expansion readiness — plus confirmation/extension of the substantive tracks.

**Convention:** each finding is marked **CONFIRMED** (the code was read) or **SUSPECTED** (needs a
runtime/prod-config check). Findings that re-confirm or extend a Wave 14 item cite `DA-NNN`.
New IDs are `EX-NNN`.

> **Relationship to Wave 14:** Wave 14 (DA-001…DA-029) covered concurrency, security/IDOR, legal
> mentions, money precision, domain completeness, and resilience. This wave does **not** re-derive
> those; where still open they are referenced. The net-new value here is Tracks 4 (memory/leaks),
> 6 (UX), 11 (i18n/a11y), and the edge/scale findings.

---

## TRACK 1 · Functional Completeness & Business Rules (Salma)

### EX-001 🟠 High — ✅ RESOLVED (2026-06-14) — Generic `PATCH /statut` bypasses the legal guards of the dedicated reservation path
> **Fix:** `VenteService.updateStatut()` split into a public guard + a private `applyStatutChange()`.
> The public path now rejects transitions into guarded-entry stages — `OPTION`, `RESERVE`,
> `EN_RETRACTATION`, `LIVRE_AVEC_RESERVES`, `RESERVES_LEVEES` — with `GuardedStageEntryException`
> (HTTP 409, `ErrorCode.GUARDED_STAGE_ENTRY`), naming the dedicated endpoint to use. `recordDelivery()`
> calls the trusted `applyStatutChange()` so legitimate delivery still works. Frontend: the generic
> advance dialog's `NEXT_MAP` no longer offers guarded transitions (PROSPECT/OPTION/LIVRE_AVEC_RESERVES);
> it now points the user at the dedicated VEFA panel (`confirmReservation` / `liftReserve`). Tests:
> `VenteServiceTest.updateStatut_guardedStages_areRejected` (216 unit tests green); frontend build green.
> Note: the EX-009 timezone half (date math) is now also closed — `MarketConfig.withdrawalDeadline()`
> + the market-zoned `Clock` (`MarketTimeConfig.marketClock` = `Clock.system(Africa/Casablanca)`) — so
> the full EX-011 composite (setup + date math + exit guard) is resolved.
- **Where:** `vente/service/VenteService.java:550` `updateStatut()` vs `:280` `createOption()` / `:317` `confirmReservation()`.
- **What:** Two parallel write paths advance a Vente. `confirmReservation()` enforces the Art. 618‑4
  deposit cap (≤ 5%) → `422`, sets `dateFinDelaiReflexion`, and seeds the rétractation window.
  `updateStatut()` only calls `validateTransition()`. The matrix
  (`VenteService.java:971`) allows `PROSPECT → RESERVE` and `RESERVE → EN_RETRACTATION` directly, so
  `PATCH /api/ventes/{id}/statut {statut:"RESERVE"}` moves a sale into RESERVE **without** the deposit
  cap and **without** populating `dateFinDelaiReflexion`. A vente can then reach `EN_RETRACTATION`
  with a null legal-reflection date — the very field the rétractation guard (DA-011) depends on.
- **Consequence:** An agent (or a script) can put a sale in a reserved/cooling-off state that skips
  the 5% cap (legal, Art. 618‑4) and produces an `EN_RETRACTATION` with no computed withdrawal
  deadline. Legal enforcement is only as strong as the *weakest* of the two paths, and the generic
  path is the weaker one.
- **Reproduce:** Create a vente (PROSPECT) → `PATCH /statut {statut:"RESERVE"}` → observe no deposit
  validation and `dateFinDelaiReflexion == null` → `PATCH /statut {statut:"EN_RETRACTATION"}`.
- **Fix direction:** Route all stage entry through the guarded methods, or have `updateStatut()`
  reject transitions into RESERVE / EN_RETRACTATION / ACOMPTE that carry legal preconditions and
  delegate to the dedicated handlers. Single source of truth for stage-entry side effects.
- **Effort:** M. Links DA-011, T3.4, T9.3.

### EX-002 (functional note) ⚪ Low — CONFIRMED — `RESERVES_LEVEES` is a near dead-end
- **Where:** `VenteService.java:980` `case RESERVES_LEVEES -> Set.of(LIVRE_DEFINITIF);`
- **What:** Once reserves are lifted, the only legal exit is `LIVRE_DEFINITIF` — there is no `ANNULE`
  path and no way to re-open a reserve that resurfaces. Probably intentional, but worth a domain
  decision: in practice a defect found between "reserves lifted" and "final delivery" cannot be
  re-recorded.
- **Fix direction:** Confirm with Salma whether `RESERVES_LEVEES → LIVRE_AVEC_RESERVES` (re-open) or
  `→ ANNULE` should exist. **Effort:** S.

### T1 — Pipeline matrix completeness (CONFIRMED)
The `switch` in `validateTransition()` is total over the enum (no default branch needed; compiler
enforces exhaustiveness). Terminal states `LIVRE_DEFINITIF` and `ANNULE` correctly map to `Set.of()`.
No unreachable state. No illegal forward skips except the legal-guard gap in EX-001. **Verdict: the
matrix shape is sound; the enforcement asymmetry (EX-001) is the real issue.**

> **Deferred (needs a dedicated pass):** T1.1 full RG-A…RG-H implementation table, T1.3 documented
> role-matrix vs `@PreAuthorize` diff, T1.4 persona feature-parity, T1.5 document-trigger↔template
> map. These require the canonical RG list (`docs/spec/business-rules-audit.md` uses prose, not
> `RG-x` IDs — grep returned none) and an endpoint-by-endpoint sweep. Raised as a question below.

---

## TRACK 2 · Concurrency, Transactions & Data Consistency (Rachid)

Confirmed still-current from Wave 14 (re-read, unchanged):
- **DA-001** (🟡) concurrent vente creation surfaces as **500 not 409** — the partial unique index
  `uk_vente_active_property` (changeset 075) backs one-active-vente-per-property, but the
  `DataIntegrityViolationException` is not mapped to a clean 409. **T2.1 status: constraint exists ✔,
  clean-409 mapping ✘.**
- **DA-004** (🟡) payment recording has no idempotency key → double-submit double-records. **Open.**
- **DA-005** (🟠) external I/O (R2 pre-sign, Brevo send) inside `@Transactional` holding a Neon
  connection across a network call → pool starvation. **Open — high-value at Neon's low ceiling.**

### EX-003 🟡 Medium — SUSPECTED — `@Version` optimistic locking is not uniformly present on hot entities
- **Where:** `vente/domain/Vente.java`, `property/domain/Property.java` (need field-level confirm).
- **What:** Changeset 057 added "optimistic lock version" to some tables; it is not clear every
  concurrently-edited aggregate (Vente, Property, Echeance) carries `@Version`. Without it, two
  staff editing the same Vente/Property is last-write-wins with silent field loss (T2.6 / T7.3).
- **How to confirm:** `grep -rn "@Version" hlm-backend/src/main/java` and cross-check against the
  list of user-editable entities.
- **Fix direction:** `@Version Long version` on every user-editable aggregate + 409 on
  `OptimisticLockException`. **Effort:** S–M.

---

## TRACK 3 · Adversarial Security & Tenant Isolation (Imane)

Confirmed solid (re-read this wave):
- **`SocieteContext.clear()` runs in a `finally`** (`JwtAuthenticationFilter.java:137-140`) — the
  ThreadLocal cannot leak société/user across pooled requests. (Also satisfies **T4.11**.) ✅
- DA-006 (portal cross-contact document download) is **RESOLVED** (commit 1602fa8, this branch).

Still open from Wave 14: **DA-007** (GLB upload size/validation), **DA-008** (cross-société agent
assignment), **DA-010** (plaintext CIN/financials), **DA-013** (no tamper-evident audit of pipeline
actions).

### EX-004 🟠 High — ✅ RESOLVED (2026-06-14, via EX-001) — Mass-assignment of `statut` via the generic endpoint defeats pipeline + legal checks
- This is the security framing of **EX-001** (T3.4). The `UpdateVenteStatutRequest` body carries
  `statut` directly; `updateStatut()` accepts any matrix-legal target. The deposit cap and
  reflection-window setup live only in the dedicated handlers, so a `@RequestBody`-driven status move
  is the documented attack: `POST`/`PATCH statut=RESERVE` with no deposit, or chaining toward
  `ACOMPTE` skipping cooling-off semantics. See EX-001 for fix.

> **Deferred (needs the dedicated sweep):** T3.1 full IDOR table over every `:id` endpoint, T3.7
> rate-limit/magic-link strength re-verification, T3.8 prod CORS origins. Wave 14 spot-checked these;
> a complete endpoint matrix was out of scope this wave (raised as a question).

---

## TRACK 4 · Memory, Resource Leaks & Lifecycle (Lina) — *primary new track*

### EX-005 🟠 High — ✅ RESOLVED (2026-06-14) — `ThreeEngineService` is a root singleton that **completes its event Subjects on dispose** → 3D interactivity is dead on every visit after the first
> **Fix:** `ThreeEngineService` changed from `@Injectable({ providedIn: 'root' })` to `@Injectable()`
> (component-scoped); `providers: [ThreeEngineService]` added to both consumers
> (`project-viewer-3d.component.ts`, `mesh-mapping-admin.component.ts`). Each viewer mount now gets a
> fresh engine with fresh `hover$/click$/tap$` Subjects, so revisits work. Collaborators
> `LotMappingService` (self-resets via `clear()` on load) and `ModelLoaderService` (stateless) remain
> root — verified safe. Frontend build green.
- **Where:** `modules/viewer-3d/services/three-engine.service.ts` — `@Injectable({ providedIn:
  'root' })` (singleton, constructed once, never destroyed by Angular). `dispose()` calls
  `this.hover$.complete(); this.click$.complete(); this.tap$.complete();`. The host
  `project-viewer-3d.component.ts:214` calls `engine.dispose()` in `ngOnDestroy`.
- **What:** Because the service is a **root singleton**, `hover$/click$/tap$` are created exactly once.
  After the first navigation away from the viewer, `dispose()` **permanently completes** them. On the
  second visit, `ngAfterViewInit` calls `engine.init()` (rebuilds renderer/scene fine) and re-subscribes
  to the same Subjects (`:148/:177/:187`) — but **a subscription to a completed Subject never emits**.
  Result: hover tooltips, lot-click navigation, and touch-tap all silently stop working on the 2nd and
  every subsequent visit to any 3D viewer within the same SPA session.
- **Consequence:** A flagship feature degrades to "looks fine, does nothing" after one navigation — the
  kind of bug that survives a demo (first load works) and fails in real daily use. Not a memory leak per
  se, but a lifecycle-mismatch defect; additionally the singleton retains `scene/renderer/meshes/canvas`
  references after `dispose()` (they are never nulled), so GPU/JS state from a closed viewer is held
  until the next `init()` overwrites it.
- **Reproduce:** Open a project's 3D viewer, hover a lot (tooltip appears) → navigate away → reopen any
  3D viewer → hover/click: nothing fires.
- **Fix direction:** Either (a) make the engine **component-scoped** (`providers: [ThreeEngineService]`
  on the host, not `providedIn: 'root'`) so a fresh instance is created per mount, or (b) stop completing
  the Subjects in `dispose()` (only `unsubscribe` DOM listeners + dispose GPU) and re-create them in
  `init()`, and null out `scene/renderer/meshes/canvas` on dispose. (a) is cleaner.
- **Effort:** S. **The single highest-value new finding this wave.**

### T4.2 — Three.js GPU disposal (CONFIRMED, mostly good)
`dispose()` cancels the RAF, removes `pointermove/click/touchstart/visibilitychange` listeners,
disconnects the `ResizeObserver`, disposes `OrbitControls`, traverses the scene disposing geometries +
materials, and calls `renderer.dispose()`. **Textures** are not explicitly disposed (the traverse
disposes geometry + material but not `material.map`/normalmap textures); for Draco GLB lots that are
flat-coloured `MeshStandardMaterial` this is usually nil, but a textured model would leak texture VRAM.
Also the held references (EX-005) mean disposed objects aren't GC-eligible until re-init. **Verdict:
disposal logic is good; the singleton lifetime (EX-005) undermines it.**

### T4.1 / T4.3 / T4.4 — Subscriptions, listeners, timers (CONFIRMED, clean)
- Every component file containing `.subscribe()` also contains a `takeUntil`/`takeUntilDestroyed`/
  `take(1)` pattern (file-level scan over 256 call-sites; none isolated). Matches Wave 14 F-008
  (HttpClient one-shots auto-complete). **No systemic subscription leak.**
- `notification-polling.service.ts` (`interval(60_000)`) → `stop()` unsubscribes, `ngOnDestroy` calls
  `stop()`. ✅
- `keep-alive.service.ts` (`setInterval`) → `clearInterval` in `ngOnDestroy`. ✅
- `project-viewer-3d.component.ts:270` `interval(30_000)` status poll is piped through
  `takeUntil(destroy$)`. ✅
- The many `setTimeout(...)` for toast/dropdown dismissal are short-lived (150ms–3s) and self-clear;
  not leaks.
- **Verdict: T4.1/T4.3/T4.4 are genuinely solid — do not touch.**

### T4.11 — ThreadLocal cleanup (CONFIRMED, solid) — see Track 3.

> **Backend leaks (T4.7–T4.10):** Caffeine caches are bounded by TTL + size per `CacheConfig`
> (10s 3D status, etc.). No unbounded per-tenant map was found in the spot-check. R2 input-stream /
> file-handle closure (T4.8) and large entity-graph loading (T4.10) overlap with Track 5 below and
> were not exhaustively traced — raised as a follow-up.

---

## TRACK 5 · Scalability & Performance at Volume (Rachid + Lina)

Context: `GET /api/ventes` and `GET /api/properties` are paginated (`PageResponse`, F-006). The
remaining `List<T>` endpoints are FK-bounded sub-resources.

### T5.2 — Unpaginated list endpoints (CONFIRMED, mostly acceptable)
`vente/api/VenteController.java` returns `List<T>` for `listReserves`, `listEcheances`,
`listDocuments`, `CoAcquereurController.list`, `ReserveLivraisonProjectController.getProjectReserves`.
All are bounded by a single parent FK (one vente's échéances ≈ ≤ 12; co-acquéreurs ≤ a handful).
**`getProjectReserves`** is the one to watch — reserves across *all* lots of a large project could grow;
at 5,000 units with reserves it returns an unbounded list. **Suggest pagination on the project-scoped
reserve list only.** Verdict: low risk except the project-wide reserve list.

### EX-006 🟡 Medium — SUSPECTED — 3D status endpoint at fleet scale
- **Where:** `viewer3d/service/Project3dService.getStatusSnapshot()` (10s Caffeine cache) +
  `project-viewer-3d.component.ts:270` 30s client poll.
- **What:** The 10s server cache and 30s client poll are sensible per-project. But the cache key and
  the underlying `lot_3d_mapping` ↔ property status join need an index on `(societe_id, projet_id)`
  to stay cheap when many concurrent users poll many projects. At 500 lots × N users this is the
  likeliest CPU/query hotspot.
- **How to confirm:** `EXPLAIN ANALYZE` the snapshot query at 5k properties; check for an index on
  `lot_3d_mapping(societe_id, projet_id)` and on `property(societe_id, ...)` used by the join.
- **Fix direction:** Confirm composite indexes; consider increasing cache TTL or push-on-change.
  **Effort:** S–M.

> **Deferred (needs profiling, not just reading):** T5.1 N+1 sweep (every `@OneToMany` fetch
> strategy + DTO lazy-trigger), T5.3 index coverage audit, T5.5 frontend virtual-scroll at 1,000+
> rows, T5.6 KPI aggregation (SQL vs Java), T5.7 Neon pooled-connection ceiling, T5.8 payload bloat.
> These are runtime/EXPLAIN tasks — flagged for a profiling session, not assertable from source alone.
> **DA-005 (I/O in transaction) is the most acute scale risk already on record.**

---

## TRACK 6 · Ergonomics & UX Under Real Conditions (Karim) — *primary new track*

### EX-007 🟡 Medium — ✅ RESOLVED (2026-06-14) — The language switcher is cosmetic: it changes nothing but `dir`, and `dir=rtl` actively breaks the layout
> **Fix:** removed the façade. The `LanguageSwitcherComponent` is deleted and unmounted from the CRM
> shell, super-admin shell, and login. The `APP_INITIALIZER` no longer reads `hlm_lang`/sets `dir=rtl`;
> it now **clears the stale `hlm_lang` key** and forces `dir=ltr` / `lang=fr`, so a user who had
> previously selected Arabic is no longer pinned to a broken RTL layout (self-healing). Decision: the
> product is committed FR-only (Phase D removed ngx-translate); reinstate a real i18n layer + switcher
> together when France/Arabic work begins (EX-014). Frontend build green.
- **Where:** `core/components/language-switcher.component.ts` (mounted in `shell`, `superadmin-shell`,
  `login`). Offers **FR / EN / عربي**. `switchLanguage()` persists to `localStorage`, PUTs
  `/auth/me/langue`, fades the body, and sets `document.documentElement.dir = lang==='ar' ? 'rtl':'ltr'`.
- **What:** ngx-translate was removed (Phase D, FR-only); **every UI string is hardcoded French.**
  So selecting EN or عربي does a fade animation and leaves 100% of the text in French. Selecting Arabic
  additionally sets `dir=rtl` on a layout that has **no RTL stylesheet** — flipping the entire CRM to a
  garbled mirrored state while the words stay French/LTR-authored. The control looks like a core feature
  and does the opposite of working.
- **Consequence:** Real users will click it (it's prominent in the shell + login), get a broken/mirrored
  UI with unchanged language, and lose trust. For an Arabic-speaking Moroccan user it is worse than
  having no switcher.
- **Reproduce:** Click "عربي" anywhere → page mirrors to RTL, all labels remain French.
- **Fix direction:** Either hide the switcher until i18n exists (FR-only reality), or re-introduce a real
  i18n layer (see EX-009). At minimum, remove the `dir=rtl` side effect until an RTL stylesheet exists.
- **Effort:** S (hide) / L (real i18n). Links T11.1.

### EX-008 🟡 Medium — ✅ RESOLVED (2026-06-14) — `LOCALE_ID` is `'fr'` (metropolitan France), not `'fr-MA'`
> **Fix:** `app.config.ts` now sets `LOCALE_ID = 'fr-MA'` (registers `localeFrMA`; keeps `localeFr`
> for any explicit `:'fr'` usage) so `date`/`number`/`percent` pipes format per Morocco, and adds
> `{ provide: DEFAULT_CURRENCY_CODE, useValue: 'MAD' }` so any `currency` pipe defaults to MAD (Angular's
> built-in default is USD). **Note:** the codebase currently has **no `| currency` pipe usage** — money
> is rendered via hardcoded helpers (`toLocaleString('fr-FR') + ' MAD'`, ` K/M MAD`) scattered across
> reports/dashboards. Those are correct (always ' MAD') but duplicated and use `fr-FR` grouping; the
> `DEFAULT_CURRENCY_CODE` change is defensive for future pipe usage. **Follow-up:** consolidate the
> hardcoded money helpers into one shared `formatMad()` / currency pipe (consistency, France-readiness).
- **Where:** `app.config.ts:23` `{ provide: LOCALE_ID, useValue: 'fr' }`, `registerLocaleData(localeFr)`.
- **What:** Angular's `currency`/`number`/`date` pipes resolve against France French. The default
  currency for the `currency` pipe under `fr` is **EUR (€)** — every `{{ x | currency }}` without an
  explicit `'MAD'` code renders euros for a Moroccan product. Date/number grouping is FR-metropolitan,
  not `fr-MA`. This is both a correctness risk now (MAD/EUR leakage, T6.8) and the seed of the
  France-expansion locale story (T11.2).
- **How to confirm:** grep `currency` pipe usages without an explicit `'MAD'` argument.
- **Fix direction:** Provide a per-market locale + default currency (centralized), pass `'MAD'`
  explicitly until then. **Effort:** S now / M for multi-market.

> **Deferred (needs a clickable build / device):** T6.1 slow-network behavior, T6.2 click-economy per
> persona, T6.3 mid-task error recovery / input loss, T6.5 feedback latency / double-submit, T6.7
> mobile 380px one-handed usability, T6.9 destructive-action confirmation. These are interaction tests,
> not source assertions — flagged for a hands-on UX session. (Note: Wave 12 D added skeleton loaders &
> empty states, so T6.6 is partially addressed.)

---

## TRACK 7 · Unhandled Scenarios & Edge Cases (Salma + Rachid)

### EX-009 🟠 High — ✅ RESOLVED (2026-06-14) — Legal deadlines mix `LocalDate.now()`/`LocalDateTime.now()` (JVM zone) with `Instant` — off-by-one on the 7-day rétractation
> **Fix:** introduced a **market-aware `Clock`** — `MarketConfig.getZoneId()` (MA → `Africa/Casablanca`,
> FR → `Europe/Paris`, overridable via `app.market.zone-id`) feeds a `Clock` bean
> (`legal/MarketTimeConfig.java`). `VenteService` now injects `Clock` and every legal date/time uses
> the zoned form (`LocalDate.now(clock)`, `LocalDateTime.now(clock)`, `Instant.now(clock)`) — the
> rétractation window, the deadline comparison in `exerciseRetractation`, the reserve-lift window, the
> sweeps, and the penalty calc. Test `confirmReservation_coolingOffDeadlineUsesMarketZone` pins a clock
> at `23:30 UTC` (= `00:30` next day in Casablanca) and asserts the 7-day window ends on the
> Casablanca date, not the UTC date (217 unit tests green). The injected clock also makes the service
> deterministically testable. **Extended (2026-06-14):** `ReservationService` now injects the same
> `Clock` — the +7-day expiry deadline, the `findExpired` sweep, and the 48h expiring-soon
> notifications all use the market zone (tests construct it with a fixed Casablanca clock; 217 unit
> tests green). Deliberately left on wall-clock (correct by design): entity `@PrePersist`/`@PreUpdate`
> audit stamps (`createdAt`/`updatedAt`/`confirmedAt`/etc.), dashboard "asOf" timestamps, and the
> ref-generator year (a ~1h/year New-Year-boundary edge) — these are "when the server recorded it",
> not legal deadlines. Convert the ref-year too if strict per-jurisdiction ref numbering is required.
- **Where:** `VenteService.java` — rétractation deadline `:337`
  `vente.setDateFinDelaiReflexion(LocalDate.now().plusDays(marketConfig.getDelaiRetractationJours()))`;
  compromis-derived dates `:249-250` use `request.dateCompromis().plusDays(...)`; `stageEntryDate`
  `:339/:503/:566` uses `LocalDateTime.now()`; **but the option** `:300` uses
  `Instant.now().plus(duree, HOURS)` and the sweep `:386` queries `optionExpireAtBefore(Instant.now())`.
- **What:** `LocalDate.now()` / `LocalDateTime.now()` resolve against the **JVM default zone**. If the
  container runs UTC (Docker/Coolify default), then between **23:00–00:00 Africa/Casablanca** (UTC+1
  year-round) the server still reads the *previous* calendar day, so the 7-day rétractation deadline is
  computed **one day short**. The withdrawal right (Art. 618‑3) is date-precise and legally protected —
  an off-by-one here either truncates a buyer's legal right or extends the developer's exposure. Option
  hours (Instant) are immune; the date-based legal windows are not.
- **How to confirm:** Check the deployed container's `TZ`/`user.timezone`; create a vente at ~23:30
  Casablanca local and inspect `dateFinDelaiReflexion`.
- **Fix direction:** Compute all legal dates in an explicit zone:
  `LocalDate.now(ZoneId.of("Africa/Casablanca"))`, or inject a market-aware `Clock`. Centralize so the
  France market can swap the zone. **Effort:** S. Links DA-011, T9.7, T11.3.

### EX-010 🟡 Medium — ✅ RESOLVED (2026-06-20) — In-memory `@Scheduled` sweeps + no durable trigger for option/legal expiries across redeploys
> **Fix (EX-010 + DA-025):** `@SchedulerLock` (ShedLock, the outbox-dispatcher pattern) now wraps every
> DB-backed domain sweep, so on a multi-instance deploy only one node runs a given sweep — the
> one-shot side effects (freeing a property, closing a rétractation window, sending one reminder/expiry
> email, deleting tokens, GDPR retention) can no longer double-fire. Locked: `VenteVefaScheduler`,
> `ReservationExpiryScheduler`, `DepositWorkflowScheduler`, `PortalTokenCleanupScheduler`,
> `DataRetentionScheduler`, `reminder.ReminderScheduler`, `payments.ReminderScheduler` (joining the
> already-locked outbox dispatcher, notification digest, and visite reminder job).
> **Deliberately not locked:** `LoginRateLimiter.cleanupIdleBuckets` cleans a per-instance in-memory
> `ConcurrentHashMap` — it *must* run on every node, so a cluster lock there would be a bug.
> Durability was already fine (deadline state is DB-persisted); this closes the multi-instance idempotency gap.
- **Where:** `VenteVefaScheduler` (hourly option-expiry + VEFA sweeps), `ReservationExpiryScheduler`.
- **What:** These are cron `@Scheduled` sweeps reading DB rows (`optionExpireAtBefore(now)`), so the
  *deadline state is persisted in the DB* — good, a redeploy doesn't lose the data. **However** Wave 14
  **DA-025** flagged that most schedulers lacked ShedLock → on multi-instance deploy the sweeps
  double-executed. Durability was OK (DB-backed), but **idempotency under multi-instance was not**.

Still open from Wave 14 (re-confirmed relevant): **DA-019** (no buyer payment-default state),
**DA-020** (no cession/transfert de contrat), **DA-022** (no document versioning/signed state),
**DA-004** (retry idempotency on POST). T7.2 (delete-while-in-use cascade), T7.4 (orphaned R2 object on
mid-upload drop), T7.7 (price-change échéance recompute — DA-018 says no edit path exists) remain.

---

## TRACK 8 · Financial Correctness & Audit Trail (Omar)

Re-confirmed from Wave 14 (unchanged this wave):
- Money is `BigDecimal` on transactional fields; the only `double`/`Double` in the vente/legal/payments
  tree is `VenteRepository` aggregate projections (`AVG(...)` → `Double`), which is acceptable for a
  reporting KPI, not stored money. **T8.1 verdict: no stored float money found. ✅**
- **DA-015** (🟡) échéancier amounts rounded independently, no remainder reconciliation to the centime.
  **Open.**
- **DA-017** (🟡) overpayment not capped on call-for-funds. **Open.**
- **DA-016** (⚪) currency implicit (EUR-expansion) — reinforced by EX-008. **Open.**
- **DA-013** (🟠) pipeline transitions / price changes / document generation are not captured as an
  immutable, tamper-evident audit trail (only `updated_at`). **Open — evidentiary quality matters for
  44-00 disputes.** Note B-004 added `@ReadAudit` on `getLegalDetails()`/`getCommercial()` (sensitive
  *reads*), but **write/transition history is still not an immutable ledger.**

No new T8 finding this wave beyond reinforcing DA-013/DA-015/EX-008.

---

## TRACK 9 · Legal & Regulatory Exposure (Nadia)

Open from Wave 14 (all still relevant): **DA-009** (missing mandatory 44-00 mentions in generated
reservation contract), **DA-010** (plaintext CIN/financials; CNDP), **DA-011** (rétractation defeatable
— see EX-001, EX-009), **DA-012** (user-settable TVA, needs CGI re-verification), **DA-014** (MRE /
Office des Changes unmodeled). Phase B added `data-retention.md`, `pdf-review-checklist.md`, the
`@ReadAudit` aspect, and the CNDP declaration fields (`ComplianceController`) — partial mitigations of
the 09-08 surface, not closures.

### EX-011 🟠 High — ✅ RESOLVED (2026-06-14, via EX-001 + EX-009 + cooling-off guard) — The rétractation deadline can be **null or miscomputed**, and the window was **defeatable by advancing the pipeline** (composite legal finding, incl. DA-011)
> **Fix — three layers:**
> 1. **Entry (EX-001):** `EN_RETRACTATION` is reachable only through `confirmReservation()`, which
>    always sets `dateFinDelaiReflexion`.
> 2. **Date math (EX-009):** the deadline is computed in the market zone (Casablanca) via a single
>    source of truth, `MarketConfig.withdrawalDeadline(LocalDate)`, whose Javadoc states the day-count
>    convention (⚠️ still **REQUIRES LEGAL VERIFICATION** of day-0/inclusive boundary by counsel).
> 3. **Exit (DA-011, new):** `VenteService.enforceRetractationWindow()` blocks advancing
>    `EN_RETRACTATION → ACOMPTE` while the window is open (or the deadline is unknown) →
>    `RetractationWindowOpenException` (HTTP 409, `RETRACTATION_WINDOW_OPEN`). The only permitted exits
>    during the window are the buyer's withdrawal (`→ ANNULE`, `exerciseRetractation`) and the
>    scheduled close once the deadline elapses (`closeExpiredRetractations → RESERVE`). The boundary is
>    inclusive, consistent with `exerciseRetractation`. Frontend: the generic "Avancer" dialog no longer
>    offers `EN_RETRACTATION → ACOMPTE`; it shows "withdraw or wait for the window to close".
>    Tests: `updateStatut_blocksAcompteWhileRetractationWindowOpen` +
>    `updateStatut_allowsAcompteAfterRetractationWindow` (219 unit tests green); frontend build green.
> **DA-011 is now closed** — the cooling-off right can no longer be curtailed by a status PATCH.
> **Re-verified 2026-06-14:** end-to-end implementation confirmed against the code on this pass —
> public `updateStatut()` → `GuardedStageEntryException` for guarded stages → delegates to private
> `applyStatutChange()`, which runs `validateTransition()` then `enforceRetractationWindow(vente, target)`
> (`VenteService.java:635, :1043`); the guard compares against `LocalDate.now(clock)` where `clock` is the
> Casablanca-zoned `marketClock` bean, blocks while the deadline is in the future **or null** (fail-safe),
> and only permits `→ ANNULE` (buyer withdrawal). Mapped to HTTP 409 `RETRACTATION_WINDOW_OPEN`. Tests
> exercise the **public** path; `VenteServiceTest` green (23 tests in the class; full unit suite green).
> Only residual is the legal day-count sign-off below (not a code item).
- This is the legal synthesis of **EX-001 + EX-009**: (a) a vente reaching `EN_RETRACTATION` via the
  generic `PATCH /statut` has **no `dateFinDelaiReflexion`** (EX-001), and (b) when the dedicated path
  *does* set it, it's computed in the JVM zone and can be off by a day (EX-009). Either way the system
  cannot reliably prove the buyer's Art. 618‑3 window — a contract-voidability and CNDP/consumer risk.
- **Fix direction:** Single guarded stage-entry + Casablanca-zoned `Clock` (EX-001 + EX-009 fixes
  together close this). **Effort:** M. ⚠️ **REQUIRES LEGAL VERIFICATION** of the exact day-count rule
  (inclusive/exclusive of day 0).

> ⚠️ Per conduct rules: every legal-constant assertion above (5% cap, 7-day window, TVA rate/thresholds)
> **REQUIRES VERIFICATION against the current Loi 44-00 / CGI text by counsel** — do not treat the coded
> constants as authoritative.

---

## TRACK 10 · Architecture Coherence & Tech Debt (Rachid)

### EX-012 🟡 Medium — ✅ RESOLVED (2026-06-14) — Stage-entry side effects are duplicated across write paths (the root cause of EX-001)
> **Fix:** introduced one private `VenteService.applyStageEntry(vente, target)` that owns **all**
> stage-entry side effects — the universal mechanics (`statut` + `stageEntryDate` + `probability`),
> contact-lifecycle advancement (target-driven: `EN_RETRACTATION→CLIENT`, `LIVRE_DEFINITIF→COMPLETED_CLIENT`),
> property workflow (`ACTE→sell`, `ANNULE→release` via the shared `releasePropertyForCancelledVente`),
> échéance cancellation on `ANNULE`, and the `SaleFinalizedEvent`. All four write paths now delegate to
> it: `applyStatutChange` (generic), `confirmReservation` (`→EN_RETRACTATION`), `exerciseRetractation`
> (`→ANNULE`), `liftReserve` (`→RESERVES_LEVEES`). The previously-divergent ANNULE property release
> (inline in `applyStatutChange` vs. helper in `exerciseRetractation`) is unified, and the
> `setStatut+stageEntryDate+probability` triple is no longer copy-pasted in four places. Callers keep
> only their stage-specific preconditions/fields (deposit cap, reserves, motif/dates) and the post-save
> `VenteAnnulee` event. Behavior preserved: **219 unit tests green** (one pre-existing wall-clock-fragile
> test made deterministic against the injected clock). This closes the systemic pattern **P1**.
- **Where:** `VenteService` — deposit cap, reflection-window seeding, property-status driving, contact
  status advancement, and KPI events are partly in `confirmReservation()`/`exerciseRetractation()`/
  `recordDelivery()`/`liftReserve()` and partly re-implemented in `updateStatut()`. They have **drifted**
  (the deposit cap exists in one path only).
- **Consequence:** Every new transition is a fresh chance to forget a side effect. This is the systemic
  pattern behind EX-001/EX-004/EX-011.
- **Fix direction:** A single `enterStage(vente, target, ctx)` that owns *all* per-stage side effects;
  the controllers/handlers feed it data, never re-implement effects. **Effort:** M.

### EX-013 ⚪ Low — CONFIRMED — Dead/contradictory documentation of the pipeline (tech-debt/onboarding hazard)
- **Where:** `CLAUDE.md` still documents the **old** `COMPROMIS→FINANCEMENT→ACTE_NOTARIE→LIVRE` state
  machine and "Vente State Machine (as-implemented)" block, contradicting the actual VEFA enum
  (`VenteStatut.java` — `ACTE`, `LIVRE_DEFINITIF`, plus OPTION/RESERVE/EN_RETRACTATION/...). D-001 fixed
  stale Javadoc in 5 Java files but the CLAUDE.md state-machine diagrams remain stale.
- **Consequence:** New contributors (and future audits) reason from a wrong model.
- **Fix direction:** Update the CLAUDE.md state-machine sections to the VEFA pipeline. **Effort:** S.

T10.2 (tenant isolation hand-rolled per endpoint via `requireSocieteId()` rather than one enforced
aspect) — confirmed pattern; RLS phase-2 is the backstop but the service-layer check is still manual
per method, so a forgotten call is a latent IDOR. T10.5 (France-expansion coupling) — reinforced by
EX-007/EX-008/EX-009 (hardcoded FR strings, `fr` locale, JVM-zone legal math). T10.6 dead code: the
language switcher's EN/AR options (EX-007) and the `localeFr`-only setup are effectively dead multi-lang
scaffolding.

---

## TRACK 11 · Accessibility & i18n / Expansion Readiness (Karim + Lina) — *primary new track*

### EX-014 🟠 High — 🟡 FOUNDATION IMPLEMENTED (2026-06-14), string migration in progress — i18n is architecturally absent: 100% hardcoded French; France/Arabic expansion is currently impossible without a rewrite
> **Decision:** option (b) — re-introduce a real i18n layer (chosen over FR-only cleanup). The architectural
> finding ("i18n is absent / expansion needs a rewrite") is now **resolved**: a runtime i18n layer is wired
> and live. Re-keying every string is the remaining mechanical follow-through, tracked in
> `docs/i18n/i18n-migration-guide.md`.
> **Implemented:** `@ngx-translate/core` v17 + HTTP-loader catalogs (`public/i18n/{fr,en,ar}.json`);
> `I18nService` facade (`core/i18n/`) owning language registry, persistence (`localStorage` +
> `langueInterface` profile sync), `<html lang/dir>`, and RTL; `provideTranslateService` + APP_INITIALIZER in
> `app.config.ts` (first paint is pre-translated, French fallback so partial migration never blanks a screen);
> the **functional** `LanguageSwitcher` (FR/EN/عربي) restored and mounted in login + CRM shell;
> `[dir=rtl]` baseline in `styles.css`; `AuthService` adopts the user's saved language on `/auth/me`.
> **Fully migrated as canonical patterns:** the login screen and the entire CRM shell navigation
> (sections, rail items + tooltips, bottom nav, search, logout), with `common`/`auth`/`nav`/`lang`
> namespaces translated in all three languages. `npm run build` green.
> **Remaining (the "re-key everything" tail):** ~57/62 templates + ~61 TS components, migrated feature-by-feature
> per the guide (build stays green via the French fallback); `en`/`ar` to reach key parity; full-page RTL audit
> beyond the chrome. France/Arabic launch is now a translation+migration effort, **not** a rewrite.
- **Where:** Phase D removed ngx-translate (613 pipes deleted, FR-only). User strings are inline French
  across components. Only 23 files still reference "translate" (residual). The `language-switcher`
  (EX-007) is the visible symptom.
- **What:** There is no key/catalog layer. The platform's stated future is France (Loi SRU) — and the
  switcher even advertises Arabic — yet there is no mechanism to render any string in another language.
- **Consequence:** France expansion = re-introduce an entire i18n layer + re-key every template (weeks of
  work), not a config switch. Marketed multi-language is non-functional today.
- **Fix direction:** Decide explicitly: (a) commit to FR-only and remove the switcher + Arabic/English
  affordances, or (b) re-introduce `@angular/localize` or ngx-translate before France work begins.
  **Effort:** L. Owner: Product + FE.

### EX-015 🟡 Medium — PARTIALLY RESOLVED (2026-06-14) — Multi-market legal constants partly centralized in `MarketConfig`, but locale is not market-driven
- **Where:** `legal/MarketConfig.java` holds delays/percentages/penalty (good — T11.3 partially met).
  **Update:** the legal-date **zone** is now market-driven (`MarketConfig.getZoneId()` + `Clock` bean,
  EX-009). Still open: the frontend **locale/currency** (EX-008) is hardcoded `fr`/EUR, not derived from
  the active market. The backend `Clock` is now consumed by both `VenteService` and
  `ReservationService` (all VEFA + reservation deadline math is market-zoned).
- **Fix direction:** Extend `MarketConfig` (or a `Market` abstraction) to own `ZoneId`, `Locale`, and
  default currency; resolve per société. **Effort:** M. Links EX-008, EX-009.

> **Deferred (needs a build + AT tools):** T11.4 WCAG-AA keyboard/focus/ARIA/contrast pass (the 3D
> viewer's keyboard-Tab nav and screen-reader fallback specifically), T11.5 RTL readiness (EX-007 shows
> `dir=rtl` is wired but unsupported). Flagged for an accessibility session.

---

## TRACK 12 · Resilience, Failure Modes & Observability (Yassine)

Re-confirmed from Wave 14: **DA-026** (no error tracking/alerting — failures invisible until a user
calls), **DA-025** (schedulers lack ShedLock — see EX-010), **DA-027** (startup-coupled Liquibase can
wedge deploys), **DA-029** (unmitigated cold-start on a daily-use B2B tool — Neon scale-to-zero +
backend cold start = "every morning feels broken"). The `keep-alive.service.ts` ping partially addresses
the *frontend session* keep-alive but not backend/Neon cold start.

### EX-016 🟡 Medium — ✅ RESOLVED (2026-06-14) — `/actuator/health` likely reports UP without verifying R2 reachability
> **Fix:** added `ObjectStorageHealthIndicator` (`media/health/`) — a HEAD on the configured bucket
> (`ObjectStorageMediaStorage.verifyBucketReachable()`), result cached 20s so frequent probes don't
> hammer R2. It's `@ConditionalOnProperty(app.media.object-storage.enabled=true)` (local-disk deploys
> have no external storage to probe) and contributes to the **aggregate** `/actuator/health` so an R2
> outage is visible/alertable — but it is deliberately **not** in the liveness/readiness probe groups
> (`management.endpoint.health.probes.enabled=true`), so an R2 blip never restarts or de-routes the
> container. `application.yml` documents that deploy probes should target `/actuator/health/readiness`.
> Backend build green. **Note:** Brevo/SMTP health remains gated behind `MAIL_HEALTH_ENABLED` (unchanged);
> wiring DA-026 alerting on top of these signals is the remaining observability step.
- **Where:** `application.yml` management config + default Spring Boot health contributors.
- **What:** Spring's default health aggregates DB + disk; **R2 (Cloudflare object storage) is an external
  HTTP dependency with no auto health contributor.** If R2 is down, `/health` can still return 200 while
  every document/3D/upload flow is broken (T12.7). Combined with DA-026 (no alerting), the first signal is
  an angry call.
- **How to confirm:** Hit `/actuator/health` with R2 credentials revoked; check for a custom
  `HealthIndicator` bean for storage.
- **Fix direction:** Add a lightweight R2 `HealthIndicator` (HEAD on a known key) and surface Brevo/SMTP
  status; wire DA-026 alerting. **Effort:** S–M.

> **Deferred (needs prod/ops access):** T12.1 dependency-down behavior matrix, T12.3 failed-migration
> mid-deploy recovery, T12.5 Neon PITR + R2 versioning *tested* restore (DA-028), T12.6 cold-start timing.
> These need the running environment, not the repo.

---

## TRACK 13 · Synthesis, Risk Register & Systemic Patterns

### 1. Risk Register (new EX findings + still-open DA highlights), sorted by Severity × Likelihood

| ID | Track | Sev | Likelihood | C/S | Impact | Owner | Effort |
|----|-------|-----|-----------|-----|--------|-------|--------|
| **EX-005** ✅ | T4 | 🟠 High | High | C | Flagship 3D feature silently dead after 1 navigation | FE | **DONE** |
| **EX-001** ✅ | T1/T3/T9 | 🟠 High | Med | C | Legal deposit-cap + rétractation setup bypassable via generic PATCH | BE/Legal | **DONE** |
| **EX-009** ✅ | T7/T9 | 🟠 High | Med | C/S | Off-by-one on 7-day legal rétractation (JVM zone) | BE/Legal | **DONE** |
| **EX-011** ✅ | T9 | 🟠 High | Med | C | Cannot reliably prove buyer's Art.618-3 window (EX-001+EX-009) | BE/Legal | **DONE** (⚠️ day-count rule still needs counsel sign-off) |
| **EX-014** 🟡 | T11 | 🟠 High | High | C | France/AR expansion blocked; marketed multi-lang non-functional | Product/FE | **Foundation DONE; string migration in progress** (see i18n-migration-guide.md) |
| DA-005 | T2 | 🟠 High | Med | C | Mail/R2 hiccup → Neon pool exhaustion → outage | BE/SRE | M |
| DA-010 | T3 | 🟠 High | Med | — | Plaintext CIN/financials; CNDP blast radius | BE/Data | M |
| DA-013 | T8/T9 | 🟠 High | High | — | No tamper-evident history of legal actions | BE | M |
| DA-009 | T9 | 🟠 High | High | — | Generated reservation voidable (missing 44-00 mentions) | Legal/BE | M |
| **EX-007** ✅ | T6/T11 | 🟡 Med | High | C | Language switcher breaks layout, changes nothing | FE | **DONE** |
| **EX-008** ✅ | T6/T11 | 🟡 Med | High | C | `fr` locale → EUR/format leakage on a MAD product | FE | **DONE** |
| **EX-012** ✅ | T10 | 🟡 Med | High | C | Duplicated stage-entry effects → recurring legal gaps | BE | **DONE** |
| **EX-016** ✅ | T12 | 🟡 Med | Med | S | `/health` green while R2 down → silent outage | SRE | **DONE** |
| **EX-010** ✅ | T7/T12 | 🟡 Med | Med | S | Multi-instance scheduler double-fire (DA-025) | SRE | **DONE** |
| **EX-003** | T2 | 🟡 Med | Med | S | Last-write-wins edit loss (no `@Version` everywhere) | BE | S |
| **EX-006** | T5 | 🟡 Med | Med | S | 3D status query hotspot at fleet scale | BE | S–M |
| **EX-015** | T11 | 🟡 Med | Low | S | Market zone/locale not config-driven | BE | M |
| DA-026 | T12 | 🟡 Med | High | — | Prod failures invisible until user calls | SRE | M |
| DA-015/017/004 | T8/T2 | 🟡 Med | Med | — | Rounding drift / overpayment / double-record | BE | S |
| **EX-002** | T1 | ⚪ Low | Low | C | `RESERVES_LEVEES` near dead-end | Product | S |
| **EX-013** | T10 | ⚪ Low | Low | C | Stale CLAUDE.md state-machine docs | BE | S |
| DA-029 | T12 | ⚪ Low | High | — | Daily cold-start looks broken | SRE | S |

### 2. The Findings That Keep Us Up

1. **The 3D viewer dies after one navigation (EX-005).** Demos pass, daily use fails: the second time
   anyone opens a 3D plan, hovering and clicking lots do nothing. A signature feature degrades to a
   static picture, silently, with no error — the worst failure shape for trust.
2. **A buyer's legal withdrawal window can be null or off by a day (EX-001 + EX-009 = EX-011).** Between
   the generic-PATCH path that never sets the reflection date and the JVM-zone date math that can land a
   day early near midnight Casablanca, the system cannot reliably prove the Art. 618‑3 right. In a
   dispute, that is a voidable reservation and a CNDP/consumer-protection exposure.
3. **One mail or R2 hiccup can take the whole app down (DA-005), and `/health` won't tell you (EX-016,
   DA-026).** External I/O inside a DB transaction holds a scarce Neon connection across a network call;
   under a Brevo/R2 slowdown the pool exhausts and the app stops serving — while the health check stays
   green and no alert fires.
4. **CIN and financial data sit in plaintext (DA-010).** Any DB-level exposure is a directly reportable
   CNDP/09-08 breach of the most sensitive fields the platform holds.
5. **There is no immutable ledger of legally significant actions (DA-013).** Pipeline transitions, price
   changes, and document generation leave only `updated_at`. When a contract's history is challenged,
   there is no defensible audit evidence.
6. **Marketed multi-language is a façade (EX-014 + EX-007).** The product shows FR/EN/عربي and even flips
   to RTL, but every string is hardcoded French. France expansion is a rewrite, not a setting — and an
   Arabic user today gets a broken mirrored UI.
7. **The scale wall is the transaction-bound I/O + unindexed hot paths (DA-005, EX-006).** Fine at 50
   units; at 5,000 with concurrent pollers and a mail/R2 dependency in the request path, connection
   pressure and the 3D status query are where it first buckles.

### 3. Systemic Patterns (fix-once, prevent-a-class)

- **P1 — Duplicated stage-entry side effects (EX-012 → EX-001/004/011).** Legal guards live in some write
  paths and not others. *Fix once:* one `enterStage()` owning all per-stage effects.
- **P2 — Implicit time/locale/currency (EX-008/009/015, DA-016).** Dates use the JVM zone, the frontend
  uses `fr`/EUR, currency is implicit MAD. *Fix once:* a `Market` abstraction owning `ZoneId`, `Locale`,
  currency, and legal constants, resolved per société.
- **P3 — Singleton lifetime vs component lifetime (EX-005).** A `providedIn:'root'` service that holds
  per-view, disposable resources. *Fix once:* component-scoped providers for anything with a `dispose()`.
- **P4 — Invisibility of failure (DA-026, EX-016, DA-025).** No alerting, health doesn't reflect external
  deps, sweeps can double-fire. *Fix once:* error tracking + real health contributors + ShedLock on all
  sweeps, as one observability hardening pass.
- **P5 — Hand-rolled tenant isolation (T10.2).** `requireSocieteId()` per method; RLS is the backstop but
  a forgotten call is a latent IDOR. *Fix once:* an enforced aspect/interceptor that fails closed.

### 4. What's Genuinely Solid (do not touch)

- **ThreadLocal tenant context** is set in the filter and **cleared in `finally`** — no cross-request
  leak (`JwtAuthenticationFilter.java:137-140`). ✅
- **Subscription/timer/listener hygiene** on the frontend: takeUntil/`stop()`/`clearInterval` are
  consistently present; the notification poll and keep-alive clean up correctly. ✅
- **Three.js GPU disposal logic** itself (geometry/material/controls/renderer/RAF/listeners) is thorough —
  it's only the singleton lifetime that undermines it (EX-005). ✅
- **The pipeline transition matrix** is exhaustive and compiler-checked; terminal states are correct. ✅
- **Money is `BigDecimal`** on stored transactional fields (no float money found). ✅
- **One-active-vente-per-property** is backed by a real partial unique index (changeset 075). ✅
- **Portal cross-contact document IDOR (DA-006) is fixed** on this branch. ✅

### 5. Suggested Fix Sequencing (Wave 16)

**Blocks "first real client / legal go-live":**
1. EX-001 + EX-009 + EX-011 (one workstream): single guarded `enterStage()` + Casablanca `Clock`. Closes
   the rétractation/deposit legal gaps. *(P1 + P2)*
2. DA-010 encrypt CIN/financials at rest. *(CNDP)*
3. DA-005 move R2/Brevo I/O out of `@Transactional`; add HTTP timeouts. *(outage prevention)*
4. EX-005 component-scope the 3D engine. *(flagship feature correctness — small, high ROI)*

**Blocks "production confidence":**
5. P4 observability pass: error tracking + R2 `HealthIndicator` (EX-016 ✅) + ShedLock on all sweeps
   (EX-010/DA-025 ✅) + DA-026 alerting. *(Remaining: DA-026 error-tracking/alerting — needs an
   external sink + secrets.)*
6. DA-013 immutable transition/audit ledger.
7. EX-003 `@Version` on user-editable aggregates.

**Blocks "trust / polish":**
8. EX-007 hide or fix the language switcher; EX-008 fix locale/currency defaults.
9. DA-001 409-mapping, DA-004 idempotency, DA-015/017 money reconciliation/overpayment.
10. EX-013 fix stale CLAUDE.md docs.

**Blocks "France expansion":**
11. EX-014 re-introduce an i18n layer; EX-015 promote `Market` to own zone/locale/currency *(P2)*.

---

### Open Questions for the User (couldn't resolve from source alone)
1. **What is the deployed container's timezone** (`TZ`/`user.timezone`)? Confirms EX-009 severity.
2. **Is there a canonical `RG-A…RG-H` business-rule list?** `business-rules-audit.md` is prose; the
   RG-ID table (T1.1) needs the source-of-truth IDs to be completed.
3. **Is FR-only the committed product decision, or is France/Arabic on the near roadmap?** Determines
   whether EX-007/EX-014 is "remove the switcher" (S) or "build i18n now" (L).
4. Several tracks (T5 profiling, T6 device UX, T11.4 WCAG, T12 ops) need a **running environment** —
   want me to drive a build/EXPLAIN session next, or keep this read-only?

---

## SAFE STOP POINT — AUDIT COMPLETE
RESUME FROM: (none — all 13 tracks streamed) | findings: 16 new (EX-001…EX-016) + 29 DA cross-refs
Deferred deep-dives flagged per track require runtime/profiling/ops access (noted inline).
