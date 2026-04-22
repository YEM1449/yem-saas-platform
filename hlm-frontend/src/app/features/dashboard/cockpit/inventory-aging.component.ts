import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InventoryAgingRow } from '../dashboard-cockpit.service';

const AGING_COLORS: Record<string, string> = {
  FRESH:  '#10b981',
  SHORT:  '#84cc16',
  MEDIUM: '#f59e0b',
  LONG:   '#ef4444',
  STALE:  '#7f1d1d',
};

@Component({
  selector: 'app-inventory-aging',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Ancienneté du stock disponible</span>
        <span class="widget-sub">Biens actifs · depuis mise en ligne</span>
      </div>

      @if (totalUnits === 0) {
        <div class="empty-state">Aucun bien actif en stock.</div>
      } @else {
        <div class="summary-row">
          <span class="summary-label">{{ totalUnits }} biens disponibles</span>
          <span class="summary-val">Valeur : {{ fmtAmount(totalValue) }}</span>
        </div>

        <!-- Stacked aging bar -->
        <div class="aging-bar">
          @for (r of rows; track r.bucket) {
            @if (r.count > 0) {
              <div class="aging-segment"
                   [style.width.%]="pct(r.count)"
                   [style.background]="color(r.bucket)"
                   [title]="r.bucketLabel + ' : ' + r.count + ' biens'"></div>
            }
          }
        </div>

        <!-- Buckets detail -->
        <div class="bucket-grid">
          @for (r of rows; track r.bucket) {
            <div class="bucket-card" [class.bucket-alert]="isAlert(r.bucket)">
              <div class="bucket-dot" [style.background]="color(r.bucket)"></div>
              <div class="bucket-body">
                <div class="bucket-label">{{ r.bucketLabel }}</div>
                <div class="bucket-count" [style.color]="color(r.bucket)">{{ r.count }}</div>
                <div class="bucket-val">{{ fmtAmount(r.totalValue) }}</div>
                @if (totalUnits > 0) {
                  <div class="bucket-pct">{{ pct(r.count) }}%</div>
                }
              </div>
            </div>
          }
        </div>

        @if (staleCount > 0) {
          <div class="aging-alert">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="7" cy="7" r="6" stroke="#dc2626" stroke-width="1.4"/>
              <path d="M7 4.5v3" stroke="#dc2626" stroke-width="1.6" stroke-linecap="round"/>
              <circle cx="7" cy="9.5" r=".6" fill="#dc2626"/>
            </svg>
            {{ staleCount }} bien{{ staleCount > 1 ? 's' : '' }} en stock depuis plus d'un an —
            action commerciale recommandée.
          </div>
        }
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:12px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-sub { font-size:11px; color:#6b7280; text-transform:uppercase; letter-spacing:.04em; }
    .summary-row { display:flex; justify-content:space-between; font-size:12px; color:#6b7280; margin-bottom:8px; }
    .summary-val { font-weight:600; color:#111827; }
    .aging-bar { display:flex; height:14px; border-radius:999px; overflow:hidden; gap:2px; margin-bottom:16px; }
    .aging-segment { min-width:4px; }
    .bucket-grid { display:grid; grid-template-columns:repeat(5,1fr); gap:8px; }
    .bucket-card { background:#f9fafb; border-radius:8px; padding:10px 8px; text-align:center; position:relative; }
    .bucket-card.bucket-alert { background:#fef2f2; }
    .bucket-dot { width:8px; height:8px; border-radius:50%; margin:0 auto 4px; }
    .bucket-label { font-size:10px; color:#6b7280; margin-bottom:4px; line-height:1.3; }
    .bucket-count { font-size:20px; font-weight:700; line-height:1; }
    .bucket-val { font-size:10px; color:#6b7280; margin-top:2px; }
    .bucket-pct { font-size:10px; color:#9ca3af; }
    .aging-alert { margin-top:12px; display:flex; align-items:center; gap:6px; font-size:12px; color:#dc2626; background:#fef2f2; padding:8px 12px; border-radius:6px; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
    @media(max-width:500px) { .bucket-grid { grid-template-columns:repeat(3,1fr); } }
  `],
})
export class InventoryAgingComponent {
  @Input() rows: InventoryAgingRow[] = [];

  get totalUnits(): number { return this.rows.reduce((s, r) => s + r.count, 0); }
  get totalValue(): number { return this.rows.reduce((s, r) => s + r.totalValue, 0); }
  get staleCount(): number { return this.rows.find(r => r.bucket === 'STALE')?.count ?? 0; }

  pct(count: number): number {
    if (this.totalUnits === 0) return 0;
    return Math.round((count / this.totalUnits) * 100);
  }

  isAlert(bucket: string): boolean { return bucket === 'LONG' || bucket === 'STALE'; }
  color(bucket: string): string { return AGING_COLORS[bucket] ?? '#9ca3af'; }

  fmtAmount(n: number): string {
    if (!n || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)     return Math.round(n / 1_000) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }
}
