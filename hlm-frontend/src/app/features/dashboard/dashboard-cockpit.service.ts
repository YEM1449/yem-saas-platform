import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map, of, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface KpiDelta {
  current: number;
  previous: number;
  delta: number;
  /** Percentage change vs previous period; null when previous = 0. */
  deltaPct: number | null;
}

export interface SparklinePoint {
  weekStart: string;
  value: number;
}

export interface KpiComparison {
  asOf: string;
  caSigne: KpiDelta;
  ventesCreated: KpiDelta;
  reservations: KpiDelta;
  encaisse: KpiDelta;
  caSparkline: SparklinePoint[];
  ventesSparkline: SparklinePoint[];
}

export interface FunnelStage {
  stage: string;
  label: string;
  count: number;
  /** 1-decimal % vs upstream stage. null when upstream = 0. */
  conversionRate: number | null;
  /** 100 - conversionRate. Same null semantics. */
  dropOffRate: number | null;
}

export interface FunnelSnapshot {
  asOf: string;
  stages: FunnelStage[];
}

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface DashboardAlert {
  id: string;
  severity: AlertSeverity;
  category: string;
  title: string;
  message: string;
  ctaLabel: string | null;
  ctaRoute: string | null;
}

// ── Pipeline analysis ─────────────────────────────────────────────────────
export interface PipelineStageRow {
  statut: string;
  count: number;
  rawValue: number;
  weightedValue: number;
  defaultProbability: number;
  avgAgingDays: number;
}

export interface AtRiskDeal {
  venteId: string;
  venteRef: string;
  contactFullName: string;
  statut: string;
  prixVente: number;
  weightedValue: number;
  agingDays: number;
}

export interface PipelineAnalysis {
  asOf: string;
  totalWeightedValue: number;
  totalRawValue: number;
  stages: PipelineStageRow[];
  atRiskDeals: AtRiskDeal[];
}

// ── Forecast ──────────────────────────────────────────────────────────────
export interface Forecast {
  asOf: string;
  next30Days: number;
  next60Days: number;
  next90Days: number;
  undated: number;
  undatedCount: number;
}

// ── Agent performance ─────────────────────────────────────────────────────
export interface AgentRow {
  agentId: string;
  agentName: string;
  totalSales: number;
  totalCA: number;
  conversionRate: number | null;
  avgDealSize: number | null;
  avgDaysToClose: number | null;
  activePipeline: number;
}

export interface AgentPerformance {
  asOf: string;
  agents: AgentRow[];
}

// ── Inventory intelligence ────────────────────────────────────────────────
export interface StockSummary {
  total: number;
  available: number;
  reserved: number;
  sold: number;
  withdrawn: number;
  absorptionRate: number | null;
}

export interface ProjectStock {
  projectId: string;
  projectName: string;
  total: number;
  available: number;
  reserved: number;
  sold: number;
  absorptionRate: number | null;
  totalValue: number;
  soldValue: number;
}

export interface InventoryIntelligence {
  asOf: string;
  overall: StockSummary;
  byProject: ProjectStock[];
}

// ── Discount analytics ───────────────────────────────────────────────────
export interface AgentDiscount {
  agentId: string;
  agentName: string;
  dealsWithDiscount: number;
  totalDeals: number;
  avgDiscountPercent: number | null;
  totalDiscountVolume: number;
}

export interface DiscountAnalytics {
  asOf: string;
  totalDealsWithDiscount: number;
  totalDeals: number;
  avgDiscountPercent: number | null;
  maxDiscountPercent: number | null;
  totalDiscountVolume: number;
  byAgent: AgentDiscount[];
}

export interface CockpitBundle {
  kpi: KpiComparison | null;
  funnel: FunnelSnapshot | null;
  alerts: DashboardAlert[];
  pipeline: PipelineAnalysis | null;
  forecast: Forecast | null;
  agentPerformance: AgentPerformance | null;
  inventory: InventoryIntelligence | null;
  discount: DiscountAnalytics | null;
}

@Injectable({ providedIn: 'root' })
export class DashboardCockpitService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/dashboard`;

  getKpiComparison(): Observable<KpiComparison> {
    return this.http.get<KpiComparison>(`${this.base}/kpi-comparison`);
  }

  getFunnel(): Observable<FunnelSnapshot> {
    return this.http.get<FunnelSnapshot>(`${this.base}/funnel`);
  }

  getAlerts(): Observable<DashboardAlert[]> {
    return this.http.get<DashboardAlert[]>(`${this.base}/alerts`);
  }

  getPipelineAnalysis(): Observable<PipelineAnalysis> {
    return this.http.get<PipelineAnalysis>(`${this.base}/pipeline-analysis`);
  }

  getForecast(): Observable<Forecast> {
    return this.http.get<Forecast>(`${this.base}/forecast`);
  }

  getAgentPerformance(): Observable<AgentPerformance> {
    return this.http.get<AgentPerformance>(`${this.base}/agents-performance`);
  }

  getInventoryIntelligence(): Observable<InventoryIntelligence> {
    return this.http.get<InventoryIntelligence>(`${this.base}/inventory-intelligence`);
  }

  getDiscountAnalytics(): Observable<DiscountAnalytics> {
    return this.http.get<DiscountAnalytics>(`${this.base}/discount-analytics`);
  }

  /** Single call used by the home dashboard — degrades gracefully on per-endpoint failure. */
  getBundle(): Observable<CockpitBundle> {
    return forkJoin({
      kpi:              this.getKpiComparison().pipe(catchError(() => of(null))),
      funnel:           this.getFunnel().pipe(catchError(() => of(null))),
      alerts:           this.getAlerts().pipe(catchError(() => of([] as DashboardAlert[]))),
      pipeline:         this.getPipelineAnalysis().pipe(catchError(() => of(null))),
      forecast:         this.getForecast().pipe(catchError(() => of(null))),
      agentPerformance: this.getAgentPerformance().pipe(catchError(() => of(null))),
      inventory:        this.getInventoryIntelligence().pipe(catchError(() => of(null))),
      discount:         this.getDiscountAnalytics().pipe(catchError(() => of(null))),
    });
  }
}
