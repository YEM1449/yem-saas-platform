import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PricePerSqmRow, PricePerSqmProjectRow } from '../dashboard-cockpit.service';

const TYPE_LABELS: Record<string, string> = {
  APPARTEMENT: 'Appartement', VILLA: 'Villa', STUDIO: 'Studio',
  DUPLEX: 'Duplex', T2: 'T2', T3: 'T3', PARKING: 'Parking',
};

const TYPE_COLORS = [
  '#16a34a','#7c3aed','#059669','#d97706','#0891b2','#65a30d','#94a3b8',
];

@Component({
  selector: 'app-price-per-sqm',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Prix au m²</span>
        <span class="widget-sub">Stock catalogue</span>
      </div>

      @if (byType.length === 0) {
        <div class="empty-state">Données surface non disponibles.</div>
      } @else {
        <div class="global-row">
          @if (globalAvg) {
            <div class="global-val">{{ fmtSqm(globalAvg) }}<span class="global-unit">/m² moyen</span></div>
          }
        </div>

        <!-- By type bars -->
        <div class="section-label">Par type de bien</div>
        <div class="sqm-list">
          @for (r of byType; track r.propertyType; let i = $index) {
            <div class="sqm-row">
              <span class="sqm-type">{{ typeLabel(r.propertyType) }}</span>
              <div class="sqm-bar-wrap">
                <div class="sqm-bar"
                     [style.width.%]="barWidth(r.avgPricePerSqm)"
                     [style.background]="TYPE_COLORS[i % TYPE_COLORS.length]"></div>
              </div>
              <span class="sqm-val">{{ fmtSqm(r.avgPricePerSqm) }}</span>
              <span class="sqm-count">{{ r.count }} biens</span>
            </div>
          }
        </div>

        @if (byProject.length > 0) {
          <div class="section-label" style="margin-top:16px">Par projet (ventes)</div>
          <div class="proj-list">
            @for (r of byProject; track r.projectId; let i = $index) {
              <div class="sqm-row">
                <span class="sqm-type">{{ r.projectName }}</span>
                <div class="sqm-bar-wrap">
                  <div class="sqm-bar"
                       [style.width.%]="projBarWidth(r.avgPricePerSqm)"
                       [style.background]="TYPE_COLORS[(i + 3) % TYPE_COLORS.length]"></div>
                </div>
                <span class="sqm-val">{{ fmtSqm(r.avgPricePerSqm) }}</span>
                <span class="sqm-count">{{ r.sampleSize }} ventes</span>
              </div>
            }
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
    .global-row { margin-bottom:12px; }
    .global-val { font-size:22px; font-weight:700; color:#16a34a; }
    .global-unit { font-size:13px; font-weight:400; color:#6b7280; margin-left:4px; }
    .section-label { font-size:11px; font-weight:600; text-transform:uppercase; letter-spacing:.06em; color:#9ca3af; margin-bottom:8px; }
    .sqm-list, .proj-list { display:flex; flex-direction:column; gap:7px; }
    .sqm-row { display:grid; grid-template-columns:110px 1fr 80px 55px; align-items:center; gap:8px; }
    .sqm-type { font-size:12.5px; font-weight:600; color:#374151; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .sqm-bar-wrap { height:8px; background:#f3f4f6; border-radius:999px; overflow:hidden; }
    .sqm-bar { height:100%; border-radius:999px; transition:width .3s; }
    .sqm-val { font-size:12.5px; font-weight:700; color:#111827; text-align:right; }
    .sqm-count { font-size:10px; color:#9ca3af; text-align:right; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class PricePerSqmComponent {
  @Input() byType: PricePerSqmRow[] = [];
  @Input() byProject: PricePerSqmProjectRow[] = [];
  @Input() globalAvg: number | null = null;

  readonly TYPE_COLORS = TYPE_COLORS;

  typeLabel(t: string): string { return TYPE_LABELS[t] ?? t; }

  get maxTypeSqm(): number {
    return Math.max(...this.byType.map(r => r.avgPricePerSqm ?? 0), 1);
  }

  get maxProjSqm(): number {
    return Math.max(...this.byProject.map(r => r.avgPricePerSqm ?? 0), 1);
  }

  barWidth(v: number | null): number {
    if (!v || v === 0) return 2;
    return Math.min((v / this.maxTypeSqm) * 100, 100);
  }

  projBarWidth(v: number | null): number {
    if (!v || v === 0) return 2;
    return Math.min((v / this.maxProjSqm) * 100, 100);
  }

  fmtSqm(n: number | null): string {
    if (!n || n === 0) return '—';
    if (n >= 1_000) return (n / 1_000).toFixed(0) + ' K MAD';
    return Math.round(n).toLocaleString('fr-FR') + ' MAD';
  }
}
