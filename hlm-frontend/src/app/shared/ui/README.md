# HLM UI — Shared Component Library

Standalone, typed, accessible Angular components that **wrap the existing design-token / global-CSS system** (`src/design-tokens.css`, `src/styles.css`). They render identically to current hand-written markup, so adoption is incremental and visually safe — replace ad-hoc markup screen by screen, no big-bang.

> **Why wrap instead of replace?** The visual system (colors, `.btn`, `.card`, `.badge`, spacing) is already cohesive. The missing piece was *component maturity*: typed inputs, encapsulated behaviour, a11y, and a single place to evolve. These wrappers deliver that without re-theming anything.

## Import

No path alias is configured — import by relative path in a component's `imports: []`:

```ts
import { UiButtonComponent, UiKpiCardComponent, UiPageHeaderComponent } from '../../shared/ui';

@Component({
  standalone: true,
  imports: [CommonModule, UiButtonComponent, UiKpiCardComponent, UiPageHeaderComponent],
  // …
})
```

All components are standalone and tree-shakable — unused ones add nothing to the bundle.

## Catalogue

| Component | Selector | Purpose |
|---|---|---|
| `UiButtonComponent` | `<ui-button>` | Button — variants `primary \| secondary \| tertiary \| danger`, sizes `md \| sm \| xs`, `loading`, `disabled`. Native click bubbles. |
| `UiCardComponent` | `<ui-card>` | Surface container with optional header (`title` + `[card-actions]` slot) and body (`flush` for tables). |
| `UiKpiCardComponent` | `<ui-kpi-card>` | KPI tile — `label`, `value`, `prefix/suffix`, `tone`, optional `delta`+`trend`+`hint`. The decomposition unit for dashboards. |
| `UiProgressCardComponent` | `<ui-progress-card>` | Labelled progress bar — absorption today, construction progress tomorrow. |
| `UiStatusPillComponent` | `<ui-status-pill>` | Status chip mapped onto the canonical status palette (sales + construction statuses). |
| `UiEmptyStateComponent` | `<ui-empty-state>` | Dashed empty state with an action slot. |
| `UiPageHeaderComponent` | `<ui-page-header>` | Breadcrumb + title + subtitle + right-aligned action slot. Anchor for the project workspace header. |

## Examples

```html
<ui-page-header
   [breadcrumbs]="[{label:'Projets', link:'/app/projets'}, {label: projet.nom}]"
   title="Résidence Al Manar"
   subtitle="Casablanca · 3 tranches · 84 lots">
  <ui-button variant="primary" (click)="newTranche()">Nouvelle tranche</ui-button>
</ui-page-header>

<div class="kpi-grid">
  <ui-kpi-card label="Pipeline" [value]="pipeline | number" suffix=" DH" tone="primary" />
  <ui-kpi-card label="Conversion" [value]="conv" suffix=" %" [delta]="+4.2" trend="up" hint="vs. 30j" />
  <ui-progress-card title="Absorption" [percent]="absorption" caption="42/84 lots" />
</div>

<ui-status-pill [status]="vente.statut" />

<ui-empty-state icon="📭" title="Aucune vente"
                message="Créez votre première vente pour démarrer le pipeline.">
  <ui-button variant="primary" (click)="create()">Nouvelle vente</ui-button>
</ui-empty-state>
```

## Conventions
- Standalone + `ChangeDetectionStrategy.OnPush`.
- Classic `@Input()` decorators (matches existing `shared/pickers/` components).
- Styling via design tokens only — no hard-coded colors except the status palette (which mirrors the `--status-*` tokens).
- A11y baked in: `aria-busy` on loading buttons, `role="progressbar"` with aria values, `aria-current` on the active breadcrumb, `prefers-reduced-motion` honored.

## Adoption guidance
1. Start with **new** screens and the **KPI/empty-state** blocks of existing god-templates (lowest risk, highest reuse).
2. Decompose dashboards by replacing repeated KPI markup with `<ui-kpi-card>` driven by a typed array.
3. Roll `<ui-page-header>` across feature roots to converge information architecture.
4. Construction module: build on `ui-progress-card`, `ui-kpi-card`, `ui-status-pill` from day one; add `ProjectCard`/`MilestoneCard`/`SiteReportCard` here as that module lands.
