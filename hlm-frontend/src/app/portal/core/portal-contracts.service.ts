import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PortalContract, PortalProperty } from '../../core/models/portal.model';
import { PaymentScheduleItem } from '../../core/models/payment-schedule.model';

@Injectable({ providedIn: 'root' })
export class PortalContractsService {
  private http = inject(HttpClient);

  listContracts(): Observable<PortalContract[]> {
    return this.http.get<PortalContract[]>('/api/portal/contracts');
  }

  getContractPdfUrl(contractId: string): string {
    return `/api/portal/contracts/${contractId}/documents/contract.pdf`;
  }

  downloadContractPdf(contractId: string): Observable<Blob> {
    return this.http.get(this.getContractPdfUrl(contractId), { responseType: 'blob' });
  }

  getPaymentSchedule(contractId: string): Observable<PaymentScheduleItem[]> {
    return this.http.get<PaymentScheduleItem[]>(`/api/portal/contracts/${contractId}/payment-schedule`);
  }

  getProperty(propertyId: string): Observable<PortalProperty> {
    return this.http.get<PortalProperty>(`/api/portal/properties/${propertyId}`);
  }
}
