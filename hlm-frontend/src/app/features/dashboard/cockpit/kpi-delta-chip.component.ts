import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { KpiDelta } from '../dashboard-cockpit.service';

/**
 * Compact chip showing the period-over-period delta of a KPI.
 *
 * <p>Renders an arrow + signed percentage; falls back to a "—" when no
 * comparison is possible (previous period was zero).
 */
@Component({
  selector: 'app-kpi-delta-chip',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (delta?.deltaPct == null) {
      <span class="kpi-chip kpi-chip-neutral" title="Pas de période de référence">—</span>
    } @else {
      <span class="kpi-chip"
            [class.kpi-chip-up]="(delta!.deltaPct ?? 0) >= 0"
            [class.kpi-chip-down]="(delta!.deltaPct ?? 0) < 0"
            [title]="'Période précédente : ' + previousLabel">
        {{ (delta!.deltaPct ?? 0) >= 0 ? '↑' : '↓' }}
        {{ absPct }}%
      </span>
    }
  `,
  styles: [`
    .kpi-chip {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      padding: 2px 8px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 600;
      line-height: 1.4;
      letter-spacing: 0.01em;
      white-space: nowrap;
    }
    .kpi-chip-up      { background: #ecfdf5; color: #047857; }
    .kpi-chip-down    { background: #fef2f2; color: #b91c1c; }
    .kpi-chip-neutral { background: #f3f4f6; color: #6b7280; }
  `],
})
export class KpiDeltaChipComponent {
  @Input() delta: KpiDelta | null = null;
  @Input() previousLabel = '';

  get absPct(): number {
    const p = this.delta?.deltaPct ?? 0;
    return p < 0 ? -p : p;
  }
}
