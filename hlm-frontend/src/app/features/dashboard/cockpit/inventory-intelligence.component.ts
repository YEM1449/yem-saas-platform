import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { InventoryIntelligence } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-inventory-intelligence',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Intelligence stock</span>
        <a routerLink="/app/properties" class="widget-link">Voir les biens →</a>
      </div>

      @if (!data) {
        <div class="empty-state">Chargement…</div>
      } @else {
        <div class="stock-summary">
          <div class="stock-stat">
            <div class="stock-num">{{ data.overall.total }}</div>
            <div class="stock-label">Total lots</div>
          </div>
          <div class="stock-stat stat-avail">
            <div class="stock-num">{{ data.overall.available }}</div>
            <div class="stock-label">Disponibles</div>
          </div>
          <div class="stock-stat stat-res">
            <div class="stock-num">{{ data.overall.reserved }}</div>
            <div class="stock-label">Réservés</div>
          </div>
          <div class="stock-stat stat-sold">
            <div class="stock-num">{{ data.overall.sold }}</div>
            <div class="stock-label">Vendus</div>
          </div>
          @if (data.overall.withdrawn > 0) {
            <div class="stock-stat stat-withd">
              <div class="stock-num">{{ data.overall.withdrawn }}</div>
              <div class="stock-label">Retirés</div>
            </div>
          }
        </div>

        @if (data.overall.absorptionRate != null) {
          <div class="absorption-row">
            <span class="abs-label">Absorption globale</span>
            <div class="abs-track">
              <div class="abs-fill"
                   [style.width.%]="Math.min(data.overall.absorptionRate, 100)"
                   [class.fill-success]="data.overall.absorptionRate >= 70"
                   [class.fill-warning]="data.overall.absorptionRate >= 40 && data.overall.absorptionRate < 70"
                   [class.fill-danger]="data.overall.absorptionRate < 40"></div>
            </div>
            <span class="abs-value">{{ data.overall.absorptionRate }}%</span>
          </div>
        }

        @if (data.byProject.length > 0) {
          <div class="table-wrap">
            <table class="inv-table">
              <thead>
                <tr>
                  <th>Projet</th>
                  <th class="num">Dispo.</th>
                  <th class="num">Rés.</th>
                  <th class="num">Vendus</th>
                  <th class="num">Absorption</th>
                  <th class="num">Valeur stock</th>
                </tr>
              </thead>
              <tbody>
                @for (p of data.byProject; track p.projectId) {
                  <tr>
                    <td class="name">
                      <a [routerLink]="['/app/projects', p.projectId]" class="proj-link">{{ p.projectName }}</a>
                    </td>
                    <td class="num">{{ p.available }}</td>
                    <td class="num">{{ p.reserved }}</td>
                    <td class="num">{{ p.sold }}</td>
                    <td class="num">
                      @if (p.absorptionRate != null) {
                        <span [class.text-success]="p.absorptionRate >= 70"
                              [class.text-warning]="p.absorptionRate >= 40 && p.absorptionRate < 70"
                              [class.text-danger]="p.absorptionRate < 40">
                          {{ p.absorptionRate }}%
                        </span>
                      } @else { — }
                    </td>
                    <td class="num">{{ fmt(p.totalValue) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="empty-state">Aucun projet avec des lots.</div>
        }
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:14px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-link { font-size:12px; color:#6366f1; text-decoration:none; }
    .widget-link:hover { text-decoration:underline; }
    .stock-summary { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:16px; }
    .stock-stat { text-align:center; flex:1; min-width:60px; padding:10px 8px; border-radius:8px; background:#f9fafb; }
    .stock-num { font-size:20px; font-weight:700; color:#111827; font-variant-numeric:tabular-nums; }
    .stock-label { font-size:11px; color:#6b7280; margin-top:2px; }
    .stat-avail { background:#ecfdf5; }
    .stat-avail .stock-num { color:#047857; }
    .stat-res { background:#fef3c7; }
    .stat-res .stock-num { color:#92400e; }
    .stat-sold { background:#eff6ff; }
    .stat-sold .stock-num { color:#15803d; }
    .stat-withd { background:#f3f4f6; }
    .stat-withd .stock-num { color:#6b7280; }
    .absorption-row { display:flex; align-items:center; gap:12px; margin-bottom:16px; }
    .abs-label { font-size:12px; font-weight:600; color:#374151; min-width:120px; }
    .abs-track { flex:1; height:10px; background:#f3f4f6; border-radius:999px; overflow:hidden; }
    .abs-fill { height:100%; border-radius:999px; transition:width 280ms ease; min-width:4px; }
    .fill-success { background:#10b981; }
    .fill-warning { background:#f59e0b; }
    .fill-danger { background:#ef4444; }
    .abs-value { font-size:13px; font-weight:700; color:#111827; min-width:50px; text-align:right; }
    .table-wrap { overflow-x:auto; }
    .inv-table { width:100%; border-collapse:collapse; font-size:13px; }
    .inv-table th { color:#6b7280; font-weight:600; font-size:11px; text-transform:uppercase; padding:6px 8px; border-bottom:1px solid #e5e7eb; text-align:left; white-space:nowrap; }
    .inv-table td { padding:8px; border-bottom:1px solid #f3f4f6; }
    .num { text-align:right; font-variant-numeric:tabular-nums; }
    .name { font-weight:600; color:#111827; }
    .proj-link { color:#4f46e5; text-decoration:none; }
    .proj-link:hover { text-decoration:underline; }
    .text-success { color:#047857; font-weight:600; }
    .text-warning { color:#d97706; font-weight:600; }
    .text-danger { color:#dc2626; font-weight:600; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class InventoryIntelligenceComponent {
  @Input() data: InventoryIntelligence | null = null;

  readonly Math = Math;

  fmt(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000) return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }
}
