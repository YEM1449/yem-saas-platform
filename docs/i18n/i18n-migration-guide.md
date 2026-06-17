# i18n Migration Guide (EX-014)

**Status:** Foundation implemented and live; string migration in progress.
**Date:** 2026-06-14
**Stack:** `@ngx-translate/core` v17 + `@ngx-translate/http-loader` v17 (runtime JSON catalogs, Angular 19 standalone).

This restores a real internationalization layer after Phase D removed ngx-translate (FR-only). France
(Loi SRU) and Arabic are now a **translation + migration** effort, not a rewrite — the architecture is in place.

---

## 1. Architecture (done — do not re-build)

| Piece | File | Role |
|---|---|---|
| Language registry | `src/app/core/i18n/i18n.config.ts` | `LANGUAGES` (fr/en/ar), default, RTL flag, `normalizeLang()` |
| Facade service | `src/app/core/i18n/i18n.service.ts` | `bootstrap()`, `setLanguage()`, `adoptUserPreference()`, `instant()`, `isRtl()`; owns `<html lang/dir>` + persistence + profile sync |
| Provider wiring | `src/app/app.config.ts` | `provideTranslateService({ loader: provideTranslateHttpLoader({prefix:'/i18n/', suffix:'.json'}), fallbackLang:'fr' })` + APP_INITIALIZER → `i18n.bootstrap()` |
| Switcher | `src/app/core/components/language-switcher.component.ts` | FR/EN/عربي; mounted in login + CRM shell footer |
| Catalogs | `public/i18n/{fr,en,ar}.json` | served at `/i18n/<lang>.json` |
| RTL baseline | `src/styles.css` (`[dir="rtl"]` block) | flips chrome layout for Arabic |
| Preference adoption | `src/app/core/auth/auth.service.ts` | on `/auth/me`, `adoptUserPreference(user.langueInterface)` |

**Language resolution order:** explicit device choice (`localStorage 'hlm_lang'`) → user profile `langueInterface`
→ default `fr`. Missing catalog keys fall back to French (`fallbackLang`), so a partially-migrated screen is
never blank — untranslated strings simply stay French until keyed.

---

## 2. Migrated so far (canonical patterns to copy)

- **Login** (`features/login/`) — full template, including dynamic `[attr.aria-label]` and ternary labels, plus the switcher.
- **CRM shell** (`features/shell/`) — nav sections, all rail items (label + `[title]`), bottom nav, topbar search, logout.
- **Shared catalog namespaces:** `common.*`, `auth.login.*`, `nav.*`, `lang.*` — in all three languages.

---

## 3. The keying playbook (mechanical, per component)

For each component:

1. **Templates (`.html`)** — replace each user-visible French string:
   - Text node: `Enregistrer` → `{{ 'common.actions.save' | translate }}`
   - Attribute: `title="Projets"` → `[title]="'nav.projects' | translate"`
   - Dynamic/aria with ternary: `[attr.aria-label]="(cond ? 'a.b' : 'a.c') | translate"`
   - With params: `{{ 'x.y' | translate:{ name: value } }}` (catalog: `"y": "Hello {{name}}"`).
2. **Add the pipe to the component** `imports: [..., TranslatePipe]` (from `@ngx-translate/core`).
3. **TypeScript strings** (toasts, computed labels): inject `I18nService` and use
   `this.i18n.instant('errors.http.500')` — do **not** hardcode.
4. **Add keys** to `public/i18n/fr.json` (source) and translate in `en.json` + `ar.json`.
   Use feature-namespaced dotted keys: `ventes.list.title`, `contacts.form.lastName`, `errors.http.409`.
5. **Specs:** any unit test that constructs a now-`TranslatePipe`/`I18nService`-dependent component must
   provide them — see `auth.service.spec.ts` for the stub pattern, or add `provideTranslateService()` (no loader)
   to the TestBed.

**Key naming convention:** `<feature>.<area>.<name>` (camelCase leaves). Reuse `common.*` for shared actions/fields.

---

## 4. Remaining inventory (re-key "everything")

Heuristic scan (2026-06-14): **57 / 62** HTML templates and ~**61** TS components still carry French
literals. Migrate feature-by-feature (each is independent; the build stays green throughout because of the
French fallback). Suggested order by surface area / visibility:

| Order | Area | Templates |
|---|---|---|
| 1 | `portal/features/*` (buyer-facing — highest external visibility) | 7 |
| 2 | `features/dashboard/*` | 6 |
| 3 | `features/ventes/*` (VEFA pipeline) | 4 |
| 4 | `features/projects/*` | 4 |
| 5 | `features/reports/*`, `features/contracts/*` | 3 + 3 |
| 6 | `features/{tasks,reservations,prospects,properties,contacts,admin-users}/*` | 2 each |
| 7 | `features/superadmin/*` | 5 |
| 8 | `core/components/*` (toasts, cards, dialogs — shared) | inline |

A controlled migration script (exact string → key) is appropriate for repetitive nav/label-heavy templates
— see the shell migration commit for the pattern (verify with `npm run build` after each feature).

---

## 4b. Migrated: the buyer portal (2026-06-14)

The entire `portal/features/*` surface is keyed under the `portal.*` namespace in all three languages —
shell (nav/logout/footer + switcher), login, verify, **ventes** (the flagship buyer screen, incl. the
échéancier table, contract upload, documents), payments redirect, contracts, and property. Status labels
that lived in TypeScript (`echLabel`, `statusLabel`) now resolve through `I18nService.instant('…')`.

**Exception — legal pages (`portal-legal`, `portal-privacy`) are intentionally FR-only.** Moroccan legal
notices (Loi 09-08 / 44-00, CNDP) must not be machine/dev-translated; EN/AR versions require
counsel-authored text and should be served as **per-language legal documents**, not per-paragraph keys.
This is documented in each component's header comment.

## 5. Definition of done (per language)

- [ ] All 57 remaining templates + TS literals keyed.
- [ ] `en.json` and `ar.json` reach key parity with `fr.json` (no fallback leakage in non-FR).
- [ ] RTL: audit feature layouts under `dir=rtl` beyond the chrome baseline (forms, tables, the 3D viewer).
- [ ] Legal/market constants: default language derived from the active `Market` (links EX-015 / DA-016 currency).
- [ ] A lint/CI check that flags new hardcoded user-facing strings (e.g. an ESLint rule or a string-scan gate).
