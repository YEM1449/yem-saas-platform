import { ChangeDetectionStrategy, Component, Input } from '@angular/core';


export type UiKpiTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';
export type UiTrend = 'up' | 'down' | 'flat';

/**
 * Single KPI tile — the most duplicated block in the app (the 2037-line
 * home-dashboard template is largely repeated KPI markup). Extracting it into one
 * typed component is the highest-leverage step in god-template decomposition and
 * is reused directly by future construction dashboards (EVM, progress, cost).
 *
 * Usage:
 *   <ui-kpi-card label="Pipeline" [value]="pipelineValue" suffix=" DH" tone="primary" />
 *   <ui-kpi-card label="Taux de conversion" [value]="rate" suffix=" %"
 *                [delta]="+4.2" trend="up" hint="vs. 30j" />
 */
@Component({
  selector: 'ui-kpi-card',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ui-kpi" [attr.data-tone]="tone">
      <div class="ui-kpi-head">
        <span class="ui-kpi-label">{{ label }}</span>
        @if (icon) { <span class="ui-kpi-icon" aria-hidden="true">{{ icon }}</span> }
      </div>
      <div class="ui-kpi-value">
        @if (prefix) { <span class="ui-kpi-affix">{{ prefix }}</span> }{{ value }}@if (suffix) { <span class="ui-kpi-affix">{{ suffix }}</span> }
      </div>
      @if (delta !== null && delta !== undefined) {
        <div class="ui-kpi-delta" [attr.data-trend]="trend">
          <span aria-hidden="true">{{ trend === 'up' ? '▲' : trend === 'down' ? '▼' : '—' }}</span>
          {{ delta > 0 ? '+' : '' }}{{ delta }}{{ deltaSuffix }}
          @if (hint) { <span class="ui-kpi-hint">{{ hint }}</span> }
        </div>
      } @else if (hint) {
        <div class="ui-kpi-hint-only">{{ hint }}</div>
      }
    </div>
  `,
  styles: [`
    .ui-kpi {
      background: var(--c-surface); border: 1px solid var(--c-border);
      border-radius: var(--r-md, 8px); padding: var(--sp-4, 16px);
      display: flex; flex-direction: column; gap: var(--sp-2, 8px);
      border-left: 3px solid var(--c-border-strong);
    }
    .ui-kpi[data-tone="primary"] { border-left-color: var(--c-primary); }
    .ui-kpi[data-tone="success"] { border-left-color: var(--c-success); }
    .ui-kpi[data-tone="warning"] { border-left-color: var(--c-warning); }
    .ui-kpi[data-tone="danger"]  { border-left-color: var(--c-danger); }
    .ui-kpi[data-tone="info"]    { border-left-color: var(--c-info); }
    .ui-kpi-head { display: flex; align-items: center; justify-content: space-between; }
    .ui-kpi-label { font-size: var(--text-sm, .875rem); color: var(--c-text-secondary); font-weight: 500; }
    .ui-kpi-icon { font-size: var(--text-lg, 1.125rem); }
    .ui-kpi-value { font-size: var(--text-2xl, 1.5rem); font-weight: 700; color: var(--c-text); line-height: 1.1; }
    .ui-kpi-affix { font-size: var(--text-base, 1rem); font-weight: 600; color: var(--c-text-secondary); }
    .ui-kpi-delta { display: inline-flex; align-items: center; gap: 6px; font-size: var(--text-xs, .75rem); font-weight: 600; }
    .ui-kpi-delta[data-trend="up"]   { color: var(--c-success); }
    .ui-kpi-delta[data-trend="down"] { color: var(--c-danger); }
    .ui-kpi-delta[data-trend="flat"] { color: var(--c-text-muted); }
    .ui-kpi-hint, .ui-kpi-hint-only { color: var(--c-text-muted); font-weight: 400; }
    .ui-kpi-hint-only { font-size: var(--text-xs, .75rem); }
  `],
})
export class UiKpiCardComponent {
  @Input() label = '';
  @Input() value: string | number = '';
  @Input() prefix?: string;
  @Input() suffix?: string;
  @Input() icon?: string;
  @Input() tone: UiKpiTone = 'neutral';
  @Input() delta?: number | null;
  @Input() deltaSuffix = '%';
  @Input() trend: UiTrend = 'flat';
  @Input() hint?: string;
}
