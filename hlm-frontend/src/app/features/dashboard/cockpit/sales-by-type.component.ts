import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SalesByTypeRow } from '../dashboard-cockpit.service';

const TYPE_LABELS: Record<string, string> = {
  APPARTEMENT: 'Appartement', VILLA: 'Villa', STUDIO: 'Studio',
  DUPLEX: 'Duplex', T2: 'T2', T3: 'T3', PARKING: 'Parking',
};

const TYPE_COLORS: Record<string, string> = {
  APPARTEMENT: '#2563eb', VILLA: '#7c3aed', STUDIO: '#059669',
  DUPLEX: '#d97706', T3: '#0891b2', T2: '#65a30d', PARKING: '#94a3b8',
};

@Component({
  selector: 'app-sales-by-type',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Ventes par type de bien</span>
        <span class="widget-sub">CA signé · hors annulés</span>
      </div>

      @if (!rows || rows.length === 0) {
        <div class="empty-state">Pas encore de données par type.</div>
      } @else {
        <!-- Total row -->
        <div class="total-row">
          <span class="total-label">Total portefeuille signé</span>
          <span class="total-val">{{ fmt(totalCA) }}</span>
        </div>

        <!-- Stacked bar -->
        <div class="stacked-bar">
          @for (r of rows; track r.propertyType) {
            <div class="stacked-segment"
                 [style.width.%]="pct(r.totalCA)"
                 [style.background]="color(r.propertyType)"
                 [title]="label(r.propertyType) + ': ' + fmt(r.totalCA)"></div>
          }
        </div>

        <!-- Legend + detail table -->
        <div class="type-list">
          @for (r of rows; track r.propertyType; let i = $index) {
            <div class="type-row">
              <span class="type-dot" [style.background]="color(r.propertyType)"></span>
              <span class="type-name">{{ label(r.propertyType) }}</span>
              <span class="type-count">{{ r.ventesCount }} vente{{ r.ventesCount !== 1 ? 's' : '' }}</span>
              <div class="type-bar-wrap">
                <div class="type-bar"
                     [style.width.%]="pct(r.totalCA)"
                     [style.background]="color(r.propertyType)"></div>
              </div>
              <span class="type-pct">{{ pct(r.totalCA) | number:'1.0-0' }}%</span>
              <span class="type-ca">{{ fmt(r.totalCA) }}</span>
              @if (r.avgPricePerSqm && r.avgPricePerSqm > 0) {
                <span class="type-sqm">{{ fmtSqm(r.avgPricePerSqm) }}/m²</span>
              }
            </div>
          }
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
    .total-row { display:flex; justify-content:space-between; align-items:baseline; margin-bottom:8px; }
    .total-label { font-size:12px; color:#6b7280; }
    .total-val { font-size:18px; font-weight:700; color:#111827; }
    .stacked-bar { display:flex; height:12px; border-radius:999px; overflow:hidden; margin-bottom:16px; gap:2px; }
    .stacked-segment { min-width:4px; }
    .type-list { display:flex; flex-direction:column; gap:7px; }
    .type-row { display:grid; grid-template-columns:10px 100px 60px 1fr 36px 90px 60px; align-items:center; gap:8px; }
    .type-dot { width:10px; height:10px; border-radius:50%; flex-shrink:0; }
    .type-name { font-size:12.5px; font-weight:600; color:#374151; }
    .type-count { font-size:11px; color:#6b7280; white-space:nowrap; }
    .type-bar-wrap { height:6px; background:#f3f4f6; border-radius:999px; overflow:hidden; }
    .type-bar { height:100%; border-radius:999px; }
    .type-pct { font-size:11px; color:#6b7280; text-align:right; }
    .type-ca { font-size:12.5px; font-weight:600; color:#111827; text-align:right; }
    .type-sqm { font-size:10px; color:#9ca3af; text-align:right; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
    @media(max-width:600px) { .type-row { grid-template-columns:10px 1fr 50px 80px; }
      .type-count,.type-pct,.type-sqm { display:none; } }
  `],
})
export class SalesByTypeComponent {
  @Input() rows: SalesByTypeRow[] = [];

  get totalCA(): number {
    return this.rows.reduce((s, r) => s + r.totalCA, 0);
  }

  pct(ca: number): number {
    const t = this.totalCA;
    if (t === 0) return 0;
    return Math.round((ca / t) * 100);
  }

  label(type: string): string { return TYPE_LABELS[type] ?? type; }
  color(type: string): string { return TYPE_COLORS[type] ?? '#6b7280'; }

  fmt(n: number): string {
    if (!n || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000)     return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }

  fmtSqm(n: number): string {
    if (!n) return '—';
    if (n >= 1_000) return Math.round(n / 1_000) + ' K MAD';
    return Math.round(n).toLocaleString('fr-FR') + ' MAD';
  }
}
