import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AddPaymentRequest,
  CashDashboardResponse,
  CreateScheduleItemRequest,
  PaymentScheduleItem,
  SchedulePayment,
  SendScheduleItemRequest,
  UpdateScheduleItemRequest,
} from '../../core/models/payment-schedule.model';

@Injectable({ providedIn: 'root' })
export class PaymentScheduleService {
  private http = inject(HttpClient);

  // ── Schedule items ────────────────────────────────────────────────────────

  list(contractId: string): Observable<PaymentScheduleItem[]> {
    return this.http.get<PaymentScheduleItem[]>(`/api/contracts/${contractId}/schedule`);
  }

  create(contractId: string, req: CreateScheduleItemRequest): Observable<PaymentScheduleItem> {
    return this.http.post<PaymentScheduleItem>(`/api/contracts/${contractId}/schedule`, req);
  }

  update(itemId: string, req: UpdateScheduleItemRequest): Observable<PaymentScheduleItem> {
    return this.http.put<PaymentScheduleItem>(`/api/schedule-items/${itemId}`, req);
  }

  delete(itemId: string): Observable<void> {
    return this.http.delete<void>(`/api/schedule-items/${itemId}`);
  }

  issue(itemId: string): Observable<PaymentScheduleItem> {
    return this.http.post<PaymentScheduleItem>(`/api/schedule-items/${itemId}/issue`, {});
  }

  send(itemId: string, req: SendScheduleItemRequest): Observable<PaymentScheduleItem> {
    return this.http.post<PaymentScheduleItem>(`/api/schedule-items/${itemId}/send`, req);
  }

  cancel(itemId: string): Observable<PaymentScheduleItem> {
    return this.http.post<PaymentScheduleItem>(`/api/schedule-items/${itemId}/cancel`, {});
  }

  downloadPdf(itemId: string): Observable<Blob> {
    return this.http.get(`/api/schedule-items/${itemId}/pdf`, { responseType: 'blob' });
  }

  // ── Payments ─────────────────────────────────────────────────────────────

  listPayments(itemId: string): Observable<SchedulePayment[]> {
    return this.http.get<SchedulePayment[]>(`/api/schedule-items/${itemId}/payments`);
  }

  addPayment(itemId: string, req: AddPaymentRequest): Observable<SchedulePayment> {
    return this.http.post<SchedulePayment>(`/api/schedule-items/${itemId}/payments`, req);
  }

  // ── Cash dashboard ────────────────────────────────────────────────────────

  getCashDashboard(from?: string, to?: string): Observable<CashDashboardResponse> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to)   params = params.set('to', to);
    return this.http.get<CashDashboardResponse>('/api/dashboard/commercial/cash', { params });
  }
}
