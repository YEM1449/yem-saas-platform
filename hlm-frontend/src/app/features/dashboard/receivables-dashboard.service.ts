import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReceivablesDashboard } from '../../core/models/receivables-dashboard.model';

export interface VenteReceivablesSummary {
  totalOutstanding: number;
  totalOverdue: number;
  current: number;
  bucket1to30: number;
  bucket31to60: number;
  bucket61to90: number;
  bucketOver90: number;
}

@Injectable({ providedIn: 'root' })
export class ReceivablesDashboardService {
  private http = inject(HttpClient);

  getSummary(agentId?: string): Observable<ReceivablesDashboard> {
    const params: Record<string, string> = {};
    if (agentId) params['agentId'] = agentId;
    return this.http.get<ReceivablesDashboard>('/api/dashboard/receivables', { params });
  }

  getVenteReceivables(): Observable<VenteReceivablesSummary> {
    return this.http.get<VenteReceivablesSummary>('/api/dashboard/receivables/vente');
  }
}
