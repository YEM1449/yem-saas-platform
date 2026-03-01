import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreatePaymentScheduleRequest,
  PaymentCallResponse,
  PaymentResponse,
  PaymentScheduleResponse,
  RecordPaymentRequest,
} from '../../core/models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  // ── Schedule ──────────────────────────────────────────────────────

  getSchedule(contractId: string): Observable<PaymentScheduleResponse> {
    return this.http.get<PaymentScheduleResponse>(
      `${this.apiUrl}/api/contracts/${contractId}/payment-schedule`
    );
  }

  createSchedule(
    contractId: string,
    req: CreatePaymentScheduleRequest
  ): Observable<PaymentScheduleResponse> {
    return this.http.post<PaymentScheduleResponse>(
      `${this.apiUrl}/api/contracts/${contractId}/payment-schedule`,
      req
    );
  }

  // ── Payment calls ─────────────────────────────────────────────────

  issueCall(contractId: string, trancheId: string): Observable<PaymentCallResponse> {
    return this.http.post<PaymentCallResponse>(
      `${this.apiUrl}/api/contracts/${contractId}/payment-schedule/tranches/${trancheId}/issue-call`,
      {}
    );
  }

  downloadCallPdf(callId: string): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/api/payment-calls/${callId}/documents/appel-de-fonds.pdf`,
      { responseType: 'blob' }
    );
  }

  // ── Payments (cash-in) ────────────────────────────────────────────

  listPayments(callId: string): Observable<PaymentResponse[]> {
    return this.http.get<PaymentResponse[]>(
      `${this.apiUrl}/api/payment-calls/${callId}/payments`
    );
  }

  recordPayment(callId: string, req: RecordPaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(
      `${this.apiUrl}/api/payment-calls/${callId}/payments`,
      req
    );
  }
}
