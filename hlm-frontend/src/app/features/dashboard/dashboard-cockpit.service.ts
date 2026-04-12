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

export interface CockpitBundle {
  kpi: KpiComparison | null;
  funnel: FunnelSnapshot | null;
  alerts: DashboardAlert[];
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

  /** Single call used by the home dashboard — degrades gracefully on per-endpoint failure. */
  getBundle(): Observable<CockpitBundle> {
    return forkJoin({
      kpi:    this.getKpiComparison().pipe(catchError(() => of(null))),
      funnel: this.getFunnel().pipe(catchError(() => of(null))),
      alerts: this.getAlerts().pipe(catchError(() => of([] as DashboardAlert[]))),
    }).pipe(map(b => ({ kpi: b.kpi, funnel: b.funnel, alerts: b.alerts })));
  }
}
