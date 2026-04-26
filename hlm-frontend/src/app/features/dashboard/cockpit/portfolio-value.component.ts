import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SalesIntelligence } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-portfolio-value',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pv-row">
      <!-- Total portfolio value -->
      <div class="pv-card pv-portfolio">
        <div class="pv-icon">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <rect x="2" y="5" width="16" height="12" rx="2" stroke="currentColor" stroke-width="1.8"/>
            <path d="M7 5V3.5A1.5 1.5 0 0 1 8.5 2h3A1.5 1.5 0 0 1 13 3.5V5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            <path d="M2 10h16" stroke="currentColor" stroke-width="1.5" stroke-dasharray="2 2"/>
          </svg>
        </div>
        <div class="pv-body">
          <div class="pv-val">{{ fmt(si?.totalPortfolioValue) }}</div>
          <div class="pv-label">Valeur totale portefeuille</div>
          <div class="pv-hint">Catalogue complet (hors brouillons)</div>
        </div>
      </div>

      <!-- Unsold inventory value -->
      <div class="pv-card pv-unsold">
        <div class="pv-icon">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M3 17L9 7l4 6 3-4 4 8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="pv-body">
          <div class="pv-val pv-val-amber">{{ fmt(si?.unsoldInventoryValue) }}</div>
          <div class="pv-label">Stock invendu (actif + réservé)</div>
          <div class="pv-hint">
            {{ si?.activeUnitsCount ?? 0 }} disponibles ·
            {{ si?.reservedUnitsCount ?? 0 }} réservés
          </div>
        </div>
      </div>

      <!-- Average list price -->
      <div class="pv-card pv-avgprice">
        <div class="pv-icon">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <circle cx="10" cy="10" r="8" stroke="currentColor" stroke-width="1.8"/>
            <path d="M10 6v8M7.5 8.5c0-1.1.9-2 2.5-2s2.5.9 2.5 2S12.2 11 10 11s-2.5.9-2.5 2 .9 2 2.5 2 2.5-.9 2.5-2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="pv-body">
          <div class="pv-val pv-val-blue">{{ fmt(si?.avgListPriceActive) }}</div>
          <div class="pv-label">Prix catalogue moyen</div>
          <div class="pv-hint">Biens disponibles à la vente</div>
        </div>
      </div>

      <!-- Price per sqm -->
      @if (si?.globalAvgPricePerSqm) {
        <div class="pv-card pv-sqm">
          <div class="pv-icon">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <rect x="2" y="2" width="16" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/>
              <path d="M2 10h16M10 2v16" stroke="currentColor" stroke-width="1.2" stroke-dasharray="2 3"/>
            </svg>
          </div>
          <div class="pv-body">
            <div class="pv-val pv-val-purple">{{ fmtSqm(si!.globalAvgPricePerSqm) }}</div>
            <div class="pv-label">Prix moyen au m²</div>
            <div class="pv-hint">Catalogue actif</div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .pv-row { display:grid; grid-template-columns:repeat(auto-fill, minmax(220px,1fr)); gap:14px; }
    .pv-card { background:#fff; border:1px solid #e2e8f0; border-radius:12px; padding:16px; display:flex; gap:12px; align-items:flex-start; }
    .pv-icon { width:36px; height:36px; border-radius:8px; background:#f1f5f9; display:flex; align-items:center; justify-content:center; color:#64748b; flex-shrink:0; }
    .pv-portfolio .pv-icon { background:#eff6ff; color:#16a34a; }
    .pv-unsold .pv-icon { background:#fffbeb; color:#d97706; }
    .pv-avgprice .pv-icon { background:#f0fdf4; color:#15803d; }
    .pv-sqm .pv-icon { background:#f5f3ff; color:#7c3aed; }
    .pv-body { flex:1; min-width:0; }
    .pv-val { font-size:19px; font-weight:700; color:#1e293b; }
    .pv-val-amber { color:#d97706; }
    .pv-val-blue { color:#16a34a; }
    .pv-val-purple { color:#7c3aed; }
    .pv-label { font-size:12px; font-weight:600; color:#374151; margin-top:3px; }
    .pv-hint { font-size:11px; color:#94a3b8; margin-top:2px; }
  `],
})
export class PortfolioValueComponent {
  @Input() si: SalesIntelligence | null = null;

  fmt(n: number | null | undefined): string {
    if (!n || n === 0) return '—';
    if (n >= 1_000_000_000) return (n / 1_000_000_000).toFixed(1).replace('.', ',') + ' Md MAD';
    if (n >= 1_000_000)     return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)         return Math.round(n / 1_000) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }

  fmtSqm(n: number | null): string {
    if (!n || n === 0) return '—';
    if (n >= 1_000) return (n / 1_000).toFixed(0) + ' K MAD/m²';
    return Math.round(n).toLocaleString('fr-FR') + ' MAD/m²';
  }
}
