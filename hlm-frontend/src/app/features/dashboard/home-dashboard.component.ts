import { Component, inject, OnInit, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HomeDashboardService, HomeDashboard, ProjectBreakdownRow, TrancheBreakdownRow, ImmeubleBreakdownRow, InventoryTypeRow, PipelineStageAgingRow, TypeVelocityRow, ShareholderKpi, ProjectDirectorKpi, AgentLeaderboardRow, ProjectProgressRow } from './home-dashboard.service';
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
import { FunnelComponent } from './cockpit/funnel.component';
import { AlertsPanelComponent } from './cockpit/alerts-panel.component';
import { PipelineAnalysisComponent } from './cockpit/pipeline-analysis.component';
import { ForecastWidgetComponent } from './cockpit/forecast-widget.component';
import { AgentPerformanceComponent } from './cockpit/agent-performance.component';
import { InventoryIntelligenceComponent } from './cockpit/inventory-intelligence.component';
import { DiscountAnalyticsComponent } from './cockpit/discount-analytics.component';
import { InsightsPanelComponent } from './cockpit/insights-panel.component';
import { ShortcutGridComponent } from './shortcut-grid.component';
import { VentesRecentesComponent } from './ventes-recentes.component';
import { AuthService } from '../../core/auth/auth.service';
import { KPI_TARGETS, toneFor } from '../../core/config/kpi-targets';

/** One actionable line in the director's morning triage worklist. */
interface DayPriority {
  severity: 'critical' | 'warning' | 'info';
  icon: string;
  text: string;
  cta: string;
  nav: () => void;
}

// i18n
@Component({
  selector: 'app-home-dashboard',
  standalone: true,
  imports: [
    RouterLink, DatePipe, DecimalPipe,
    KpiDeltaChipComponent, FunnelComponent, AlertsPanelComponent,
    PipelineAnalysisComponent, ForecastWidgetComponent, AgentPerformanceComponent,
    InventoryIntelligenceComponent, DiscountAnalyticsComponent, InsightsPanelComponent,
    MiniBarChartComponent, SalesByTypeComponent, InventoryAgingComponent,
    PricePerSqmComponent, TimeToCloseComponent, PortfolioValueComponent, LowerCasePipe,
    ShortcutGridComponent, VentesRecentesComponent, TranslatePipe],
  templateUrl: './home-dashboard.component.html',
  styleUrl: './home-dashboard.component.css',
})
export class HomeDashboardComponent implements OnInit {
  private i18n = inject(I18nService);
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

