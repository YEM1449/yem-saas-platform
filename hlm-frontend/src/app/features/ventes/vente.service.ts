import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

const BASE = `${environment.apiUrl}/api/ventes`;

export type VenteStatut = 'COMPROMIS' | 'FINANCEMENT' | 'ACTE_NOTARIE' | 'LIVRE' | 'ANNULE';
export type EcheanceStatut = 'EN_ATTENTE' | 'PAYEE' | 'EN_RETARD';

export interface Echeance {
  id: string;
  venteId: string;
  libelle: string;
  montant: number;
  dateEcheance: string;
  statut: EcheanceStatut;
  datePaiement: string | null;
  notes: string | null;
  createdAt: string;
}

export interface VenteDocument {
  id: string;
  venteId: string;
  nomFichier: string;
  contentType: string | null;
  tailleOctets: number | null;
  uploadedById: string;
  createdAt: string;
}

export interface Vente {
  id: string;
  societeId: string;
  propertyId: string;
  contactId: string;
  contactFullName: string;
  agentId: string;
  reservationId: string | null;
  statut: VenteStatut;
  prixVente: number | null;
  dateCompromis: string | null;
  dateActeNotarie: string | null;
  dateLivraisonPrevue: string | null;
  dateLivraisonReelle: string | null;
  notes: string | null;
  echeances: Echeance[];
  documents: VenteDocument[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateVenteRequest {
  reservationId?: string | null;
  contactId?: string | null;
  propertyId?: string | null;
  agentId?: string | null;
  prixVente?: number | null;
  /** Optional commercial discount (only meaningful when converting from reservation). */
  reduction?: number | null;
  dateCompromis?: string | null;
  dateLivraisonPrevue?: string | null;
  notes?: string | null;
}

export interface UpdateVenteStatutRequest {
  statut: VenteStatut;
  dateTransition?: string | null;
  notes?: string | null;
}

export interface CreateEcheanceRequest {
  libelle: string;
  montant: number;
  dateEcheance: string;
  notes?: string | null;
}

export interface UpdateEcheanceStatutRequest {
  statut: EcheanceStatut;
  datePaiement?: string | null;
}

@Injectable({ providedIn: 'root' })
export class VenteService {
  private http = inject(HttpClient);

  list(): Observable<Vente[]> {
    return this.http.get<Vente[]>(BASE);
  }

  listByContact(contactId: string): Observable<Vente[]> {
    return this.http.get<Vente[]>(BASE, { params: { contactId } });
  }

  uploadDocument(venteId: string, file: File): Observable<VenteDocument> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<VenteDocument>(`${BASE}/${venteId}/documents`, form);
  }

  get(id: string): Observable<Vente> {
    return this.http.get<Vente>(`${BASE}/${id}`);
  }

  create(req: CreateVenteRequest): Observable<Vente> {
    return this.http.post<Vente>(BASE, req);
  }

  updateStatut(id: string, req: UpdateVenteStatutRequest): Observable<Vente> {
    return this.http.patch<Vente>(`${BASE}/${id}/statut`, req);
  }

  addEcheance(venteId: string, req: CreateEcheanceRequest): Observable<Echeance> {
    return this.http.post<Echeance>(`${BASE}/${venteId}/echeances`, req);
  }

  updateEcheanceStatut(venteId: string, echeanceId: string, req: UpdateEcheanceStatutRequest): Observable<Echeance> {
    return this.http.patch<Echeance>(`${BASE}/${venteId}/echeances/${echeanceId}/statut`, req);
  }

  inviteBuyer(venteId: string): Observable<{ message: string; magicLinkUrl: string }> {
    return this.http.post<{ message: string; magicLinkUrl: string }>(`${BASE}/${venteId}/portal/invite`, {});
  }
}
