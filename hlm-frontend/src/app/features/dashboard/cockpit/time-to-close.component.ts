import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TimeToCloseRow } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-time-to-close',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Durée du cycle de vente</span>
        <span class="widget-sub">Ventes livrées uniquement</span>
      </div>

      @if (totalDeals === 0) {
        <div class="empty-state">Aucune vente livrée pour calculer le cycle.</div>
      } @else {
        @if (avgDays) {
          <div class="avg-row">
            <span class="avg-val">{{ avgDays | number:'1.0-0' }} jours</span>
            <span class="avg-label">délai moyen de closing</span>
            <span class="avg-chip" [class.chip-good]="avgDays < 60"
                  [class.chip-warn]="avgDays >= 60 && avgDays < 120"
                  [class.chip-bad]="avgDays >= 120">
              {{ cycleHealthLabel(avgDays) }}
            </span>
          </div>
        }

        <div class="ttc-bars">
          @for (r of rows; track r.bucket) {
            <div class="ttc-row">
              <div class="ttc-label">{{ r.bucketLabel }}</div>
              <div class="ttc-track">
                <div class="ttc-fill"
                     [style.width.%]="barPct(r.count)"
                     [class.fill-fast]="r.bucket === 'LT_30'"
                     [class.fill-med]="r.bucket === 'D30_60' || r.bucket === 'D61_90'"
                     [class.fill-slow]="r.bucket === 'D91_180' || r.bucket === 'GT_180'"></div>
              </div>
              <span class="ttc-count">{{ r.count }}</span>
              <span class="ttc-pct">{{ barPct(r.count) | number:'1.0-0' }}%</span>
              @if (r.avgDays != null) {
                <span class="ttc-avg">moy. {{ r.avgDays | number:'1.0-0' }}j</span>
              }
            </div>
          }
        </div>

        <div class="ttc-insight">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <circle cx="6" cy="6" r="5" stroke="#6366f1" stroke-width="1.4"/>
            <path d="M6 4v3" stroke="#6366f1" stroke-width="1.4" stroke-linecap="round"/>
            <circle cx="6" cy="8.5" r=".5" fill="#6366f1"/>
          </svg>
          <span>{{ totalDeals }} vente{{ totalDeals > 1 ? 's' : '' }} livrée{{ totalDeals > 1 ? 's' : '' }} analysées
            @if (fastCount > 0) { · {{ fastCount }} en moins de 30 jours }</span>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:12px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-sub { font-size:11px; color:#6b7280; text-transform:uppercase; letter-spacing:.04em; }
    .avg-row { display:flex; align-items:baseline; gap:10px; margin-bottom:16px; flex-wrap:wrap; }
    .avg-val { font-size:26px; font-weight:700; color:#111827; }
    .avg-label { font-size:12px; color:#6b7280; }
    .avg-chip { font-size:11px; padding:2px 8px; border-radius:99px; font-weight:600; }
    .chip-good { background:#d1fae5; color:#047857; }
    .chip-warn { background:#fef3c7; color:#92400e; }
    .chip-bad  { background:#fee2e2; color:#b91c1c; }
    .ttc-bars { display:flex; flex-direction:column; gap:8px; }
    .ttc-row { display:grid; grid-template-columns:110px 1fr 30px 36px 60px; align-items:center; gap:8px; }
    .ttc-label { font-size:12px; color:#374151; font-weight:500; }
    .ttc-track { height:10px; background:#f3f4f6; border-radius:999px; overflow:hidden; }
    .ttc-fill { height:100%; border-radius:999px; min-width:4px; }
    .fill-fast { background:#10b981; }
    .fill-med  { background:#f59e0b; }
    .fill-slow { background:#ef4444; }
    .ttc-count { font-size:12px; font-weight:600; color:#111827; text-align:right; }
    .ttc-pct { font-size:11px; color:#6b7280; text-align:right; }
    .ttc-avg { font-size:10px; color:#9ca3af; text-align:right; }
    .ttc-insight { margin-top:12px; display:flex; align-items:center; gap:6px; font-size:11px; color:#6b7280; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class TimeToCloseComponent {
  @Input() rows: TimeToCloseRow[] = [];
  @Input() avgDays: number | null = null;

  get totalDeals(): number { return this.rows.reduce((s, r) => s + r.count, 0); }
  get fastCount(): number  { return this.rows.find(r => r.bucket === 'LT_30')?.count ?? 0; }

  barPct(count: number): number {
    if (this.totalDeals === 0) return 0;
    return Math.round((count / this.totalDeals) * 100);
  }

  cycleHealthLabel(days: number): string {
    if (days < 60)  return 'Cycle rapide';
    if (days < 120) return 'Cycle normal';
    return 'Cycle long';
  }
}
