import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HomeDashboardService, HomeDashboard, ProjectBreakdownRow, ShareholderKpi, ProjectDirectorKpi } from './home-dashboard.service';
import { MiniBarChartComponent } from './mini-bar-chart.component';
import { SalesByTypeComponent } from './cockpit/sales-by-type.component';
import { InventoryAgingComponent } from './cockpit/inventory-aging.component';
import { PricePerSqmComponent } from './cockpit/price-per-sqm.component';
import { TimeToCloseComponent } from './cockpit/time-to-close.component';
import { PortfolioValueComponent } from './cockpit/portfolio-value.component';
import {
  DashboardCockpitService,
  CockpitBundle,
  KpiDelta,
} from './dashboard-cockpit.service';
import { KpiDeltaChipComponent } from './cockpit/kpi-delta-chip.component';
import { SparklineComponent } from './cockpit/sparkline.component';
import { FunnelComponent } from './cockpit/funnel.component';
import { AlertsPanelComponent } from './cockpit/alerts-panel.component';
import { PipelineAnalysisComponent } from './cockpit/pipeline-analysis.component';
import { ForecastWidgetComponent } from './cockpit/forecast-widget.component';
import { AgentPerformanceComponent } from './cockpit/agent-performance.component';
import { InventoryIntelligenceComponent } from './cockpit/inventory-intelligence.component';
import { DiscountAnalyticsComponent } from './cockpit/discount-analytics.component';
import { InsightsPanelComponent } from './cockpit/insights-panel.component';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, DatePipe, DecimalPipe,
    KpiDeltaChipComponent, SparklineComponent, FunnelComponent, AlertsPanelComponent,
    PipelineAnalysisComponent, ForecastWidgetComponent, AgentPerformanceComponent,
    InventoryIntelligenceComponent, DiscountAnalyticsComponent, InsightsPanelComponent,
    MiniBarChartComponent, SalesByTypeComponent, InventoryAgingComponent,
    PricePerSqmComponent, TimeToCloseComponent, PortfolioValueComponent,
  ],
  templateUrl: './home-dashboard.component.html',
  styleUrl: './home-dashboard.component.css',
})
export class HomeDashboardComponent implements OnInit {
  private svc     = inject(HomeDashboardService);
  private cockpit = inject(DashboardCockpitService);
  private router  = inject(Router);
  readonly auth   = inject(AuthService);

  snap               = signal<HomeDashboard | null>(null);
  bundle             = signal<CockpitBundle | null>(null);
  loading            = signal(true);
  error              = signal('');
  shareholderKpi     = signal<ShareholderKpi | null>(null);
  projectDirectorKpi = signal<ProjectDirectorKpi | null>(null);

  private shareholderLoaded     = false;
  private projectDirectorLoaded = false;

  activeTab: 'synthese' | 'dirigeant' | 'commercial' | 'projets' | 'operationnel' | 'actionnaire' | 'chef-projet' = 'synthese';

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

  // ── Drill-through navigation helpers ──────────────────────────────────────

  drillVentes(statut?: string): void {
    const queryParams = statut ? { statut } : {};
    this.router.navigate(['/app/ventes'], { queryParams });
  }

  drillByAgent(agentId: string): void {
    this.router.navigate(['/app/ventes'], { queryParams: { agentId } });
  }

  drillProperties(status: string): void {
    this.router.navigate(['/app/properties'], { queryParams: { status } });
  }

  drillByProject(projectId: string, status?: string): void {
    const queryParams: Record<string, string> = { projectId };
    if (status) queryParams['status'] = status;
    this.router.navigate(['/app/properties'], { queryParams });
  }

  goToProject(projectId: string | null): void {
    if (projectId) this.router.navigate(['/app/projects', projectId]);
  }

  drillReceivables(): void {
    this.router.navigate(['/app/dashboard/receivables']);
  }

