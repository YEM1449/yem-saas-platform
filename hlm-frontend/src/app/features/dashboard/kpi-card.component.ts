import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

/** Generic KPI summary card for dashboard hero sections. */
@Component({
  selector: 'app-kpi-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kpi-card" [class.clickable]="!!clicked.observed" (click)="clicked.emit()">
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value" [class]="valueClass">{{ value }}</div>
      @if (sub) { <div class="kpi-sub">{{ sub }}</div> }
    </div>
  `,
  styles: [`
    .kpi-card {
      background: var(--surface, #fff);
      border: 1px solid var(--border, #e5e7eb);
      border-radius: 10px;
      padding: 14px 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      transition: box-shadow .15s ease;
    }
    .kpi-card.clickable { cursor: pointer; }
    .kpi-card.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,.06); }
    .kpi-label {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-muted, #6b7280);
      text-transform: uppercase;
      letter-spacing: .3px;
    }
    .kpi-value {
      font-size: 24px;
      font-weight: 700;
      color: var(--text, #111827);
      line-height: 1.1;
    }
    .kpi-good { color: #10b981; }
    .kpi-warn { color: #f59e0b; }
    .kpi-bad  { color: #ef4444; }
    .kpi-sub  { font-size: .75rem; color: var(--text-muted, #9ca3af); }
  `],
})
export class KpiCardComponent {
  @Input({ required: true }) label = '';
  @Input({ required: true }) value = '';
  @Input() sub: string | null = null;
  @Input() valueClass: 'kpi-good' | 'kpi-warn' | 'kpi-bad' | '' = '';
  @Output() clicked = new EventEmitter<void>();
}