  /** Wave 16 — open the visites agenda from the dashboard KPI. */
  goToVisites(): void {
    this.router.navigate(['/app/visites']);
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
      this.svc.getShareholderKpis().subscribe({
        next:  k  => this.shareholderKpi.set(k),
        error: () => { this.shareholderLoaded = false; },
      });
    }
    if (tab === 'chef-projet' && !this.projectDirectorLoaded) {
      this.projectDirectorLoaded = true;
      this.svc.getProjectDirectorKpis().subscribe({
        next:  k  => this.projectDirectorKpi.set(k),
        error: () => { this.projectDirectorLoaded = false; },
      });
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

  // ── Director morning triage (workflow-first) ──────────────────────────────
  // One synthesised worklist of the decisions a director acts on first, pulled
  // from signals otherwise scattered across tabs (collections, the single most
  // stalled deal, weakest-absorbing project, overdue tasks). Each line drills
  // straight to where the work happens — a worklist, not another KPI grid.

  /** The weakest-absorbing project with meaningful stock, or null. */
  private weakestAbsorptionProject() {
    const byProject = this.inventoryData()?.byProject ?? [];
    const candidates = byProject.filter(p => p.total >= 5 && p.absorptionRate != null && p.absorptionRate < 35);
    if (candidates.length === 0) return null;
    return candidates.reduce((min, p) => (p.absorptionRate! < min.absorptionRate! ? p : min));
  }

  dayPriorities(): DayPriority[] {
    const s = this.snap();
    if (!s) return [];
    const out: DayPriority[] = [];

    if (s.echeancesEnRetardCount > 0) {
      out.push({
        severity: 'critical', icon: '💸',
        text: this.i18n.instant('dashboard.home.alert.receivables', { count: s.echeancesEnRetardCount, amount: this.formatAmount(s.echeancesEnRetardMontant) }),
        cta: this.i18n.instant('dashboard.home.cta.encaissements'),
        nav: () => this.drillReceivables(),
      });
    }

    const atRisk = this.pipelineData()?.atRiskDeals ?? [];
    if (atRisk.length > 0) {
      const d = atRisk[0];
      out.push({
        severity: 'critical', icon: '⏳',
        text: this.i18n.instant('dashboard.home.alert.atRisk', { ref: d.venteRef, contact: d.contactFullName, days: d.agingDays, amount: this.formatAmount(d.prixVente) }),
        cta: this.i18n.instant('dashboard.home.cta.ouvrir'),
        nav: () => this.router.navigate(['/app/ventes', d.venteId]),
      });
    }

    if (s.ventesStalleesCount > 0) {
      out.push({
        severity: 'warning', icon: '🛑',
        text: this.i18n.instant('dashboard.home.alert.stalled', { count: s.ventesStalleesCount }),
        cta: this.i18n.instant('dashboard.home.cta.voir'),
        nav: () => this.drillVentes(),
      });
    }

    const weakest = this.weakestAbsorptionProject();
    if (weakest) {
      out.push({
        severity: 'warning', icon: '📉',
        text: this.i18n.instant('dashboard.home.alert.weakAbsorption', { project: weakest.projectName, pct: Math.round(weakest.absorptionRate!), available: weakest.available, total: weakest.total }),
        cta: this.i18n.instant('dashboard.home.cta.analyser'),
        nav: () => this.drillByProject(weakest.projectId),
      });
    }

    if (s.overdueTasksCount > 0) {
      out.push({
        severity: 'warning', icon: '⏰',
        text: this.i18n.instant('dashboard.home.alert.overdueTasks', { count: s.overdueTasksCount }),
        cta: this.i18n.instant('dashboard.home.cta.traiter'),
        nav: () => { this.activeTab = 'operationnel'; },
      });
    }

    const rank = { critical: 0, warning: 1, info: 2 };
    return out.sort((a, b) => rank[a.severity] - rank[b.severity]);
  }

  /** Agent worklist — their own tasks and reservations, not société financials. */
  agentPriorities(): DayPriority[] {
    const s = this.snap();
    if (!s) return [];
    const out: DayPriority[] = [];

    if (s.overdueTasksCount > 0) {
      out.push({
        severity: 'critical', icon: '⏰',
        text: this.i18n.instant('dashboard.home.alert.overdueTasks', { count: s.overdueTasksCount }),
        cta: this.i18n.instant('dashboard.home.cta.traiter'),
        nav: () => this.router.navigate(['/app/tasks']),
      });
    }
    if (s.tasksDueTodayCount > 0) {
      out.push({
        severity: 'warning', icon: '✅',
        text: this.i18n.instant('dashboard.home.alert.tasksDueToday', { count: s.tasksDueTodayCount }),
        cta: this.i18n.instant('dashboard.home.cta.voir'),
        nav: () => this.router.navigate(['/app/tasks']),
      });
    }
    if (s.reservationsExpirantBientot > 0) {
      out.push({
        severity: 'warning', icon: '⌛',
        text: this.i18n.instant('dashboard.home.alert.reservationsExpiring', { count: s.reservationsExpirantBientot }),
        cta: this.i18n.instant('dashboard.home.cta.relancer'),
        nav: () => this.router.navigate(['/app/reservations']),
      });
    }

    if (out.length === 0) {
      out.push({ severity: 'info', icon: '✓', text: this.i18n.instant('dashboard.home.alert.upToDate'), cta: '', nav: () => {} });
    }
    const rank = { critical: 0, warning: 1, info: 2 };
    return out.sort((a, b) => rank[a.severity] - rank[b.severity]);
  }

  /** Role-appropriate worklist for the Synthèse landing. */
  priorities(): DayPriority[] {
    return this.isAdminOrManager ? this.dayPriorities() : this.agentPriorities();
  }

  // ── Sales Director cockpit (Commercial tab) ───────────────────────────────
  salesPipelineWeighted(): number { return this.pipelineData()?.totalWeightedValue ?? 0; }
  salesForecast30(): number { return this.forecastData()?.next30Days ?? 0; }
  salesAtRiskCount(): number { return this.pipelineData()?.atRiskDeals?.length ?? 0; }
  salesAtRiskValue(): number {
    return (this.pipelineData()?.atRiskDeals ?? []).reduce((s, d) => s + (d.weightedValue || 0), 0);
  }
  /** Stalled deals, oldest first — the Sales Director's intervention list. */
  salesAtRiskList() {
    return [...(this.pipelineData()?.atRiskDeals ?? [])]
      .sort((a, b) => b.agingDays - a.agingDays)
      .slice(0, 5);
  }
  /** Overall funnel conversion: last stage / first stage ×100. */
  salesFunnelConversion(): number | null {
    const stages = this.funnelData()?.stages ?? [];
    if (stages.length < 2) return null;
    const first = stages[0].count;
    const last = stages[stages.length - 1].count;
    return first > 0 ? Math.round((last / first) * 100) : null;
  }

  // ── CEO / Direction cockpit (Dirigeant tab) ───────────────────────────────
  quotaTone(): string        { return toneFor(this.snap()?.quotaAttainmentMtdPct, KPI_TARGETS.quotaAttainmentPct); }
  cancellationTone(): string { return toneFor(this.snap()?.cancellationRate90d, KPI_TARGETS.cancellationPct); }
  ceoAbsorptionTone(): string { return toneFor(this.snap()?.tauxAbsorption, KPI_TARGETS.absorptionPct); }
  /** Projects dragging the portfolio — below the absorption target, worst first. */
  ceoUnderperformingProjects() {
    return (this.inventoryData()?.byProject ?? [])
      .filter(p => p.total >= 5 && p.absorptionRate != null && p.absorptionRate < KPI_TARGETS.absorptionPct.target)
      .sort((a, b) => (a.absorptionRate ?? 0) - (b.absorptionRate ?? 0))
      .slice(0, 5);
  }
  readonly ABSORPTION_TARGET = KPI_TARGETS.absorptionPct.target;
  readonly QUOTA_TARGET = KPI_TARGETS.quotaAttainmentPct.target;
  readonly CANCELLATION_TARGET = KPI_TARGETS.cancellationPct.target;

  // ── Tunnel de valeur — les 3 clôtures du cycle (depuis pipelineData) ──────
  // CA en cours (signature) → CA acté (clôture commerciale, acte notarié)
  //                         → CA livré (clôture livraison, réalisé).
  private stageRaw(statut: string): number {
    return (this.pipelineData()?.stages ?? []).find(st => st.statut === statut)?.rawValue ?? 0;
  }
  /** Valeur des ventes encore au compromis/financement (pas encore actées). */
  caEnCours(): number { return this.stageRaw('COMPROMIS') + this.stageRaw('FINANCEMENT'); }
  /** Valeur actée (acte notarié signé) mais pas encore livrée — clôture commerciale. */
  caActe(): number { return this.stageRaw('ACTE'); }

  formatDeltaPrev(d: KpiDelta | null): string {
    if (!d) return '';
    return this.formatAmount(d.previous);
  }

  // ── Statut labels/colors ────────────────────────────────────────────────────



  readonly STATUT_COLORS: Record<string, string> = {
    COMPROMIS: '#6366f1', FINANCEMENT: '#f59e0b',
    ACTE: '#3b82f6', LIVRE_DEFINITIF: '#10b981', ANNULE: '#ef4444',
  };

  statutLabel(s: string): string { return this.i18n.instant('ventes.statut.' + s); }
  statutColor(s: string): string { return this.STATUT_COLORS[s] ?? '#9ca3af'; }

  statutClass(s: string): string {
    const m: Record<string, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE: 'badge-primary', LIVRE_DEFINITIF: 'badge-success', ANNULE: 'badge-error',
    };
    return 'badge ' + (m[s] ?? '');
  }

