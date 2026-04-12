import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Forecast } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-forecast-widget',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Prévision de CA</span>
        <span class="widget-sub">Pondéré par probabilité de closing</span>
      </div>

      @if (!data) {
        <div class="empty-state">Chargement…</div>
      } @else {
        <div class="timeline">
          <div class="tl-item" [class.tl-highlight]="data.next30Days > 0">
            <div class="tl-horizon">30 jours</div>
            <div class="tl-bar-track">
              <div class="tl-bar" [style.width.%]="barPct(data.next30Days)" style="background:#10b981"></div>
            </div>
            <div class="tl-value">{{ fmt(data.next30Days) }}</div>
          </div>
          <div class="tl-item">
            <div class="tl-horizon">60 jours</div>
            <div class="tl-bar-track">
              <div class="tl-bar" [style.width.%]="barPct(data.next60Days)" style="background:#3b82f6"></div>
            </div>
            <div class="tl-value">{{ fmt(data.next60Days) }}</div>
          </div>
          <div class="tl-item">
            <div class="tl-horizon">90 jours</div>
            <div class="tl-bar-track">
              <div class="tl-bar" [style.width.%]="barPct(data.next90Days)" style="background:#6366f1"></div>
            </div>
            <div class="tl-value">{{ fmt(data.next90Days) }}</div>
          </div>
        </div>

        @if (data.undatedCount > 0) {
          <div class="undated-warn">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.4"/>
              <path d="M7 4v3.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
              <circle cx="7" cy="10" r=".7" fill="currentColor"/>
            </svg>
            {{ data.undatedCount }} vente{{ data.undatedCount > 1 ? 's' : '' }}
            sans date de closing prévue ({{ fmt(data.undated) }} CA pondéré non planifié)
          </div>
        }
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:16px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-sub { font-size:11px; color:#6b7280; text-transform:uppercase; letter-spacing:.04em; }
    .timeline { display:flex; flex-direction:column; gap:14px; }
    .tl-item { display:flex; align-items:center; gap:12px; }
    .tl-horizon { font-size:12px; font-weight:600; color:#374151; min-width:70px; }
    .tl-bar-track { flex:1; height:10px; background:#f3f4f6; border-radius:999px; overflow:hidden; }
    .tl-bar { height:100%; border-radius:999px; transition:width 280ms ease; min-width:4px; }
    .tl-value { font-size:13px; font-weight:700; color:#111827; min-width:80px; text-align:right; font-variant-numeric:tabular-nums; }
    .tl-highlight .tl-value { color:#047857; }
    .undated-warn { display:flex; align-items:center; gap:8px; margin-top:14px; padding:10px 12px; border-radius:8px; background:#fef3c7; color:#92400e; font-size:12px; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class ForecastWidgetComponent {
  @Input() data: Forecast | null = null;

  barPct(value: number): number {
    if (!this.data || !value) return 0;
    const max = Math.max(this.data.next30Days, this.data.next60Days, this.data.next90Days, 1);
    return Math.min(100, (value / max) * 100);
  }

  fmt(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000) return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }
}
