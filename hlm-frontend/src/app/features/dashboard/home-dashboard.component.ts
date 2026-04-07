import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CommercialDashboardService } from './commercial-dashboard.service';
import { CommercialDashboardSummary } from '../../core/models/commercial-dashboard.model';
import { VenteService, Vente } from '../ventes/vente.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Tableau de bord</h1>
        <p class="page-subtitle">Vue d'ensemble de votre activité commerciale</p>
      </div>
    </div>

    <!-- ── KPI cards ───────────────────────────────────────────────── -->
    @if (summary(); as s) {
      <div class="kpi-row">
        <div class="kpi-card kpi-sales">
          <div class="kpi-value">{{ s.salesCount }}</div>
          <div class="kpi-label">Ventes actives</div>
        </div>
        <div class="kpi-card kpi-deposits">
          <div class="kpi-value">{{ s.depositsCount }}</div>
          <div class="kpi-label">Acomptes</div>
        </div>
        <div class="kpi-card kpi-prospects">
          <div class="kpi-value">{{ s.activeProspectsCount }}</div>
          <div class="kpi-label">Prospects actifs</div>
        </div>
        <div class="kpi-card kpi-reservations">
          <div class="kpi-value">{{ s.activeReservationsCount }}</div>
          <div class="kpi-label">Réservations</div>
        </div>
      </div>
    }

    <!-- ── Quick actions ───────────────────────────────────────────── -->
    <div class="section-title">Accès rapides</div>
    <div class="shortcut-grid">
      <a routerLink="/app/ventes" class="shortcut-card">
        <div class="shortcut-icon sc-ventes">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path d="M3 19l4-4 4 3 4-6 4 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <span class="shortcut-label">Ventes</span>
      </a>
      <a routerLink="/app/contacts" class="shortcut-card">
        <div class="shortcut-icon sc-contacts">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <circle cx="11" cy="8" r="4" stroke="currentColor" stroke-width="1.8"/>
            <path d="M3 20c0-4 3.6-7 8-7s8 3 8 7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          </svg>
        </div>
        <span class="shortcut-label">Contacts</span>
      </a>
      <a routerLink="/app/properties" class="shortcut-card">
        <div class="shortcut-icon sc-properties">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path d="M3 10.5L11 3l8 7.5V20H14v-5h-4v5H3V10.5z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
          </svg>
        </div>
        <span class="shortcut-label">Biens</span>
      </a>
      <a routerLink="/app/reservations" class="shortcut-card">
        <div class="shortcut-icon sc-reservations">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <rect x="3" y="5" width="16" height="15" rx="2" stroke="currentColor" stroke-width="1.8"/>
            <path d="M15 3v4M7 3v4M3 10h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          </svg>
        </div>
        <span class="shortcut-label">Réservations</span>
      </a>
      <a routerLink="/app/projects" class="shortcut-card">
        <div class="shortcut-icon sc-projects">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.8"/>
            <rect x="12" y="3" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.8"/>
            <rect x="3" y="12" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.8"/>
            <rect x="12" y="12" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.8"/>
          </svg>
        </div>
        <span class="shortcut-label">Projets</span>
      </a>
      <a routerLink="/app/tasks" class="shortcut-card">
        <div class="shortcut-icon sc-tasks">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path d="M9 11l2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
            <rect x="3" y="3" width="16" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/>
          </svg>
        </div>
        <span class="shortcut-label">Tâches</span>
      </a>
    </div>

    <!-- ── Recent ventes ────────────────────────────────────────────── -->
    <div class="section-title">Ventes récentes</div>
    @if (ventesLoading()) {
      <div class="loading-wrap"><div class="spinner"></div> Chargement…</div>
    }
    @if (!ventesLoading() && recentVentes().length === 0) {
      <div class="card">
        <div class="empty-state card-body">
          <p class="empty-state-title">Aucune vente enregistrée</p>
          @if (canWrite) {
            <a routerLink="/app/ventes" class="btn btn-primary">Créer une vente</a>
          }
        </div>
      </div>
    }
    @if (recentVentes().length > 0) {
      <div class="card">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Acquéreur</th>
                <th>Statut</th>
                <th>Prix</th>
                <th>Date</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (v of recentVentes(); track v.id) {
                <tr>
                  <td>{{ v.contactFullName || '—' }}</td>
                  <td>
                    <span class="badge" [class]="statutClass(v.statut)">{{ statutLabel(v.statut) }}</span>
                  </td>
                  <td class="td-num">
                    {{ v.prixVente ? ((v.prixVente | number:'1.0-0') + ' MAD') : '—' }}
                  </td>
                  <td class="td-muted">{{ v.createdAt | date:'dd/MM/yyyy' }}</td>
                  <td>
                    <a [routerLink]="['/app/ventes', v.id]" class="btn btn-sm btn-secondary">Détail</a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="card-footer-link">
          <a routerLink="/app/ventes">Voir toutes les ventes →</a>
        </div>
      </div>
    }

    <!-- ── Dashboard link ────────────────────────────────────────────── -->
    <div class="dashboard-links">
      <a routerLink="/app/dashboard/commercial" class="dashboard-link-card">
        Tableau de bord commercial complet →
      </a>
    </div>
  `,
  styles: [`
    .kpi-row {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 16px;
      margin-bottom: 32px;
    }
    .kpi-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 20px 16px;
      text-align: center;
    }
    .kpi-value {
      font-size: 2rem;
      font-weight: 700;
      line-height: 1;
      margin-bottom: 6px;
    }
    .kpi-label { font-size: .8rem; color: var(--muted); font-weight: 500; }
    .kpi-sales        .kpi-value { color: #6366f1; }
    .kpi-deposits     .kpi-value { color: #f59e0b; }
    .kpi-prospects    .kpi-value { color: #10b981; }
    .kpi-reservations .kpi-value { color: #3b82f6; }

    .section-title {
      font-size: .75rem;
      font-weight: 600;
      letter-spacing: .08em;
      text-transform: uppercase;
      color: var(--muted);
      margin: 0 0 12px;
    }
    .shortcut-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
      gap: 12px;
      margin-bottom: 32px;
    }
    .shortcut-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      padding: 20px 12px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 10px;
      text-decoration: none;
      color: var(--text);
      font-size: .85rem;
      font-weight: 500;
      transition: border-color .15s, box-shadow .15s;
    }
    .shortcut-card:hover { border-color: var(--primary); box-shadow: 0 2px 8px rgba(99,102,241,.12); }
    .shortcut-icon {
      width: 44px; height: 44px;
      border-radius: 10px;
      display: flex; align-items: center; justify-content: center;
    }
    .sc-ventes       { background: #ede9fe; color: #6d28d9; }
    .sc-contacts     { background: #d1fae5; color: #065f46; }
    .sc-properties   { background: #dbeafe; color: #1d4ed8; }
    .sc-reservations { background: #fef3c7; color: #92400e; }
    .sc-projects     { background: #fce7f3; color: #9d174d; }
    .sc-tasks        { background: #e0f2fe; color: #0369a1; }

    .card-footer-link {
      padding: 12px 20px;
      border-top: 1px solid var(--border);
      font-size: .85rem;
    }
    .card-footer-link a { color: var(--primary); text-decoration: none; font-weight: 500; }
    .card-footer-link a:hover { text-decoration: underline; }

    .dashboard-links { margin-top: 16px; }
    .dashboard-link-card {
      display: inline-block;
      padding: 10px 18px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 8px;
      text-decoration: none;
      color: var(--primary);
      font-size: .85rem;
      font-weight: 500;
    }
    .dashboard-link-card:hover { border-color: var(--primary); }

    .td-num  { font-variant-numeric: tabular-nums; }
    .td-muted { color: var(--muted); font-size: .85rem; }
  `],
})
export class HomeDashboardComponent implements OnInit {
  private dashSvc  = inject(CommercialDashboardService);
  private venteSvc = inject(VenteService);
  private auth     = inject(AuthService);

  summary      = signal<CommercialDashboardSummary | null>(null);
  recentVentes = signal<Vente[]>([]);
  ventesLoading = signal(true);

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    const today = new Date();
    const from = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().slice(0, 10);
    const to   = today.toISOString().slice(0, 10);

    this.dashSvc.getSummary({ from, to }).subscribe({
      next:  (s) => this.summary.set(s),
      error: ()  => {},
    });

    this.venteSvc.list().subscribe({
      next:  (all) => {
        this.recentVentes.set(all.slice(0, 5));
        this.ventesLoading.set(false);
      },
      error: () => this.ventesLoading.set(false),
    });
  }

  statutLabel(s: string): string {
    const labels: Record<string, string> = {
      COMPROMIS: 'Compromis', FINANCEMENT: 'Financement',
      ACTE_NOTARIE: 'Acte notarié', LIVRE: 'Livré', ANNULE: 'Annulé',
    };
    return labels[s] ?? s;
  }

  statutClass(s: string): string {
    const classes: Record<string, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE_NOTARIE: 'badge-primary', LIVRE: 'badge-success', ANNULE: 'badge-error',
    };
    return classes[s] ?? '';
  }
}
