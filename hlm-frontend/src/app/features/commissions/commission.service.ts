import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommissionDTO, CommissionRuleRequest, CommissionRuleResponse } from '../../core/models/commission.model';

@Injectable({ providedIn: 'root' })
export class CommissionService {
  private http = inject(HttpClient);

  getMyCommissions(from?: string, to?: string): Observable<CommissionDTO[]> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to)   params['to']   = to;
    return this.http.get<CommissionDTO[]>('/api/commissions/my', { params });
  }

  getAllCommissions(agentId?: string, from?: string, to?: string): Observable<CommissionDTO[]> {
    const params: Record<string, string> = {};
    if (agentId) params['agentId'] = agentId;
    if (from)    params['from']    = from;
    if (to)      params['to']      = to;
    return this.http.get<CommissionDTO[]>('/api/commissions', { params });
  }

  listRules(): Observable<CommissionRuleResponse[]> {
    return this.http.get<CommissionRuleResponse[]>('/api/commission-rules');
  }

  createRule(req: CommissionRuleRequest): Observable<CommissionRuleResponse> {
    return this.http.post<CommissionRuleResponse>('/api/commission-rules', req);
  }

  updateRule(id: string, req: CommissionRuleRequest): Observable<CommissionRuleResponse> {
    return this.http.put<CommissionRuleResponse>(`/api/commission-rules/${id}`, req);
  }

  deleteRule(id: string): Observable<void> {
    return this.http.delete<void>(`/api/commission-rules/${id}`);
  }
}
