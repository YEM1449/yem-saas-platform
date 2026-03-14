import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReservationStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'CONVERTED_TO_DEPOSIT';

export interface Reservation {
  id: string;
  tenantId: string;
  contactId: string;
  propertyId: string;
  reservedByUserId: string;
  reservationPrice: number | null;
  reservationDate: string;
  expiryDate: string;
  status: ReservationStatus;
  notes: string | null;
  convertedDepositId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateReservationRequest {
  contactId: string;
  propertyId: string;
  reservationPrice?: number | null;
  expiryDate?: string | null;
  notes?: string | null;
}

export interface ConvertToDepositRequest {
  amount: number;
  currency?: string;
  depositDate?: string;
  reference?: string;
  dueDate?: string;
}

@Injectable({ providedIn: 'root' })
export class ReservationService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  list(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.apiUrl}/api/reservations`);
  }

  get(id: string): Observable<Reservation> {
    return this.http.get<Reservation>(`${this.apiUrl}/api/reservations/${id}`);
  }

  create(req: CreateReservationRequest): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/api/reservations`, req);
  }

  cancel(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/api/reservations/${id}/cancel`, {});
  }

  convertToDeposit(id: string, req: ConvertToDepositRequest): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/api/reservations/${id}/convert-to-deposit`, req);
  }
}
