# YEM HLM — Cross-Functional Product Review (Living Tracker)

> **Review date:** 2026-06-12 · **Branch reviewed:** `Epic/Dashboard-UIUX-improvement`
> **Team:** Sara (Design) · Karim (UX/Flows) · Leila (Security) · Adam (Full-Stack) ·
> Nadia (Legal Maroc) · Youssef (Docs/Onboarding) · Mehdi (Group Owner)
>
> **HOW TO USE THIS FILE**
> Every finding has a `Status:` line. When you fix one, change `🔲 OPEN` to
> `✅ RESOLVED (YYYY-MM-DD, commit/PR)` — do not delete the finding. Add new
> findings at the bottom of the registry with the next number. The summary
> table below should be kept in sync.
>
> **STATUS SUMMARY (update on every change)**
> | Severity | Total | Open | Resolved |
> |---|---|---|---|
> | 🔴 Critical | 1 | 0 | 1 |
> | 🟠 Major | 10 | 8 | 2 |
> | 🟡 Minor | 16 | 15 | 1 |
> | 🔵 Polish | 7 | 7 | 0 |
> | **Total** | **34** | **30** | **4** |

---

## EXECUTIVE SUMMARY — Karim, to the founder

You have built something rarer than you think: a CRM whose *legal core is real*. The VEFA
state machine is enforced server-side with proper 409/422 envelopes, the 5% deposit cap and
7-day rétractation are actual guards we triggered, the double-vente race is closed by a
partial unique index, and tenant isolation (JWT `sid` + `requireSocieteId()` ×280 + RLS) is
the best I've audited in this market segment. The visual identity is intentional — warm sand,
a semantic status-color system shared by badges, the 2D plan and the 3D viewer — and the
project-generation wizard turns a blank screen into 48 sellable units in under half an hour.
The portal speaks plain French to a 55-year-old buyer. These are not table stakes; most
competitors have none of them.

Three things must be fixed before any paying client sees this. **One:** the Excel gap —
there is no contact import and no group view, so your two highest-value moments (onboarding
a promoteur's existing book, and the multi-société owner's 9am check) both end in Excel,
and Mehdi told you what happens next. **Two:** the invisible flagship — the entire 3D module
(Wave 13, your best demo asset) is reachable only by typing a URL; no screen links to it.
**Three:** the legal paperwork around the legal engine — no privacy policy in the portal,
no CNDP evidence, no refund tracking after a rétractation your own guide promises. Nadia's
words: "the engine is compliant, the envelope is not."

The unrealized opportunity is consolidation. Every number a group owner needs already exists
per-société; nobody has written the aggregation layer. Two focused sprints — Vue Groupe +
CSV import + monthly cash curve + the 3D entry points — turn this from "a good single-société
CRM" into the only product in Morocco a multi-company promoteur can run his mornings on.
That's the moat. Build the floor above the plumbing.

### MEHDI'S CLOSING STATEMENT

To the founders: you got the hard part right and the obvious part wrong, and you should know
that's the better mistake. The hard part — the law, one login for my three companies, a
pipeline my agents can't break — is built, and I've paid for two CRMs that never managed it.
The obvious part is that I run a *group*, and your product makes me feel like three strangers
sharing a password. Before I spend serious money: give me one screen with my three sociétés,
my cash by month, and a way to load 600 contacts from Excel without my assistant resigning.
And until you answer it, the question I will ask in every sales call is the one I ask you
now: **"Combien de cash j'attends en octobre, toutes sociétés confondues — montre-le-moi
sans changer d'écran."**

---

## PRIORITY ACTION LIST (top 10, in order)

| # | Finding | Owner domain | Effort | Blocks client demo? |
|---|---------|--------------|--------|---------------------|
| 1 | #015 — Wire entry links to the 3D viewer/dashboard | Karim (UX) | XS | **YES** — flagship invisible |
| 2 | #010 — Load the Inter font (it's declared, never shipped) | Sara (Design) | XS | Yes (perceived quality) |
| 3 | #002 — Contact CSV/Excel import — ✅ done 2026-06-12 | Mehdi (Business) | M | **YES** — first demo question |
| 4 | #003 — Month-by-month cash forecast in trésorerie — ✅ done 2026-06-12 | Mehdi/Adam | S | Yes (owner demo) |
| 5 | #025 — Privacy policy + mentions légales in portal | Nadia (Legal) | S | **YES** — legal exposure |
| 6 | #004 — "Deactivate everywhere" for multi-société users — ✅ done 2026-06-12 | Leila (Security) | S | No (but security hole) |
| 7 | #001 — Vue Groupe (consolidated owner dashboard) — ✅ done 2026-06-12 | Mehdi (Business) | L | Yes for group prospects |
| 8 | #028 — Refund (remboursement) tracking after rétractation | Nadia (Legal) | M | No, but pre-GA |
| 9 | #016 — Resolve Contracts-vs-Ventes dual concept in nav | Karim (UX) | M | Yes (agent confusion) |
| 10 | #023 — Paginate list endpoints (List→Page) | Adam (Code) | L | No (load risk at scale) |

