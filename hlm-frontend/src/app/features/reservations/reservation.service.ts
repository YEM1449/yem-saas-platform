import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReservationStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'CONVERTED_TO_DEPOSIT';

export interface Reservation {
  id: string;
  societeId: string;
  reservationRef: string;
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

/** Mirrors ReservationDetailResponse from the backend. */
export interface ReservationDetail {
  id: string;
  societeId: string;
  reservationRef: string;
  status: ReservationStatus;
  reservationDate: string;
  expiryDate: string;
  reservationPrice: number | null;
  notes: string | null;
  convertedDepositId: string | null;
  createdAt: string;
  updatedAt: string;
  // Contact
  contactId: string;
  contactFullName: string;
  contactPhone: string | null;
  contactEmail: string | null;
  // Property
  propertyId: string | null;
  propertyTitle: string | null;
  propertyReferenceCode: string | null;
  propertyPrice: number | null;
  projectNom: string | null;
  trancheNom: string | null;
  immeubleNom: string | null;
  // Linked vente
  linkedVenteId: string | null;
}

/** Mirrors VentePrefillResponse from the backend. */
export interface VentePrefillData {
  reservationId: string;
  reservationRef: string;
  reservationPrice: number | null;
  contactId: string;
  contactFullName: string;
  propertyId: string | null;
  propertyTitle: string | null;
  propertyReferenceCode: string | null;
  propertyPrice: number | null;
  projectNom: string | null;
  trancheNom: string | null;
  immeubleNom: string | null;
  suggestedPrixVente: number | null;
}

/** Mirrors the backend DepositResponse record. */
export interface DepositResponse {
  id: string;
  contactId: string;
  propertyId: string;
  agentId: string;
  amount: number;
  currency: string;
  depositDate: string | null;
  reference: string;
  status: string;
  notes: string | null;
  dueDate: string | null;
  confirmedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class ReservationService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  list(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.apiUrl}/api/reservations`);
  }

  listByContact(contactId: string): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.apiUrl}/api/reservations`, {
      params: { contactId },
    });
  }

  get(id: string): Observable<Reservation> {
    return this.http.get<Reservation>(`${this.apiUrl}/api/reservations/${id}`);
  }

  getDetail(id: string): Observable<ReservationDetail> {
    return this.http.get<ReservationDetail>(`${this.apiUrl}/api/reservations/${id}/detail`);
  }

  getVentePrefill(id: string): Observable<VentePrefillData> {
    return this.http.get<VentePrefillData>(`${this.apiUrl}/api/reservations/${id}/vente-prefill`);
  }

  create(req: CreateReservationRequest): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/api/reservations`, req);
  }

  cancel(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/api/reservations/${id}/cancel`, {});
  }

  convertToDeposit(id: string, req: ConvertToDepositRequest): Observable<DepositResponse> {
    return this.http.post<DepositResponse>(
      `${this.apiUrl}/api/reservations/${id}/convert-to-deposit`, req
    );
  }
}
