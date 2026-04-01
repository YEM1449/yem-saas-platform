import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Deposit, DepositReportResponse } from '../../core/models/deposit.model';

export interface CreateDepositRequest {
  contactId: string;
  propertyId: string;
  amount: number;
  currency?: string;
  notes?: string;
}

@Injectable({ providedIn: 'root' })
export class DepositService {
  private http = inject(HttpClient);

  create(req: CreateDepositRequest): Observable<Deposit> {
    return this.http.post<Deposit>(`${environment.apiUrl}/api/deposits`, req);
  }

  listByContact(contactId: string): Observable<Deposit[]> {
    return this.http
      .get<DepositReportResponse>(
        `${environment.apiUrl}/api/deposits/report`,
        { params: { contactId } }
      )
      .pipe(map((r) => r.items));
  }

  confirm(depositId: string): Observable<Deposit> {
    return this.http.post<Deposit>(
      `${environment.apiUrl}/api/deposits/${depositId}/confirm`, {}
    );
  }

  cancel(depositId: string): Observable<Deposit> {
    return this.http.post<Deposit>(
      `${environment.apiUrl}/api/deposits/${depositId}/cancel`, {}
    );
  }

  downloadReservationPdf(depositId: string): Observable<Blob> {
    return this.http.get(
      `${environment.apiUrl}/api/deposits/${depositId}/documents/reservation.pdf`,
      { responseType: 'blob' }
    );
  }
}