---
---

# SECTION REVIEWS

## SECTION 1 · FIRST IMPRESSIONS & VISUAL IDENTITY — led by Sara

**The 30-second verdict: designed, not assembled.** Warm-sand surface palette
(`#faf6ec`/`#fdfbf5`, warm browns) with a confident green primary — the default for this
stack is cool slate, and `styles.css:9-19` explicitly documents overriding it. Closer to
Basecamp/Hey warmth than the HubSpot-clone aesthetic of most PropTech CRMs; right for an
audience that distrusts startup toys. The zellige SVG on the dashboard hero is culturally
literate without being folkloric.

**Strongest visual element:** the status token system (`styles.css:40-68`) — each lifecycle
state (Disponible/Réservé/Vendu/Livré/Retiré/Brouillon) is a 4-token family shared by badges,
the 2D plan and the 3D viewer. One source of truth for the most important semantic colors in
the product. Iconography is coherent (hand-drawn 14×14 SVGs, 1.4 stroke). Craft below the
paint: skeletons with aria-labels, focus-visible, aria-invalid, real mobile drawer.

**Most embarrassing element:** a ghost. `--font: 'Inter', …` (`design-tokens.css:111`) and
Inter is **never loaded** — no @font-face, no package, no link. Every user gets Segoe
UI/Roboto; the typography layer ships unreviewed (#010). Token discipline is also eroding
from inside: hardcoded indigo gradients on hero cards (#012), 232 inline styles concentrated
on the money screens (#013), and the dashboard CSS got the budget raised instead of being
split (#014). i18n is split-brain: 31 templates translated, 14 hardcoded French including
the flagship dashboard (#011).

Findings: **#010, #011, #012, #013, #014** (see registry).

---

## SECTION 2 · NAVIGATION & INFORMATION ARCHITECTURE — led by Karim

**Persona paths, measured against `app.routes.ts` + `shell.component.html`:**

- **Manager → today's pipeline:** login lands on `/app/dashboard` (default route). Zero
  clicks. Hero "month at a glance" answers the question. This path is genuinely right.
- **Agent → create a Vente:** sidebar "Pipeline Ventes" → list → "new" (3 clicks), or from
  reservation detail / building-view unit card (prefilled). Good — multiple natural origins.
- **Acquéreur → documents:** portal defaults to the Ventes tab; documents live inside the
  vente card. Acceptable; one level deeper than ideal for an elderly user.
- **Admin → upload a 3D model:** **impossible without typing a URL.** The routes
  `/app/projets/:id/viewer-3d` and `/app/dashboard/commercial/3d` exist, but a repo-wide
  grep found **zero** `routerLink` pointing at them — not from project detail, not from the
  dashboard, not from the sidebar. Wave 13 (the best demo asset in the product) is an
  orphaned island (#015).

**IA problems.** The sidebar shows both **"Contrats"** and **"Pipeline Ventes"** — the legacy
SaleContract module and the VEFA Vente pipeline as sibling menu items. A new agent cannot
know which one their company sells with; this is two mental models of the same business event
(#016). "Messages" (the outbox admin tool) and "Notifications" sit under the **Analytics**
section label, which is simply the wrong drawer (#017). And routes mix languages —
`/app/projects` vs `/app/projets/:id/viewer-3d` (#018). Sara adds: ~16 flat items is at the
ceiling of what an icon-rail sidebar can carry; the section labels are doing real work, but
one more wave of features breaks it.

Findings: **#015, #016, #017, #018**.

---

## SECTION 3 · FORMS & DATA ENTRY — led by Sara & Karim

Better than expected. `vente-create` is the model citizen: required asterisk on the price,
explicit "optionnel" hints on dates, inline actionable errors ("Le prix doit être supérieur
à 0."), contextual help ("Date de signature du compromis de vente"), a live computed
"Solde restant dû" hint, and `data-testid` everywhere. Login has proper autocomplete
attributes, aria-invalid wiring, a password-reveal toggle, and `(ngSubmit)` so Enter works.
`LOCALE_ID` is correctly `fr` with `registerLocaleData(localeFr)` — `| number` renders
French-style grouping, and the dashboard's `formatAmountShort()` does fr-FR + "M MAD"
correctly. Contact form marks Prénom/Nom required. Co-acquéreur and dossier-financement
forms exist in vente-detail (the Wave 12 backend is not UI-orphaned).

Two real gaps. **Leila:** a negative or zero `prixVente` posted directly to the API is not
rejected — `VenteService` (lines ~157/190) silently falls back to the computed/property
price when the submitted price isn't `> 0`. Silent correction of money input is worse than a
422; the client believes their number was accepted (#019). **Sara:** price entry is a raw
`type="number"` — no live thousands grouping while typing 850000, and `inputmode` appears
twice in the whole app; on mobile most fields are saved by `type=` but money fields deserve
a masked MAD input (#020).

Findings: **#019, #020**.

---

## SECTION 3B · MEHDI'S FUNCTIONAL REVIEW — THE OWNER'S LENS

*(Full session recorded 2026-06-12; condensed here.)*

**Morning ritual:** one login for three companies works — the partial-token →
`/auth/switch-societe` flow (`AuthService.java:176-241`) is genuinely well built. But every
dashboard starts with `requireSocieteId()`: there is **no consolidated view of any kind**.
The owner is the aggregation engine (#001). The trésorerie alerts (overdue calls with buyer
name and days late) have the right instincts — for one société, invisible from the others
(#009).

**Isolation vs consolidation:** Leila signs off on isolation (JWT single `sid`, membership
re-check on switch, RLS underneath, cross-société → 404). Consolidation: none of the five
owner needs (group revenue, best project, group stock, monthly cash, stalled pipelines)
exists. A repeat buyer across two sociétés is two unlinked strangers — Nadia notes any fix
must be consent-based under Loi 09-08 (#005).

**User management:** per-société roles via `AppUserSociete` natively support MANAGER@Atlas +
AGENT@Riad — correct architecture. No finance-only/read-only role for the accountant. No
single-action group off-boarding: deactivation is per-membership, and a forgotten membership
is live access for an ex-employee (#004). Audit log (`/api/audit/commercial`) covers
deposits/contracts/payments/reservations/users/GDPR — good breadth — but no read/view events
(#008).

**Trois Projets test: 0.5 / 5.**
Q1 group revenue ACTE+: per-société yes, group no. Q2 at-risk project: per-société partial,
comparative no. Q3 cash in October: fails even per-société — `previsionnel6Mois` is one
aggregate number, no monthly curve (#003). Q4 open réserves Agadir + responsible: per-vente
endpoint only, and `ReserveLivraison` has no responsible field (#007). Q5 TVA by rate: data
model has `prix_ht`/`tva_taux` (changeset 083) but the ventes CSV export has no HT/TVA/TTC
columns (#006).

**Day one:** sociétés are SUPER_ADMIN-provisioned (vendor-led onboarding — defensible, but
the product has no notion the three belong together). The 5-step project wizard is the one
place the product clearly respects an owner's time — blank screen to 48 sellable units in
<30 min. **No contact import of any kind** — the first question of every demo currently has
the answer "you don't" (#002).

**Verdict (verbatim):** *"If I owned one company I'd consider it — the VEFA pipeline is the
most legally serious I've seen in a Moroccan product. I own three, and within a month I'd
have GROUPE.xlsx open next to it every morning, and the moment that sheet exists the platform
has lost. Build the Vue Groupe. The plumbing exists. Build the floor above it."*

Findings: **#001–#009**.

---

## SECTION 4 · 3D VIEWER & UPLOADER — Sara/Karim (UX), Adam (tech)

**Viewer.** The loader emits real progress events (`model-loader.service.ts` — loaded/total/
ratio), so loading communicates rather than spins. Status colors come from the shared token
system — an agent who knows the badges reads the 3D for free. Engine hygiene is confirmed by
the earlier audit: full dispose on destroy, DPR cap 1.5, Page-Visibility pause — Adam has no
load concerns. Keyboard Tab navigation across units exists, which is more accessibility than
most WebGL demos ever get. Two gaps: the unit tooltip is **hover-only** with no touch
handling anywhere in the viewer CSS — on the iPad an agent brings to a client meeting, the
information layer mostly disappears (#022). Presentation mode exists (Wave 12 P4) and floor
filtering is in — good instincts, untested here on mobile.

**Uploader.** The two-step pre-signed flow is honestly surfaced as a progress bar, the error
state has a real "Réessayer" reset, and `.glb`/50 Mo constraints are stated up front. But the
constraint line reads **"Draco obligatoire · Max 50 Mo"** with zero explanation — an admin
who doesn't know what Draco is (all of them) gets a hard stop with no path forward. One
sentence and a link fixes it (#021). **Genuinely good:** the mesh-mapping admin is far more
usable than expected — auto-pair with an undo, a mapped-count chip (`12/48 mappés`), dirty-
state save. A motivated non-developer can do this job, which I would not have bet on.

Findings: **#021, #022**.

---

## SECTION 5 · DASHBOARD & REPORTING — Karim (UX), Adam (perf)

**The 30-second test: passes 2 of 3.** Units/CA this month — instant (hero card with target
progress). Cash next 30 days — partially: trésorerie shows totals and overdue alerts, but
the monthly curve doesn't exist (#003). Underperforming agent — fails: the leaderboard is a
**top-5 podium**; the manager's actual daily question ("who is struggling?") needs the bottom
of the table, which is never shown (#024). KPI cards are consistent; skeleton loaders match
the loaded layout (no jump); empty states are intentional with CTAs (Wave 12 D). Numbers are
correctly French-formatted MAD throughout — `LOCALE_ID 'fr'`, `formatAmountShort` does
"1,2 M MAD" — no EUR/USD leakage anywhere we looked.

**Adam, on load:** the structural risk is unpaginated list endpoints — `GET /api/ventes`,
`GET /api/properties`, contacts and others return raw `List<...>` (confirmed in the three
controllers). A société with 2 000 contacts or 800 ventes serializes everything on every
list view. This is audit item F-006, deferred because List→Page breaks FE contracts — it's
real work, and it's the right work before the first big client (#023). Dashboard query
budget itself is disciplined (documented 16-query budget, Caffeine caches with sane TTLs).

Findings: **#023, #024** (plus #003 from 3B).

---

## SECTION 6 · PORTAIL ACQUÉREUR — Youssef lead, Leila on exposure

Reviewed as the 55-year-old Casablanca buyer. The magic-link landing is a dedicated
single-purpose page (`portal-verify`): spinner → redirect, and on failure a **translated,
friendly error with a "Demander un nouveau lien" recovery path** — exactly right for this
persona. The default tab is Ventes (his apartment, his pipeline position via the same
stepper the CRM uses — nice continuity). The échéancier speaks human French: statuses render
as "En attente / Payée / En retard" (`echLabel()`), never `EN_ATTENTE`; paid/remaining
totals are computed and shown. Documents live inside the vente card — findable, one level
deep.

**Leila:** portal auth is a httpOnly cookie scoped to `/api/portal` (`hlm_portal_auth`),
token never in localStorage; portal queries are scoped (societeId, contactId) with
cross-contact access → 404; the 3D portal controller 404s unless the contact has a vente on
a mapped property. Other buyers' prices are not in the responses. Token: 32-byte
SecureRandom, SHA-256 stored, 48h TTL, one-time use. This surface is clean.

**Nadia:** and yet the portal — which collects and displays CIN-linked financial data to
consumers — contains **no privacy policy, no mentions légales, no CNDP notice anywhere**
(zero hits in portal templates). Under Loi 09-08 the buyer has a right to be informed of
the processing, the responsable de traitement, and his access/rectification rights. For a
consumer-facing surface this is not polish, it's exposure (#025).

Findings: **#025**.

---

## SECTION 7 · SECURITY REVIEW — Leila lead

The fundamentals are strong, and I say that rarely. **JWT in httpOnly cookie** (no
localStorage anywhere); expired session → interceptor catches 401 and routes to login
cleanly. **Tenant isolation** is defense-in-depth: `requireSocieteId()` ×280 at service
layer, `societe_id` on every query, PostgreSQL RLS phase 2 on all domain tables as backstop,
ThreadLocal propagated across @Async. Role enforcement: `/api/admin/**` hard-gated
SUPER_ADMIN, method-level @PreAuthorize on sensitive ops; the earlier audit's spot-checks
(cross-société 404, agent→admin 403/401 ITs) hold. **Uploads:** GLB files are validated by
magic bytes + glTF version + Draco extension (GlbValidator → 422), not extension. **Infra:**
R2 object keys never reach the client — pre-signed GET/PUT with TTL only; CORS origins are
env-driven (fails closed to localhost); login rate limiting + lockout exists, invitation
rate limiting too; no hardcoded secrets found (JWT_SECRET fails fast via @NotBlank).

What's left is paper and edge: the **CNDP declaration** for processing CIN/financial data
is an organizational act the codebase can't prove — it must be evidenced before GA, and the
GDPR module (anonymization, consent events) suggests the team knows it (#026). **Retention**
is undefined: deletion is soft/anonymization (F-012 deferred as design decision) with no
written policy on how long ventes/CIN data live after a relationship ends (#027). MRE /
Office des Changes handling of foreign transfers: out of scope of the code today, flag for
the legal backlog — Nadia concurs. Input validation server-side is broadly present
(jakarta validation + service guards), with the silent price-correction exception already
logged as #019.

Findings: **#026, #027** (plus #004, #019, #025 logged elsewhere).

---

## SECTION 8 · FUNCTIONAL & BUSINESS RULES — Karim lead, Nadia on legal

**What we verified actually enforces (say it once, it's impressive):** deposit > 5% → 422
`VIOLATION_LEGALE` (Art. 618-4); confirmation opens `EN_RETRACTATION` with the 7-day window,
out-of-window rétractation → 409 `RETRACTATION_IMPOSSIBLE`, in-window cancels and frees the
property; the legal échéancier generates the 7 staged calls with per-stage caps and a
cumul ≤ prix guard (Art. 618-17); double-vente race is closed both in code (RG-B03 →
409 `PROPERTY_ALREADY_ENGAGED`) and in the database (partial unique index, changeset 075);
schedulers expire options and close rétractation windows without human action. Co-acquéreur
identity (CIN/passeport/régime) is captured for the notaire. Magic-link forwarding risk is
mitigated as well as the pattern allows (48h, one-time, SHA-256 at rest).

**Where the law outruns the code. Nadia:** the manager guide itself says, on rétractation,
"le dépôt à rembourser" — and the platform has **no refund concept at all** (zero
`rembours*` hits in the backend). Who refunded, when, how much, proof — untracked. In a
CNDP-adjacent dispute or a tribunal, "we cancelled the row" is not an answer (#028).
`penaliteRetardJournalier` is captured on the property (ch. 083) but never computed against
an exceeded delivery date nor displayed on any document — a stored number that does nothing
(#029). And the generated PDFs (contrat de réservation, PV de livraison) have not been
audited line-by-line against the mentions obligatoires of Loi 44-00 — that review must
happen with a notaire before a real buyer signs one (#031).

**Edge cases. Karim:** annulation after an appel de fonds was issued but not paid — the
state machine permits it, but no test or code path defines what happens to the outstanding
échéances; behavior is currently *undefined by omission*, and the VEFA IT suite that would
catch it is deferred to CI (#030). Property/Project deletion orphaning is largely moot —
deletes are restricted in practice and ANNULE releases inventory correctly
(`VenteService:567-574`).

Findings: **#028, #029, #030, #031**.

---

## SECTION 9 · CODE QUALITY & DEAD CODE — Adam lead

Sweeps run repo-wide today, so these are counts, not impressions. **Clean:** zero `*ngIf`/
`*ngFor` remaining (the F-007 migration finished the job); 3D scene disposal verified;
absorption has one canonical formula shared FE/BE; TTC is never stored (TvaCalculator
discipline held); Contact.statut mutations flow through the vente event path; the old
payment-v1 package is genuinely deleted, not commented out.

**To remove or fix:** 89 components still import `CommonModule` they no longer need —
dead weight in every standalone import list (#032). Stale documentation lies in wait:
`VenteService.java:53` javadoc still describes the **pre-VEFA state machine**
(`COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE`) that Wave 12 replaced, and ACTE_NOTARIE
ghosts linger in comments/DTO names (`ShareholderKpiDTO`, `HomeDashboardDTO`) — the next
engineer will trust the comment and ship a bug (#033). `takeUntilDestroyed` appears only
7 times, but the earlier audit's subscription review (F-008) stands: the persistent streams
are cleaned up; the rest are one-shot HttpClient — no leak hunt needed. The two structural
items already logged: unpaginated `List<>` endpoints (#023) and the inline-style creep
(#013). One Angular note: there are no rogue `fetch()`/XHR calls bypassing interceptors —
checked.

Findings: **#032, #033** (plus #013, #023 logged elsewhere).

---

## SECTION 10 · DOCUMENTATION & BEGINNER GUIDES — Youssef lead

Coverage surprised me: eleven user guides (`docs/guides/user/` — getting-started, contacts,
sales-pipeline, properties, dashboard, 3D, portal, notifications, GDPR rights, admin,
overview) plus the Loi 44-00 manager guide. Most products at this stage have a README and
a prayer.

The **legal guide is genuinely good where it counts**: it explains *quoi/comment/pourquoi*
in plain French, states the 5% cap with its article number, walks the rétractation outcomes
including what happens when the client does nothing, and maps each rule to the error the
manager will actually see ("un dépôt supérieur est refusé — 422"). It answers Nadia's bar:
a manager reading it understands *why* the 7-day window matters, not just that it exists.

Where it slips: engineer voice leaks through. A manager guide has no business containing
`option_expire_at`, `VenteService.validateTransition`, "scheduler horaire" or `MARKET_CODE`
env-var references — my mother does not know what a scheduler is, and the manager in Agadir
doesn't either. Each of these has a one-line plain-French replacement ("la plateforme annule
l'option automatiquement à l'heure d'expiration") (#034). The same pass should confirm every
amount says "MAD" and every date format is named explicitly (DD/MM/YYYY) — minor, same
sweep. The refund question ("qui rembourse le dépôt, sous quel délai") is asked by the guide
and answered by no one — that's #028's documentation shadow.



Findings: **#034**.

---
---

# FULL FINDINGS REGISTRY

Sorted by severity. Update `Status:` in place when resolving.

## 🔴 Critical

**FINDING #001** — Functional — Mehdi — `Status: ✅ RESOLVED (2026-06-12, Vue Groupe)`
No consolidated group view: every dashboard/KPI/alert is single-société; multi-company
owners must switch contexts and aggregate by hand.
*Why:* group owners (highest-value segment) keep a parallel Excel and churn.
*Fix:* "Vue Groupe" — cross-société read endpoint keyed on user memberships (revenue, stock,
cash, alerts per société + totals) behind an explicit group-owner role.
*Effort:* L · *Business impact:* Retention
*Resolution:* `GET /api/groupe/dashboard` (ADMIN only) — `GroupDashboardService` verifies
each `AppUserSociete` ADMIN membership, then summarizes one société at a time by switching
`SocieteContext` around `GroupSocieteSummarizer` (own `@Transactional` boundary so
`RlsContextAspect` sets the matching RLS variable per société; context restored in
`finally`). Per-société rows: stock + canonical absorption, CA confirmé (ACTE+), pipeline
CA, cash from the échéancier, overdue amount/count, options actives, rétractations, ventes
bloquées 30 j+ — plus group totals, sorted best-performer first. Frontend: `/app/groupe`
(adminGuard) `VueGroupeComponent` (totals KPI row, alert strip, comparison table) + "Vue
Groupe" sidebar link (Analytics, admins only). Tests: `GroupDashboardServiceTest` ×6
(ADMIN-only gating, 403 without ADMIN membership, inactive société skipped, totals + sort,
context switch/restore incl. on exception); unit suite 160 green; FE prod build green.
MANAGER/AGENT memberships deliberately excluded (owner-level financials).

## 🟠 Major

**FINDING #002** — Functional — Mehdi — `Status: ✅ RESOLVED (2026-06-12, CSV import)`
No contact import (CSV/Excel) anywhere; onboarding an existing book is manual entry.
*Why:* first question of every sales demo; blocks Excel migration (= the whole market).
*Fix:* `POST /api/contacts/import` (multipart CSV), column-mapping preview, dedup on
phone/email, error report. *Effort:* M · *Impact:* Growth
*Resolution:* `POST /api/contacts/import` (ADMIN/MANAGER, multipart, `dryRun` param) —
`ContactImportService` parses UTF-8 CSV (`;`/`,` auto-detect, quoted fields, BOM, FR/EN
headers with accents ignored; max 2000 rows), validates each row with the same bean
constraints as the form, dedups on email + phone (in-file and against the société), then
routes every valid row through `ContactService.create()` so quota, e-mail uniqueness, the
Loi 09-08 consent/basis guard, timeline and audit apply identically. Best-effort per row
(one bad line never rolls back the rest; re-importing the same file is idempotent).
Report: created/duplicates/errors with 1-based line numbers, capped at 100 issues.
Frontend: "Importer" button on the contacts page → 3-step dialog (file + base juridique
Loi 09-08 → dry-run preview → confirmed import with final report) + downloadable CSV
template. Tests: `ContactImportServiceTest` ×8 (delimiters/quotes/accents, phone-or-email
rule, in-file + DB dedup, dry-run never persists, missing columns 422, consent guard,
per-row quota failure, ignored columns); unit suite 168 green; FE prod build green.

**FINDING #003** — Functional — Mehdi/Adam — `Status: ✅ RESOLVED (2026-06-12, monthly cash forecast)`
Cash forecast is a single 6-month aggregate (`previsionnel6Mois`); no monthly breakdown —
"how much in October?" unanswerable.
*Fix:* group `vente_echeance` by month in `TresorerieDashboardService`; return
`List<MoisPrevision>`; 6-bar timeline in UI. *Effort:* S · *Impact:* Operations
*Resolution:* New `VenteEcheanceRepository.sumUnpaidByMonth(societeId, from, to)` groups
unpaid échéances by `EXTRACT(YEAR/MONTH ...)`. `TresorerieDashboardService.buildMonthlyForecast()`
builds 6 calendar-month buckets (current month first), window starting at `today` so overdue
amounts already shown as "en retard" aren't double-counted, zero-filling months with no
échéance. `TresorerieDashboardDTO` gains `List<MoisPrevision> previsionnelParMois`
(annee/mois/libelle/montant); `previsionnel6Mois` retained for the headline KPI. Frontend:
6-bar timeline ("Encaissements prévus par mois") on the trésorerie dashboard, current month
highlighted green, K-MAD labels. Tests: `TresorerieDashboardServiceTest` +1 (6 buckets,
chronological order, gap zero-filling, non-blank labels) — existing test updated for the new
constructor arg; unit suite 169 green; FE prod build green.
*Note:* this single-société monthly curve also feeds the group case — Vue Groupe (#001) can
surface a consolidated monthly view on top of it as a later enhancement.

**FINDING #004** — Security — Leila — `Status: ✅ RESOLVED (2026-06-12, Désactiver partout)`
No single-action cross-société user deactivation; off-boarding is per-membership and
forgettable.
*Fix:* "Désactiver partout": set `actif=false` on all `AppUserSociete` rows + bump
`tokenVersion` in one call. *Effort:* S · *Impact:* Operations/Security
*Resolution:* `POST /api/users/{id}/deactivate-everywhere` (ADMIN) →
`AdminUserService.deactivateEverywhere()`: deactivates every active `AppUserSociete`
membership (with `dateRetrait`/`raisonRetrait`/`retirePar` retrait metadata), disables the
global account, bumps `tokenVersion` and evicts the security cache so all live JWTs are
rejected immediately; one audit `OFFBOARDED_ALL` event per société. **Authorization:** the
acting admin must be ADMIN in at least one société shared with the target (else 403), and
cannot off-board themselves (403). Frontend: "Désactiver partout" button on the admin-users
row (active members only) with a departure-confirmation + optional motif; success toast
reports the société count. Tests: `AdminUserServiceTest` ×6 (happy path multi-société +
audit per société, 403 no-shared-admin, 403 actor-only-MANAGER, 403 self, 404 unknown,
already-inactive not re-counted); unit suite 175 green; FE prod build green.

**FINDING #005** — Functional — Mehdi (Nadia framing) — `Status: 🔲 OPEN`
Same buyer in two sociétés = two unlinked contacts; no group-level client identity.
*Fix:* opt-in consent-based linking (Loi 09-08), match on CIN, link don't merge.
*Effort:* M · *Impact:* Revenue

**FINDING #010** — UI/UX — Sara — `Status: 🔲 OPEN`
Brand font Inter declared (`design-tokens.css:111`) but never loaded — all users get OS
fallback; typography ships unreviewed.
*Fix:* `@fontsource-variable/inter`, import in styles.css, font-display swap, re-check
dense tables. *Effort:* XS

**FINDING #015** — UI/UX — Karim — `Status: 🔲 OPEN`
3D viewer (`/app/projets/:id/viewer-3d`) and 3D dashboard (`/app/dashboard/commercial/3d`)
have **zero inbound links** in any template — the flagship Wave 13 feature is URL-only.
*Fix:* "Visualiseur 3D" tab/button on project-detail (gate on model presence or
canManageModel) + card link from commercial dashboard. *Effort:* XS

**FINDING #016** — UI/UX — Karim — `Status: 🔲 OPEN`
Sidebar offers both "Contrats" (legacy SaleContract module) and "Pipeline Ventes" (VEFA) as
parallel concepts; new agents can't know which to use.
*Fix:* decide the contract module's fate — hide behind feature flag for legacy sociétés, or
fold payment schedules under the vente; one selling concept in nav. *Effort:* M

**FINDING #023** — Code — Adam — `Status: 🔲 OPEN`
List endpoints return unpaginated `List<>` (`GET /api/ventes`, `/api/properties`, contact
lists; audit F-006) — full serialization per request.
*Fix:* coordinated List→Page migration (BE `Page<T>` + FE flat `PageResponse` pattern from
Wave 12-A), starting with contacts/ventes. *Effort:* L

**FINDING #025** — Legal — Nadia — `Status: 🔲 OPEN`
Portal (consumer-facing, CIN-linked financial data) has no privacy policy, mentions légales
or CNDP notice — zero hits in portal templates.
*Why:* Loi 09-08 right-to-information violation on a consumer surface; embarrassing in any
procurement review.
*Fix:* footer links to politique de confidentialité + mentions légales (static pages),
consent line on magic-link request. *Effort:* S

**FINDING #028** — Legal — Nadia — `Status: 🔲 OPEN`
No refund (remboursement) tracking: after rétractation/annulation the deposit's return is
untracked (no field, no event, no document) though the manager guide promises it.
*Fix:* `remboursement` record on vente (montant, date, moyen, statut) + audit event +
mention on the cancellation PV. *Effort:* M

## 🟡 Minor

**FINDING #006** — Functional — Mehdi/Adam — `Status: 🔲 OPEN`
Ventes CSV/PDF export lacks HT/TVA-rate/TTC columns though `prix_ht`/`tva_taux` exist
(ch. 083). *Fix:* add columns via TvaCalculator + per-rate subtotal block. *Effort:* S

**FINDING #007** — Functional — Mehdi — `Status: 🔲 OPEN`
Réserves de livraison: per-vente listing only; `ReserveLivraison` has no responsible field.
*Fix:* `GET /api/projects/{id}/reserves` + nullable `responsable_user_id` (ch. 084).
*Effort:* M

**FINDING #008** — Functional — Mehdi/Leila — `Status: 🔲 OPEN`
Audit log records writes only — no read/view events for sensitive data.
*Fix:* async read-audit on sensitive GETs (contact legal, pricing), sampled. *Effort:* M

**FINDING #009** — Functional — Mehdi — `Status: 🔲 OPEN`
No owner alert digest; problems in société B invisible while logged into A.
*Fix:* daily/weekly email digest per membership from existing audit + trésorerie alert
data. *Effort:* M

**FINDING #011** — UI/UX — Youssef/Sara — `Status: 🔲 OPEN`
i18n split-brain: 31 templates translated, 14 hardcoded French incl. home dashboard.
*Fix:* decide FR-only vs i18n; if i18n, sweep the 14 starting with the dashboard.
*Effort:* M

**FINDING #012** — UI/UX — Sara — `Status: 🔲 OPEN`
Off-palette hardcoded colors: indigo hero gradients (`home-dashboard.component.css:326-329`),
`stroke="#16a34a"` in shell SVG.
*Fix:* replace with `--c-primary-*`/status tokens; currentColor in inline SVGs. *Effort:* XS

**FINDING #017** — UI/UX — Karim — `Status: 🔲 OPEN`
Nav misgrouping: Messages (outbox admin) + Notifications under "Analytics"; sidebar at ~16
flat items. *Fix:* regroup (Communication section), consider role-based pruning. *Effort:* S

**FINDING #019** — Security — Leila — `Status: 🔲 OPEN`
Negative/zero `prixVente` posted to API is silently replaced by computed/property price
(`VenteService` ~157/190) instead of 422.
*Fix:* explicit validation error on submitted non-positive price (keep null = computed).
*Effort:* XS

**FINDING #021** — UI/UX — Youssef — `Status: 🔲 OPEN`
Uploader says "Draco obligatoire · Max 50 Mo" with no explanation of what Draco is or how
to produce it. *Fix:* one-line hint + docs link ("Exportez depuis Blender avec compression
Draco activée — voir guide"). *Effort:* XS

**FINDING #022** — UI/UX — Karim/Sara — `Status: 🔲 OPEN`
3D viewer tooltips are hover-only; no touch/pointer handling found in viewer styles — info
layer degraded on tablets used in client meetings.
*Fix:* tap-to-select shows the tooltip panel pinned; test on iPad Safari. *Effort:* S

**FINDING #024** — UI/UX — Karim — `Status: 🔲 OPEN`
Leaderboard is top-5 only; "which agent is underperforming" — the manager's daily question —
is unanswerable. *Fix:* full ranked table behind the podium, or a "bottom 3 / no sales 14d"
strip. *Effort:* XS

**FINDING #026** — Legal — Nadia — `Status: 🔲 OPEN`
CNDP declaration for CIN/financial processing not evidenced anywhere (org-level act).
*Fix:* file/confirm CNDP declaration; record number in mentions légales (#025). *Effort:* S
(org)

**FINDING #027** — Legal — Nadia — `Status: 🔲 OPEN`
No written data-retention policy; deletion is soft/anonymization (F-012 deferred) with
undefined lifetimes for CIN/financial data.
*Fix:* define retention matrix (prospect vs acquéreur vs vente légale), document, wire to
GDPR module purge. *Effort:* M

**FINDING #029** — Legal — Nadia — `Status: 🔲 OPEN`
`penaliteRetardJournalier` captured (ch. 083) but never computed against exceeded delivery
dates nor displayed on documents.
*Fix:* compute when tranche `dateLivraisonPrevue` exceeded; show on vente + PV; alert.
*Effort:* M

**FINDING #030** — Functional — Karim — `Status: 🔲 OPEN`
Annulation after an appel de fonds issued-but-unpaid: behavior of outstanding échéances is
undefined by omission; VEFA IT suite deferred to CI doesn't cover it.
*Fix:* define + test: cancel voids pending échéances (statut ANNULEE), excluded from
receivables/trésorerie. *Effort:* S

**FINDING #031** — Legal — Nadia — `Status: 🔲 OPEN`
Generated PDFs (contrat de réservation, PV livraison) not audited against Loi 44-00
mentions obligatoires by a legal professional.
*Fix:* notaire review of both templates; checklist in docs/legal; template fixes.
*Effort:* S (review) + follow-ups

**FINDING #034** — Functional/Docs — Youssef — `Status: 🔲 OPEN`
Manager legal guide leaks engineer identifiers (`option_expire_at`,
`VenteService.validateTransition`, "scheduler", `MARKET_CODE`).
*Fix:* plain-French pass; move technical refs to engineer guide; explicit DD/MM/YYYY and
"MAD" sweep. *Effort:* XS

## 🔵 Polish

**FINDING #013** — Code — Sara/Adam — `Status: 🔲 OPEN`
232 inline `style=""` attributes, worst on money screens (vente-detail 25, property-detail
21, dashboard 21). *Fix:* convert to utilities; ESLint ratchet. *Effort:* M

**FINDING #014** — Code — Adam — `Status: 🔲 OPEN`
`home-dashboard.component.css` = 1 656 lines; `anyComponentStyle` budget raised 16→20 kB to
fit. *Fix:* split into child components; restore 16 kB. *Effort:* S

**FINDING #018** — UI/UX — Adam — `Status: 🔲 OPEN`
Mixed-language routes: `/app/projects` vs `/app/projets/:id/viewer-3d`.
*Fix:* standardize (redirect alias for old links). *Effort:* XS

**FINDING #020** — UI/UX — Sara — `Status: 🔲 OPEN`
Money fields are raw `type="number"` — no live MAD thousands grouping while typing;
`inputmode` used twice app-wide. *Fix:* masked currency input component (1 250 000), reuse
across vente/property/commercial forms. *Effort:* S

**FINDING #032** — Code — Adam — `Status: 🔲 OPEN`
89 components import `CommonModule` unnecessarily (Angular 19 standalone + @if/@for).
*Fix:* sweep removal; lint rule. *Effort:* S

**FINDING #033** — Code — Adam — `Status: 🔲 OPEN`
Stale pre-VEFA state-machine javadoc (`VenteService.java:53`) + ACTE_NOTARIE ghosts in
comments/DTOs (`ShareholderKpiDTO`, `HomeDashboardDTO`).
*Fix:* update javadoc to W12 machine; rename/annotate ghosts. *Effort:* XS

---

*End of registry. Add new findings below this line with the next sequential number.*
