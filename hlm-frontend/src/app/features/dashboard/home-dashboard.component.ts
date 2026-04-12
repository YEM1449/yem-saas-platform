import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HomeDashboardService, HomeDashboard } from './home-dashboard.service';
import {
  DashboardCockpitService,
  CockpitBundle,
  KpiDelta,
} from './dashboard-cockpit.service';
import { KpiDeltaChipComponent } from './cockpit/kpi-delta-chip.component';
import { SparklineComponent } from './cockpit/sparkline.component';
import { FunnelComponent } from './cockpit/funnel.component';
import { AlertsPanelComponent } from './cockpit/alerts-panel.component';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, DatePipe, DecimalPipe,
    KpiDeltaChipComponent, SparklineComponent, FunnelComponent, AlertsPanelComponent,
  ],
  templateUrl: './home-dashboard.component.html',
  styleUrl: './home-dashboard.component.css',
})
export class HomeDashboardComponent implements OnInit {
  private svc     = inject(HomeDashboardService);
  private cockpit = inject(DashboardCockpitService);
  readonly auth   = inject(AuthService);

  snap    = signal<HomeDashboard | null>(null);
  bundle  = signal<CockpitBundle | null>(null);
  loading = signal(true);
  error   = signal('');

  readonly today = new Date().toLocaleDateString('fr-FR', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
  });

  get role(): string { return this.auth.user?.role ?? ''; }
  get isAgent(): boolean { return this.role === 'ROLE_AGENT'; }
  get isAdminOrManager(): boolean {
    return this.role === 'ROLE_ADMIN' || this.role === 'ROLE_MANAGER';
  }
  get userName(): string {
    const u = this.auth.user;
    return u?.prenom ?? u?.email?.split('@')[0] ?? '';
  }

  ngOnInit(): void { this.load(); }

  reload(): void {
    this.error.set('');
    this.loading.set(true);
    this.load();
  }

  private load(): void {
    this.svc.getSnapshot().subscribe({
      next:  s  => { this.snap.set(s); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger le tableau de bord.'); this.loading.set(false); },
    });
    if (this.isAdminOrManager) {
      this.cockpit.getBundle().subscribe(b => this.bundle.set(b));
    }
  }

  // ── Cockpit helpers (KPI deltas + sparklines + alerts) ─────────────────────

  caDelta(): KpiDelta | null            { return this.bundle()?.kpi?.caSigne ?? null; }
  ventesDelta(): KpiDelta | null        { return this.bundle()?.kpi?.ventesCreated ?? null; }
  reservationsDelta(): KpiDelta | null  { return this.bundle()?.kpi?.reservations ?? null; }
  encaisseDelta(): KpiDelta | null      { return this.bundle()?.kpi?.encaisse ?? null; }

  caSparkline()     { return this.bundle()?.kpi?.caSparkline     ?? []; }
  ventesSparkline() { return this.bundle()?.kpi?.ventesSparkline ?? []; }

  funnelData()      { return this.bundle()?.funnel ?? null; }
  alertsList()      { return this.bundle()?.alerts ?? []; }

  formatDeltaPrev(d: KpiDelta | null): string {
    if (!d) return '';
    return this.formatAmount(d.previous);
  }

  // ── Statut labels/colors ────────────────────────────────────────────────────

  readonly STATUT_LABELS: Record<string, string> = {
    COMPROMIS: 'Compromis', FINANCEMENT: 'Financement',
    ACTE_NOTARIE: 'Acte notarié', LIVRE: 'Livré', ANNULE: 'Annulé',
  };

  readonly STATUT_COLORS: Record<string, string> = {
    COMPROMIS: '#6366f1', FINANCEMENT: '#f59e0b',
    ACTE_NOTARIE: '#3b82f6', LIVRE: '#10b981', ANNULE: '#ef4444',
  };

  statutLabel(s: string): string { return this.STATUT_LABELS[s] ?? s; }
  statutColor(s: string): string { return this.STATUT_COLORS[s] ?? '#9ca3af'; }

  statutClass(s: string): string {
    const m: Record<string, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE_NOTARIE: 'badge-primary', LIVRE: 'badge-success', ANNULE: 'badge-error',
    };
    return 'badge ' + (m[s] ?? '');
  }

  pipelineEntries(v: Record<string, number>): { statut: string; count: number }[] {
    return ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE']
      .filter(s => v[s] != null)
      .map(s => ({ statut: s, count: v[s] }));
  }

  // ── Absorption helpers ──────────────────────────────────────────────────────

  absorptionWidth(r: number | null): string {
    if (r == null) return '0%';
    return Math.min(r, 100) + '%';
  }

  absorptionClass(r: number | null): string {
    if (r == null) return '';
    if (r >= 70) return 'bar-success';
    if (r >= 40) return 'bar-warning';
    return 'bar-danger';
  }

  absorptionLabel(r: number | null): string {
    if (r == null) return '';
    if (r >= 70) return 'Forte commercialisation';
    if (r >= 40) return 'Commercialisation active';
    return 'Stock disponible important';
  }

  // ── Monthly trend ──────────────────────────────────────────────────────────

  /** Returns percentage change current vs previous month (null if no previous). */
  moisTrend(current: number, previous: number): number | null {
    if (previous === 0) return null;
    return Math.round((current - previous) / previous * 100);
  }

  isTrendUp(current: number, previous: number): boolean {
    return current > previous;
  }

  // ── Formatting helpers ─────────────────────────────────────────────────────

  formatAmount(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }

  formatAmountShort(n: number | null | undefined): string {
    if (n == null || n === 0) return '0';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K';
    return n.toLocaleString('fr-FR');
  }

  isOverdue(d: string): boolean { return new Date(d) < new Date(); }

  // ── Alert counts ───────────────────────────────────────────────────────────

  totalAlerts(s: HomeDashboard): number {
    return (s.overdueTasksCount > 0 ? 1 : 0)
         + (s.reservationsExpirantBientot > 0 ? 1 : 0)
         + (s.echeancesEnRetardCount > 0 ? 1 : 0)
         + (s.ventesStalleesCount > 0 ? 1 : 0);
  }
}
