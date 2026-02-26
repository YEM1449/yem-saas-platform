import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CommercialDashboardSalesResponse,
  CommercialDashboardSummary,
  DashboardParams,
} from '../../core/models/commercial-dashboard.model';

@Injectable({ providedIn: 'root' })
export class CommercialDashboardService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/dashboard/commercial`;

  getSummary(params: DashboardParams): Observable<CommercialDashboardSummary> {
    return this.http.get<CommercialDashboardSummary>(`${this.base}/summary`, {
      params: this.toHttpParams(params),
    });
  }

  getSales(
    params: DashboardParams & { page?: number; size?: number }
  ): Observable<CommercialDashboardSalesResponse> {
    return this.http.get<CommercialDashboardSalesResponse>(`${this.base}/sales`, {
      params: this.toHttpParams(params),
    });
  }

  private toHttpParams(obj: Record<string, unknown>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(obj)) {
      if (v != null && v !== '') p = p.set(k, String(v));
    }
    return p;
  }
}