  pipelineEntries(v: Record<string, number>): { statut: string; count: number }[] {
    return ['COMPROMIS', 'FINANCEMENT', 'ACTE']
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
    if (r >= 70) return this.i18n.instant('dashboard.home.rating.absorptionStrong');
    if (r >= 40) return this.i18n.instant('dashboard.home.rating.absorptionActive');
    return this.i18n.instant('dashboard.home.rating.absorptionStock');
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

  // ── Hero "Month at a Glance" helpers ──────────────────────────────────────

  currentMonthLabel(): string {
    return new Date().toLocaleDateString(this.i18n.activeLang(), { month: 'long', year: 'numeric' });
  }

  quotaProgressPct(s: HomeDashboard): number {
    if (!s.caMensuelCible || s.caMensuelCible <= 0) return 0;
    return Math.min(Math.round((s.caSigneMoisCourant / s.caMensuelCible) * 100), 100);
  }

  private readonly STAGE_FLOW_ORDER = ['COMPROMIS', 'FINANCEMENT', 'ACTE', 'LIVRE_DEFINITIF'];

  private readonly STAGE_FLOW_COLORS: Record<string, string> = {
    COMPROMIS:    '#c2410c',
    FINANCEMENT:  '#a16207',
    ACTE: '#15803d',
    LIVRE_DEFINITIF:        '#15803d',
  };

  pipelineStageFlow(s: HomeDashboard): { key: string; label: string; count: number; amount: number; color: string; flex: number }[] {
    const agingMap = new Map(s.pipelineStageAging.map(r => [r.statut, r]));
    const statuts = s.ventesParStatut ?? {};
    const result = this.STAGE_FLOW_ORDER
      .filter(k => (statuts[k] ?? 0) > 0 || agingMap.has(k))
      .map(k => ({
        key:    k,
        label:  this.i18n.instant('dashboard.home.stageFlow.' + k),
        count:  statuts[k] ?? agingMap.get(k)?.count ?? 0,
        amount: agingMap.get(k)?.totalValue ?? 0,
        color:  this.STAGE_FLOW_COLORS[k] ?? '#64748b',
        flex:   0,
      }));
    if (result.length === 0) return result;
    const totalCount = result.reduce((a, r) => a + r.count, 0);
    result.forEach(r => { r.flex = Math.max((r.count / totalCount), 0.08); });
    return result;
  }

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
    if (pct >= exp + 10) return this.i18n.instant('dashboard.home.pacingVerdict.ahead');
    if (pct >= exp - 10) return this.i18n.instant('dashboard.home.pacingVerdict.onTrack');
    if (pct >= exp - 25) return this.i18n.instant('dashboard.home.pacingVerdict.behind');
    return this.i18n.instant('dashboard.home.pacingVerdict.critical');
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
    '#16a34a','#7c3aed','#059669','#d97706','#dc2626',
    '#0891b2','#65a30d','#c026d3','#ea580c','#0f172a'];

