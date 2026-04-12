import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DiscountAnalytics } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-discount-analytics',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Analyse des remises</span>
        <span class="widget-sub">Prix catalogue vs prix de vente</span>
      </div>

      @if (!data) {
        <div class="empty-state">Chargement…</div>
      } @else if (data.totalDeals === 0) {
        <div class="empty-state">Pas encore de données de vente avec prix catalogue.</div>
      } @else {
        <div class="disc-summary">
          <div class="disc-stat">
            <div class="disc-num">{{ data.totalDealsWithDiscount }}<span class="disc-denom">/{{ data.totalDeals }}</span></div>
            <div class="disc-label">Ventes avec remise</div>
          </div>
          <div class="disc-stat">
            <div class="disc-num" [class.disc-warn]="(data.avgDiscountPercent ?? 0) > 10">
              {{ data.avgDiscountPercent != null ? data.avgDiscountPercent + '%' : '—' }}
            </div>
            <div class="disc-label">Remise moyenne</div>
          </div>
          <div class="disc-stat">
            <div class="disc-num" [class.disc-bad]="(data.maxDiscountPercent ?? 0) > 20">
              {{ data.maxDiscountPercent != null ? data.maxDiscountPercent + '%' : '—' }}
            </div>
            <div class="disc-label">Remise max</div>
          </div>
          <div class="disc-stat">
            <div class="disc-num">{{ fmt(data.totalDiscountVolume) }}</div>
            <div class="disc-label">Volume remisé</div>
          </div>
        </div>

        @if (data.byAgent.length > 0) {
          <div class="table-wrap">
            <table class="disc-table">
              <thead>
                <tr>
                  <th>Agent</th>
                  <th class="num">Avec remise</th>
                  <th class="num">Remise moy.</th>
                  <th class="num">Vol. remisé</th>
                </tr>
              </thead>
              <tbody>
                @for (a of data.byAgent; track a.agentId) {
                  <tr>
                    <td class="name">{{ a.agentName }}</td>
                    <td class="num">{{ a.dealsWithDiscount }}/{{ a.totalDeals }}</td>
                    <td class="num" [class.text-warn]="(a.avgDiscountPercent ?? 0) > 10">
                      {{ a.avgDiscountPercent != null ? a.avgDiscountPercent + '%' : '—' }}
                    </td>
                    <td class="num">{{ fmt(a.totalDiscountVolume) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:14px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-sub { font-size:11px; color:#6b7280; text-transform:uppercase; letter-spacing:.04em; }
    .disc-summary { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:16px; }
    .disc-stat { text-align:center; flex:1; min-width:80px; padding:10px 8px; border-radius:8px; background:#f9fafb; }
    .disc-num { font-size:18px; font-weight:700; color:#111827; font-variant-numeric:tabular-nums; }
    .disc-denom { font-size:13px; font-weight:400; color:#6b7280; }
    .disc-label { font-size:11px; color:#6b7280; margin-top:2px; }
    .disc-warn { color:#d97706; }
    .disc-bad { color:#dc2626; }
    .table-wrap { overflow-x:auto; }
    .disc-table { width:100%; border-collapse:collapse; font-size:13px; }
    .disc-table th { color:#6b7280; font-weight:600; font-size:11px; text-transform:uppercase; padding:6px 8px; border-bottom:1px solid #e5e7eb; text-align:left; white-space:nowrap; }
    .disc-table td { padding:8px; border-bottom:1px solid #f3f4f6; }
    .num { text-align:right; font-variant-numeric:tabular-nums; }
    .name { font-weight:600; color:#111827; }
    .text-warn { color:#d97706; font-weight:600; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class DiscountAnalyticsComponent {
  @Input() data: DiscountAnalytics | null = null;

  fmt(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000) return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }
}