  switchTab(tab: HomeDashboardComponent['activeTab']): void {
    this.activeTab = tab;
    if (tab === 'actionnaire' && !this.shareholderLoaded) {
      this.shareholderLoaded = true;
      this.svc.getShareholderKpis().subscribe(k => this.shareholderKpi.set(k));
    }
    if (tab === 'chef-projet' && !this.projectDirectorLoaded) {
      this.projectDirectorLoaded = true;
      this.svc.getProjectDirectorKpis().subscribe(k => this.projectDirectorKpi.set(k));
    }
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

  funnelData()          { return this.bundle()?.funnel ?? null; }
  alertsList()          { return this.bundle()?.alerts ?? []; }
  pipelineData()        { return this.bundle()?.pipeline ?? null; }
  forecastData()        { return this.bundle()?.forecast ?? null; }
  agentPerformanceData(){ return this.bundle()?.agentPerformance ?? null; }
  inventoryData()       { return this.bundle()?.inventory ?? null; }
  discountData()        { return this.bundle()?.discount ?? null; }
  insightsList()        { return this.bundle()?.insights ?? []; }
  salesIntelData()      { return this.bundle()?.salesIntelligence ?? null; }

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

  // ── Executive view (Wave 13) ───────────────────────────────────────────────

  currentDayOfMonth(): number { return new Date().getDate(); }

  daysInCurrentMonth(): number {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  }

  /** Expected quota attainment pacing for today, as a percentage of the month. */
  pacingExpectedPct(): number {
    return Math.round((this.currentDayOfMonth() / this.daysInCurrentMonth()) * 100);
  }

  pacingWidth(pct: number | null): string {
    if (pct == null) return '0%';
    return Math.min(Math.max(pct, 0), 100) + '%';
  }

  /** Within 10 pts of expected pacing = on track. */
  isPacingOnTrack(pct: number | null): boolean {
    if (pct == null) return false;
    return pct >= this.pacingExpectedPct() - 10;
  }

  isPacingBehind(pct: number | null): boolean {
    if (pct == null) return false;
    const exp = this.pacingExpectedPct();
    return pct < exp - 10 && pct >= exp - 25;
  }

  isPacingCritical(pct: number | null): boolean {
    if (pct == null) return false;
    return pct < this.pacingExpectedPct() - 25;
  }

  pacingVerdict(pct: number | null): string {
    if (pct == null) return '—';
    const exp = this.pacingExpectedPct();
    if (pct >= exp + 10) return 'En avance';
    if (pct >= exp - 10) return 'Dans le rythme';
    if (pct >= exp - 25) return 'En retard';
    return 'Sous-performance critique';
  }

  absNum(n: number | null | undefined): number {
    if (n == null) return 0;
    return Math.abs(n);
  }

  /** Months of supply < 12 = healthy (fast sell-through). */
  isSupplyHealthy(m: number | null): boolean { return m != null && m < 12; }
  isSupplyElevated(m: number | null): boolean { return m != null && m >= 12 && m < 24; }
  isSupplyCritical(m: number | null): boolean { return m != null && m >= 24; }

  // ── Project breakdown helpers ──────────────────────────────────────────────

  projectBarWidth(row: ProjectBreakdownRow, allRows: ProjectBreakdownRow[]): string {
    const max = Math.max(...allRows.map(r => r.totalCA), 1);
    return Math.max((row.totalCA / max) * 100, 2) + '%';
  }

  totalProjectsCA(rows: ProjectBreakdownRow[]): number {
    return rows.reduce((acc, r) => acc + r.totalCA, 0);
  }

  projectSharePct(row: ProjectBreakdownRow, allRows: ProjectBreakdownRow[]): string {
    const total = this.totalProjectsCA(allRows);
    if (total === 0) return '0%';
    return ((row.totalCA / total) * 100).toFixed(0) + '%';
  }

  readonly PROJECT_COLORS = [
    '#2563eb','#7c3aed','#059669','#d97706','#dc2626',
    '#0891b2','#65a30d','#c026d3','#ea580c','#0f172a',
  ];

  projectColor(i: number): string {
    return this.PROJECT_COLORS[i % this.PROJECT_COLORS.length];
  }
}