  projectColor(i: number): string {
    return this.PROJECT_COLORS[i % this.PROJECT_COLORS.length];
  }

  // ── Tranche / immeuble breakdown helpers ──────────────────────────────────

  bdBarWidth(ca: number, allRows: { totalCA: number }[]): string {
    const max = Math.max(...allRows.map(r => r.totalCA), 1);
    return Math.max((ca / max) * 100, 2) + '%';
  }

  bdSharePct(ca: number, allRows: { totalCA: number }[]): string {
    const total = allRows.reduce((s, r) => s + r.totalCA, 0);
    if (total === 0) return '0%';
    return ((ca / total) * 100).toFixed(0) + '%';
  }

  /** Group rows by projectName, preserving insertion order */
  groupByProject<T extends { projectName: string }>(rows: T[]): { projectName: string; items: T[] }[] {
    const map = new Map<string, T[]>();
    for (const r of rows) {
      if (!map.has(r.projectName)) map.set(r.projectName, []);
      map.get(r.projectName)!.push(r);
    }
    return Array.from(map.entries()).map(([projectName, items]) => ({ projectName, items }));
  }

  // ── Inventory-by-type helpers ──────────────────────────────────────────────

  typeLabel(type: string): string {
    return this.i18n.instant('dashboard.home.type.' + type);
  }

  typeAbsorptionWidth(row: InventoryTypeRow): string {
    if (!row.absorptionRate) return '0%';
    return Math.min(row.absorptionRate, 100) + '%';
  }

