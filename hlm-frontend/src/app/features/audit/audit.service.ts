import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditEventResponse } from '../../core/models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private http = inject(HttpClient);

  getCommercialAudit(params?: { from?: string; to?: string; limit?: number }): Observable<AuditEventResponse[]> {
    let httpParams = new HttpParams();
    if (params?.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params?.to) {
      httpParams = httpParams.set('to', params.to);
    }
    if (params?.limit != null) {
      httpParams = httpParams.set('limit', params.limit.toString());
    }
    return this.http.get<AuditEventResponse[]>('/api/audit/commercial', { params: httpParams });
  }
}
