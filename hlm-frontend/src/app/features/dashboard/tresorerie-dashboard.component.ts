import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TresorerieService, TresorerieDashboard } from './tresorerie.service';

/**
 * VEFA treasury dashboard (Wave 12 P6): cash position from the legal échéancier plus the
 * actionable alerts — overdue calls, active options, retractions, bank agreements expiring.
 */
@Component({
  selector: 'app-tresorerie-dashboard',
  standalone: true,
  imports: [DecimalPipe, DatePipe, RouterLink],
  template: `
    <div class="page">
      <header class="page-head">
        <h1>Trésorerie VEFA</h1>
        <p class="sub">Position de caisse (échéancier légal) et alertes métier.</p>
      </header>

      @if (loading()) {
        <div class="skeleton-grid">
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
        </div>
      } @else if (error()) {
        <div class="empty-state"><p>{{ error() }}</p></div>
      } @else {
       @if (data(); as d) {
        <section class="kpi-row">
          <div class="kpi-card good">
            <span class="kpi-label">Encaissé</span>
            <span class="kpi-value">{{ d.encaisseTotal | number:'1.0-0' }} <small>MAD</small></span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">À encaisser</span>
            <span class="kpi-value">{{ d.aEncaisser | number:'1.0-0' }} <small>MAD</small></span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">Prévisionnel 6 mois</span>
            <span class="kpi-value">{{ d.previsionnel6Mois | number:'1.0-0' }} <small>MAD</small></span>
          </div>
          <div class="kpi-card" [class.bad]="d.enRetardCount > 0">
            <span class="kpi-label">En retard ({{ d.enRetardCount }})</span>
            <span class="kpi-value">{{ d.enRetardMontant | number:'1.0-0' }} <small>MAD</small></span>
          </div>
        </section>

        <section class="card forecast">
          <h2>Encaissements prévus par mois</h2>
          <p class="forecast-sub">Appels de fonds à venir, non encore réglés (hors retards).</p>
          @if (forecastMax(d) === 0) {
            <p class="empty">Aucun encaissement prévu sur les 6 prochains mois.</p>
          } @else {
            <div class="bars" role="img" aria-label="Prévisionnel de trésorerie sur 6 mois">
              @for (m of d.previsionnelParMois; track m.libelle) {
                <div class="bar-col">
                  <span class="bar-amount">{{ m.montant > 0 ? (m.montant / 1000 | number:'1.0-0') + ' K' : '—' }}</span>
                  <div class="bar-track">
                    <div class="bar-fill" [style.height.%]="forecastMax(d) > 0 ? (m.montant / forecastMax(d) * 100) : 0"
                         [class.bar-current]="$first"></div>
                  </div>
                  <span class="bar-label">{{ m.libelle }}</span>
                </div>
              }
            </div>
          }
        </section>

        <section class="alerts">
          <span class="chip" [class.warn]="d.optionsActives > 0">⏳ {{ d.optionsActives }} option(s) active(s)</span>
          <span class="chip" [class.warn]="d.retractationsEnCours > 0">↩ {{ d.retractationsEnCours }} en délai de rétractation</span>
          <span class="chip" [class.bad]="d.accordsExpirant15j > 0">🏦 {{ d.accordsExpirant15j }} accord(s) bancaire(s) expirant (15j)</span>
        </section>

        <section class="card">
          <h2>Appels de fonds en retard</h2>
          @if (d.appelsEnRetard.length === 0) {
            <p class="empty">Aucun appel en retard. 🎉</p>
          } @else {
            <table class="data-table">
              <thead>
                <tr><th>Vente</th><th>Acquéreur</th><th>Libellé</th><th>Montant</th><th>Échéance</th><th>Retard</th><th></th></tr>
              </thead>
              <tbody>
                @for (a of d.appelsEnRetard; track a.venteId) {
                  <tr>
                    <td>{{ a.venteRef }}</td>
                    <td>{{ a.acquereur || '—' }}</td>
                    <td>{{ a.libelle }}</td>
                    <td>{{ a.montant | number:'1.0-0' }} MAD</td>
                    <td>{{ a.dateEcheance | date:'dd/MM/yyyy' }}</td>
                    <td><span class="badge bad">{{ a.joursRetard }} j</span></td>
                    <td><a class="link" [routerLink]="['/app/ventes', a.venteId]">Ouvrir →</a></td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </section>
       }
      }
    </div>
  `,
  styles: [`
    .page { padding: 20px; max-width: 1100px; margin: 0 auto; }
    .page-head h1 { margin: 0; font-size: 22px; }
    .sub { color: #64748b; margin: 4px 0 16px; }
    .kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; }
    .kpi-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 6px; }
    .kpi-card.good { border-left: 4px solid #22c55e; }
    .kpi-card.bad { border-left: 4px solid #ef4444; }
    .kpi-label { font-size: 12px; color: #64748b; text-transform: uppercase; letter-spacing: .03em; }
    .kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; }
    .kpi-value small { font-size: 13px; color: #94a3b8; font-weight: 500; }
    .alerts { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; }
    .chip { background: #f1f5f9; border-radius: 9999px; padding: 6px 12px; font-size: 13px; color: #475569; }
    .chip.warn { background: #fef9c3; color: #a16207; }
    .chip.bad { background: #fee2e2; color: #b91c1c; }
    .card { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; margin-top: 8px; }
    .card h2 { font-size: 15px; margin: 0 0 12px; }
    .data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .data-table th, .data-table td { text-align: left; padding: 8px; border-bottom: 1px solid #f1f5f9; }
    .badge.bad { background: #fee2e2; color: #b91c1c; border-radius: 6px; padding: 2px 8px; }
    .link { color: #3b82f6; text-decoration: none; }
    .empty { color: #64748b; }
    .forecast { margin-top: 16px; }
    .forecast-sub { color: #64748b; font-size: 12px; margin: -6px 0 14px; }
    .bars { display: flex; align-items: flex-end; gap: 10px; height: 180px; padding-top: 8px; }
    .bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; }
    .bar-amount { font-size: 11px; color: #475569; font-weight: 600; margin-bottom: 4px; }
    .bar-track { flex: 1; width: 100%; display: flex; align-items: flex-end; justify-content: center; }
    .bar-fill { width: 70%; min-height: 2px; background: #cbd5e1; border-radius: 6px 6px 0 0; transition: height .3s ease; }
    .bar-fill.bar-current { background: #22c55e; }
    .bar-label { font-size: 11px; color: #64748b; margin-top: 6px; text-transform: capitalize; }
  `],
})
export class TresorerieDashboardComponent implements OnInit {
  private svc = inject(TresorerieService);

  data    = signal<TresorerieDashboard | null>(null);
  loading = signal(true);
  error   = signal('');

  ngOnInit(): void {
    this.svc.getTresorerie().subscribe({
      next:  (d) => { this.data.set(d); this.loading.set(false); },
      error: ()  => { this.error.set('Impossible de charger la trésorerie.'); this.loading.set(false); },
    });
  }

  /** Largest monthly amount — drives bar heights (0 when the forecast is empty). */
  forecastMax(d: TresorerieDashboard): number {
    return d.previsionnelParMois.reduce((max, m) => Math.max(max, m.montant), 0);
  }
}
