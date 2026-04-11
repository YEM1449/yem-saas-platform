# Implementation State — 2026-04-11 Multi-task Sprint

Resumable state for the 5-task implementation request:
1. Reservation→Sale workflow button
2. Tasks page stuck loading bug
3. Dashboard KPI expansion (real-estate owner)
4. Contract template drag-and-drop variable builder
5. Platform-wide UI/UX polish pass

## Commit Plan

| # | Commit | Status | Branch |
|---|---|---|---|
| A | Tasks page Page<T> fix + reservation→sale verify | pending | Epic/ProjectCreationUpgrade-TrancheImplementation |
| B | Dashboard KPI expansion (backend+frontend) | pending | same |
| C | Contract template drag-drop builder | pending | same |
| D | UI/UX polish pass (design tokens + top pages) | pending | same |
| E | Tests + docs + CLAUDE.md updates | pending | same |

## Findings

### Task 1 — Reservation→Sale (already wired)
- File: `hlm-frontend/src/app/features/reservations/reservation-detail.component.html:77-84`
- Button: "Démarrer la pipeline de vente" → `goConvertToVente()` → `/app/ventes/new?reservationId=`
- Backend: `GET /api/reservations/{id}/vente-prefill` (Wave 11 F7)
- Action: smoke-verify only.

### Task 2 — Tasks page stuck loading
- Root cause: `tasks.component.ts:65` reads `page.page.totalPages`. Spring `Page<T>` returns flat format `{content, totalPages, totalElements, ...}`. `page.page` is `undefined` → `TypeError` thrown inside `next` callback → `loading.set(false)` never reached → spinner spins forever.
- Same bug: `admin-users.component.ts:63-64`.
- Fix: update `TaskPage` and admin-users page interface to flat shape; update access expressions.

### Task 3 — Dashboard KPI expansion
- Existing: `HomeDashboardService` returns 30+ fields (pipeline counts, monthly trend, échéances, inventory, alerts).
- Missing for real-estate owner: cancellation rate, avg time-to-close, agent leaderboard, ROI per project, sell-through velocity, financial close rate, marge brute, stock value at risk, unsold-aging buckets, conversion funnel (lead→reservation→vente).
- Plan: extend `HomeDashboardService` (backend) + add 2 new sections to home-dashboard.component.html.

### Task 4 — Contract template builder
- Current: `ContractTemplate.htmlContent` raw HTML (Thymeleaf). Frontend likely a `<textarea>`.
- Plan: Angular standalone component `template-builder.component`:
  - Variable palette (left): groups (Vente, Acheteur, Bien, Société, Dates, Paiement)
  - WYSIWYG body (right): contenteditable div using `tiptap` or simple `execCommand` + custom token chips
  - Drag from palette → drops `${var.path}` token chip in body
  - Live preview tab (renders Thymeleaf-substitution-style with placeholder data)
- Backend: add `GET /api/templates/variables?type=...` to expose available variables per template type.

### Task 5 — UI/UX polish pass
Scope (intentionally bounded):
- Design tokens: spacing scale, colors, shadows in `_tokens.css` (already exists?) — audit and unify.
- Empty states: list pages need "No data yet" CTA (contacts, tasks, reservations, ventes, properties).
- Loading skeletons replace "Chargement..." spinners on top 5 pages.
- Mobile breakpoint pass on home dashboard, vente-detail, contact-detail.
- Consistent page headers across all features.

## Resume Instructions
1. Read this file first.
2. Check git log on `Epic/ProjectCreationUpgrade-TrancheImplementation` to see which commits already landed.
3. Continue from the first `pending` commit in the table above.
4. Update this file's table after each commit.