  typeAbsorptionClass(rate: number | null): string {
    if (rate == null) return '';
    if (rate >= 70) return 'bar-success';
    if (rate >= 40) return 'bar-warning';
    return 'bar-danger';
  }

  /** Sort types: residential first, then commercial/land/parking */
  sortedInventoryTypes(rows: InventoryTypeRow[]): InventoryTypeRow[] {
    const order = ['APPARTEMENT','T2','T3','STUDIO','VILLA','DUPLEX','COMMERCE','LOT','TERRAIN_VIERGE','PARKING'];
    return [...rows].sort((a, b) => {
      const ai = order.indexOf(a.type), bi = order.indexOf(b.type);
      return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
    });
  }

  // ── Pipeline stage aging helpers ──────────────────────────────────────────

  agingClass(avgDays: number): string {
    if (avgDays <= 15) return 'aging-ok';
    if (avgDays <= 30) return 'aging-warn';
    return 'aging-bad';
  }

  stalledClass(stalled: number, total: number): string {
    if (stalled === 0) return 'stalled-ok';
    const pct = stalled / total;
    return pct >= 0.5 ? 'stalled-bad' : 'stalled-warn';
  }

  pipelineTotalValue(rows: PipelineStageAgingRow[]): number {
    return rows.reduce((s, r) => s + r.totalValue, 0);
  }

  pipelineTotalCount(rows: PipelineStageAgingRow[]): number {
    return rows.reduce((s, r) => s + r.count, 0);
  }

  pipelineTotalStalled(rows: PipelineStageAgingRow[]): number {
    return rows.reduce((s, r) => s + r.stalled30dCount, 0);
  }

  pipelineWeightedAvgDays(rows: PipelineStageAgingRow[]): number {
    const total = this.pipelineTotalCount(rows);
    if (!total) return 0;
    return rows.reduce((s, r) => s + r.avgDays * r.count, 0) / total;
  }

  /** Cap days at 60 to produce 0-100 bar width percentage */
  agingBarPct(days: number): number {
    return Math.min(days / 60, 1) * 100;
  }

  // ── Type velocity helpers ──────────────────────────────────────────────────

  /** Look up velocity row for a given type key */
  velocityForType(type: string, rows: TypeVelocityRow[]): TypeVelocityRow | undefined {
    return rows.find(r => r.type === type);
  }

  velocityDaysClass(days: number | null | undefined): string {
    if (days == null) return '';
    if (days <= 30)  return 'vel-fast';
    if (days <= 90)  return 'vel-normal';
    return 'vel-slow';
  }

  // ── Decision Tag helpers (Wave 18) ─────────────────────────────────────────

  avgPipelineDeal(s: HomeDashboard): string {
    if (!s.activeVentesCount) return '—';
    return this.formatAmount(s.caActivePipeline / s.activeVentesCount);
  }

  annualizedCA(s: HomeDashboard): string {
    return this.formatAmount(s.caSigneMoisCourant * 12);
  }

  livraisonRatioPct(s: HomeDashboard): string {
    const total = s.caActivePipeline + s.caLivre;
    if (!total) return '—';
    return ((s.caLivre / total) * 100).toFixed(0) + '%';
  }

  projectedYearEndCA(s: HomeDashboard): string {
    const now = new Date();
    const startOfYear = new Date(now.getFullYear(), 0, 1);
    const dayOfYear = Math.floor((now.getTime() - startOfYear.getTime()) / 86_400_000) + 1;
    if (!dayOfYear || !s.caYtd) return '—';
    return this.formatAmount((s.caYtd / dayOfYear) * 365);
  }

  stockDaysToEmpty(s: HomeDashboard): number | null {
    if (!s.salesVelocityPerWeek) return null;
    return Math.round(s.biensActifsCount / (s.salesVelocityPerWeek / 7));
  }

  absorptionBenchClass(s: HomeDashboard): string {
    const cls = this.absorptionClass(s.tauxAbsorption);
    if (cls === 'bar-success') return 'bench-good';
    if (cls === 'bar-warning') return 'bench-warn';
    return 'bench-bad';
  }

  dsoStatusClass(s: HomeDashboard): string {
    const d = s.dsoRolling90d;
    if (d == null) return 'bench-info';
    if (d <= 15) return 'bench-good';
    if (d <= 45) return 'bench-warn';
    return 'bench-bad';
  }

