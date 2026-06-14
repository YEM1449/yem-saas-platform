import { ChangeDetectionStrategy, Component, Input } from '@angular/core';


/**
 * Labelled progress card with a single percentage bar. Dual-purpose by design:
 *  - Real estate today: absorption / sell-through ((SOLD+RESERVED)/total).
 *  - Construction tomorrow: physical progress %, milestone completion, budget consumed.
 *
 * Building it now (construction-ready) means the future module reuses it rather
 * than re-inventing a progress widget.
 *
 * Usage:
 *   <ui-progress-card title="Absorption" [percent]="absorption" subtitle="Tranche 1" />
 *   <ui-progress-card title="Avancement chantier" [percent]="62" tone="info"
 *                     [caption]="'18/29 lots livrés'" />
 */
@Component({
  selector: 'ui-progress-card',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ui-prog">
      <div class="ui-prog-head">
        <span class="ui-prog-title">{{ title }}</span>
        <span class="ui-prog-pct">{{ clamped }}%</span>
      </div>
      @if (subtitle) { <div class="ui-prog-sub">{{ subtitle }}</div> }
      <div class="ui-prog-track" role="progressbar"
           [attr.aria-valuenow]="clamped" aria-valuemin="0" aria-valuemax="100"
           [attr.aria-label]="title">
        <div class="ui-prog-fill" [attr.data-tone]="tone" [style.width.%]="clamped"></div>
      </div>
      @if (caption) { <div class="ui-prog-caption">{{ caption }}</div> }
    </div>
  `,
  styles: [`
    .ui-prog {
      background: var(--c-surface); border: 1px solid var(--c-border);
      border-radius: var(--r-md, 8px); padding: var(--sp-4, 16px);
      display: flex; flex-direction: column; gap: var(--sp-2, 8px);
    }
    .ui-prog-head { display: flex; align-items: baseline; justify-content: space-between; }
    .ui-prog-title { font-size: var(--text-sm, .875rem); font-weight: 600; color: var(--c-text); }
    .ui-prog-pct { font-size: var(--text-lg, 1.125rem); font-weight: 700; color: var(--c-primary); }
    .ui-prog-sub { font-size: var(--text-xs, .75rem); color: var(--c-text-secondary); }
    .ui-prog-track { height: 8px; background: var(--c-neutral-100); border-radius: 999px; overflow: hidden; }
    .ui-prog-fill { height: 100%; background: var(--c-primary); border-radius: 999px; transition: width .4s ease; }
    .ui-prog-fill[data-tone="success"] { background: var(--c-success); }
    .ui-prog-fill[data-tone="warning"] { background: var(--c-warning); }
    .ui-prog-fill[data-tone="danger"]  { background: var(--c-danger); }
    .ui-prog-fill[data-tone="info"]    { background: var(--c-info); }
    .ui-prog-caption { font-size: var(--text-xs, .75rem); color: var(--c-text-muted); }
    @media (prefers-reduced-motion: reduce) { .ui-prog-fill { transition: none; } }
  `],
})
export class UiProgressCardComponent {
  @Input() title = '';
  @Input() subtitle?: string;
  @Input() caption?: string;
  @Input() percent = 0;
  @Input() tone: 'primary' | 'success' | 'warning' | 'danger' | 'info' = 'primary';

  get clamped(): number {
    return Math.max(0, Math.min(100, Math.round(this.percent)));
  }
}
