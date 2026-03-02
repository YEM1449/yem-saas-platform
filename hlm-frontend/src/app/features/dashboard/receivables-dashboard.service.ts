import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReceivablesDashboard } from '../../core/models/receivables-dashboard.model';

@Injectable({ providedIn: 'root' })
export class ReceivablesDashboardService {
  private http = inject(HttpClient);

  getSummary(agentId?: string): Observable<ReceivablesDashboard> {
    const params: Record<string, string> = {};
    if (agentId) params['agentId'] = agentId;
    return this.http.get<ReceivablesDashboard>('/api/dashboard/receivables', { params });
  }
}
