import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GroupDashboardService, GroupDashboard } from './group-dashboard.service';

/**
 * Vue Groupe — consolidated multi-société dashboard for group owners.
 *
 * One row per société where the user is ADMIN, plus group totals: revenue (CA confirmé),
 * stock + absorption, cash position and VEFA alerts. Read-only comparison view; managing a
 * société still goes through the société switch at login.
 */
@Component({
  selector: 'app-vue-groupe',
  standalone: true,
  imports: [DecimalPipe, RouterLink, TranslatePipe],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>{{ 'dashboard.vueGroupe.title' | translate }}</h1>
          <p class="sub">{{ 'dashboard.vueGroupe.sub' | translate }}</p>
        </div>
        <div class="head-actions">
          <a class="btn btn-sm btn-secondary" routerLink="/app/groupe/clients">{{ 'dashboard.vueGroupe.recurringClients' | translate }}</a>
          <button class="btn btn-sm btn-secondary" (click)="reload()" [title]="'dashboard.vueGroupe.refresh' | translate">{{ 'dashboard.vueGroupe.refresh' | translate }}</button>
        </div>
      </header>

      @if (loading()) {
        <div class="skeleton-grid" data-testid="groupe-loading-state">
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
        </div>
      } @else if (error()) {
        <div class="empty-state">
          <p>{{ error() }}</p>
          <div class="empty-state-actions">
            <button class="btn btn-secondary" (click)="reload()">{{ 'dashboard.vueGroupe.retry' | translate }}</button>
          </div>
        </div>
      }
      @if (data(); as d) {

        <!-- Group totals -->
        <section class="kpi-row" data-testid="groupe-totals">
          <div class="kpi-card good">
            <span class="kpi-label">{{ 'dashboard.vueGroupe.caConfirme' | translate }}</span>
            <span class="kpi-value">{{ d.totals.caConfirme | number:'1.0-0' }} <small>MAD</small></span>
            <span class="kpi-foot">{{ 'dashboard.vueGroupe.caEnPipeline' | translate:{ amount: (d.totals.caEnCours | number:'1.0-0') } }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">{{ 'dashboard.vueGroupe.encaisse' | translate }}</span>
            <span class="kpi-value">{{ d.totals.encaisseTotal | number:'1.0-0' }} <small>MAD</small></span>
            <span class="kpi-foot">{{ 'dashboard.vueGroupe.aEncaisser' | translate:{ amount: (d.totals.aEncaisser | number:'1.0-0') } }}</span>
          </div>
          <div class="kpi-card" [class.bad]="d.totals.enRetardCount > 0">
            <span class="kpi-label">{{ 'dashboard.vueGroupe.enRetard' | translate:{ count: d.totals.enRetardCount } }}</span>
            <span class="kpi-value">{{ d.totals.enRetardMontant | number:'1.0-0' }} <small>MAD</small></span>
            <span class="kpi-foot">{{ 'dashboard.vueGroupe.appelsEchus' | translate }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">{{ 'dashboard.vueGroupe.stockGroupe' | translate }}</span>
            <span class="kpi-value">{{ d.totals.unitsDisponibles }} <small>{{ 'dashboard.vueGroupe.disponibles' | translate }}</small></span>
            <span class="kpi-foot">{{ 'dashboard.vueGroupe.vendusAbsorption' | translate:{ sold: d.totals.unitsVendus, pct: (d.totals.absorptionPct | number:'1.0-1') } }}</span>
          </div>
        </section>

        <!-- Group alert strip -->
        @if (d.totals.optionsActives + d.totals.retractationsEnCours + d.totals.ventesStallees > 0) {
          <section class="alert-strip" data-testid="groupe-alerts">
            @if (d.totals.optionsActives > 0) {
              <span class="alert-chip">{{ 'dashboard.vueGroupe.optionsActives' | translate:{ count: d.totals.optionsActives } }}</span>
            }
            @if (d.totals.retractationsEnCours > 0) {
              <span class="alert-chip warn">{{ 'dashboard.vueGroupe.retractations' | translate:{ count: d.totals.retractationsEnCours } }}</span>
            }
            @if (d.totals.ventesStallees > 0) {
              <span class="alert-chip bad">{{ 'dashboard.vueGroupe.ventesBloquees' | translate:{ count: d.totals.ventesStallees } }}</span>
            }
          </section>
        }

        <!-- Per-société comparison -->
        <section class="card societes-card">
          <h2 class="card-title">{{ 'dashboard.vueGroupe.parSociete' | translate }} <span class="muted">({{ d.totals.societesCount }})</span></h2>
          <div class="table-wrap">
            <table class="table" data-testid="groupe-societes-table">
              <thead>
                <tr>
                  <th>{{ 'dashboard.vueGroupe.thSociete' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thStock' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thAbsorption' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thCaConfirme' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thPipeline' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thEncaisse' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thEnRetard' | translate }}</th>
                  <th class="num">{{ 'dashboard.vueGroupe.thAlertes' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (s of d.societes; track s.societeId) {
                  <tr>
                    <td class="nom">{{ s.nom }}</td>
                    <td class="num">{{ s.unitsDisponibles }} · {{ s.unitsReserves }} · {{ s.unitsVendus }}</td>
                    <td class="num">
                      <span class="absorption-pill" [class.high]="s.absorptionPct >= 60">
                        {{ s.absorptionPct | number:'1.0-1' }} %
                      </span>
                    </td>
                    <td class="num strong">{{ s.caConfirme | number:'1.0-0' }} MAD</td>
                    <td class="num">{{ s.caEnCours | number:'1.0-0' }} MAD</td>
                    <td class="num">{{ s.encaisseTotal | number:'1.0-0' }} MAD</td>
                    <td class="num">
                      @if (s.enRetardCount > 0) {
                        <span class="badge badge-error">{{ 'dashboard.vueGroupe.enRetardBadge' | translate:{ amount: (s.enRetardMontant | number:'1.0-0'), count: s.enRetardCount } }}</span>
                      } @else {
                        <span class="muted">—</span>
                      }
                    </td>
                    <td class="num">
                      @if (s.optionsActives + s.retractationsEnCours + s.ventesStallees > 0) {
                        <span class="muted">
                          {{ 'dashboard.vueGroupe.alertesShort' | translate:{ opt: s.optionsActives, retr: s.retractationsEnCours, bloq: s.ventesStallees } }}
                        </span>
                      } @else {
                        <span class="muted">—</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (d.societes.length === 1) {
            <p class="single-note">
              {{ 'dashboard.vueGroupe.singleNote' | translate }}
            </p>
          }
        </section>
      }
    </div>
  `,
  styles: [`
    .page { max-width: 1200px; margin: 0 auto; }
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
    .page-head h1 { margin: 0 0 4px; font-size: 1.4rem; }
    .sub { margin: 0; color: var(--c-text-secondary); font-size: 0.875rem; }

    .kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-bottom: 16px; }
    .kpi-card { background: var(--c-surface-raised); border: 1px solid var(--c-border); border-radius: 10px; padding: 14px 16px; display: flex; flex-direction: column; gap: 4px; }
    .kpi-card.good { border-color: var(--c-primary-200); background: var(--c-primary-50); }
    .kpi-card.bad  { border-color: var(--status-retire-bd); background: var(--status-retire-bg); }
    .kpi-label { font-size: 0.75rem; color: var(--c-text-secondary); }
    .kpi-value { font-size: 1.35rem; font-weight: 700; color: var(--c-text); }
    .kpi-value small { font-size: 0.75rem; font-weight: 500; color: var(--c-text-muted); }
    .kpi-foot { font-size: 0.75rem; color: var(--c-text-muted); }

    .alert-strip { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
    .alert-chip { font-size: 0.78rem; padding: 4px 10px; border-radius: 999px; background: var(--status-disponible-bg); color: var(--status-disponible-fg); border: 1px solid var(--status-disponible-bd); }
    .alert-chip.warn { background: var(--status-reserve-bg); color: var(--status-reserve-fg); border-color: var(--status-reserve-bd); }
    .alert-chip.bad  { background: var(--status-retire-bg);  color: var(--status-retire-fg);  border-color: var(--status-retire-bd); }

    .societes-card { padding: 16px; }
    .card-title { margin: 0 0 12px; font-size: 1rem; }
    .muted { color: var(--c-text-muted); font-weight: 400; }
    .table-wrap { overflow-x: auto; }
    th.num, td.num { text-align: right; white-space: nowrap; }
    td.nom { font-weight: 600; }
    td.strong { font-weight: 600; }
    .absorption-pill { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 0.78rem; background: var(--status-reserve-bg); color: var(--status-reserve-fg); }
    .absorption-pill.high { background: var(--status-vendu-bg); color: var(--status-vendu-fg); }
    .single-note { margin: 12px 0 0; font-size: 0.8rem; color: var(--c-text-muted); }
  `]
})
export class VueGroupeComponent implements OnInit {
  private i18n = inject(I18nService);
  private api = inject(GroupDashboardService);

  loading = signal(true);
  error = signal('');
  data = signal<GroupDashboard | null>(null);

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getDashboard().subscribe({
      next: d => { this.data.set(d); this.loading.set(false); },
      error: () => {
        this.error.set(this.i18n.instant('dashboard.vueGroupe.loadError'));
        this.loading.set(false);
      }
    });
  }
}