  dsoLabel(s: HomeDashboard): string {
    const d = s.dsoRolling90d;
    if (d == null) return '—';
    if (d <= 15) return this.i18n.instant('dashboard.home.rating.excellent');
    if (d <= 45) return this.i18n.instant('dashboard.home.rating.correct');
    return this.i18n.instant('dashboard.home.rating.critique');
  }

  winRateStatusClass(s: HomeDashboard): string {
    const w = s.winRate90d;
    if (w == null) return 'bench-info';
    if (w >= 80) return 'bench-good';
    if (w >= 40) return 'bench-warn';
    return 'bench-bad';
  }

  winRateLabel(s: HomeDashboard): string {
    const w = s.winRate90d;
    if (w == null) return '—';
    if (w >= 80) return this.i18n.instant('dashboard.home.rating.excellent');
    if (w >= 40) return this.i18n.instant('dashboard.home.rating.correct');
    return this.i18n.instant('dashboard.home.rating.critique');
  }

  collectionStatusClass(s: HomeDashboard): string {
    const c = s.collectionEfficiency90d;
    if (c == null) return 'bench-info';
    if (c >= 90) return 'bench-good';
    if (c >= 75) return 'bench-warn';
    return 'bench-bad';
  }

  collectionLabel(s: HomeDashboard): string {
    const c = s.collectionEfficiency90d;
    if (c == null) return '—';
    if (c >= 90) return this.i18n.instant('dashboard.home.rating.cibleOk');
    if (c >= 75) return this.i18n.instant('dashboard.home.rating.correct');
    return this.i18n.instant('dashboard.home.rating.aAmeliorer');
  }

  cancellationStatusClass(s: HomeDashboard): string {
    const r = s.cancellationRate90d;
    if (r == null || r <= 10) return 'bench-good';
    if (r <= 20) return 'bench-warn';
    return 'bench-bad';
  }

  cancellationLabel(s: HomeDashboard): string {
    const r = s.cancellationRate90d;
    if (r == null || r <= 10) return this.i18n.instant('dashboard.home.rating.normal');
    if (r <= 20) return this.i18n.instant('dashboard.home.rating.attention');
    return this.i18n.instant('dashboard.home.rating.critique');
  }

  conversionStatusClass(s: HomeDashboard): string {
    const c = s.conversionRate30d;
    if (c == null) return 'bench-info';
    if (c >= 50) return 'bench-good';
    if (c >= 25) return 'bench-warn';
    return 'bench-bad';
  }

  conversionLabel(s: HomeDashboard): string {
    const c = s.conversionRate30d;
    if (c == null) return '—';
    if (c >= 50) return this.i18n.instant('dashboard.home.rating.conversionGood');
    if (c >= 25) return this.i18n.instant('dashboard.home.rating.conversionOk');
    return this.i18n.instant('dashboard.home.rating.conversionWeak');
  }

  quotaStatusClass(s: HomeDashboard): string {
    const pct = s.quotaAttainmentMtdPct;
    if (pct == null) return 'bench-info';
    const exp = this.pacingExpectedPct();
    if (pct >= exp - 10) return 'bench-good';
    if (pct >= exp - 25) return 'bench-warn';
    return 'bench-bad';
  }

  agentAvgDeal(a: AgentLeaderboardRow): string {
    if (!a.ventesCount) return '—';
    return this.formatAmount(a.totalCA / a.ventesCount);
  }

  agentShareWidth(a: AgentLeaderboardRow, agents: AgentLeaderboardRow[]): string {
    const total = agents.reduce((sum, ag) => sum + ag.totalCA, 0);
    if (!total) return '0%';
    return ((a.totalCA / total) * 100).toFixed(0) + '%';
  }

  projectDeliveryRisk(p: ProjectProgressRow): 'low' | 'medium' | 'high' {
    if (!p.deliveryPlanned) return 'low';
    const days = Math.ceil((new Date(p.deliveryPlanned).getTime() - Date.now()) / 86_400_000);
    if (days <= 60 && p.soldPct < 60) return 'high';
    if (days <= 90 && p.soldPct < 80) return 'medium';
    return 'low';
  }
}
