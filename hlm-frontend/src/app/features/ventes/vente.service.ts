import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

const BASE = `${environment.apiUrl}/api/ventes`;

export type VenteStatut = 'COMPROMIS' | 'FINANCEMENT' | 'ACTE_NOTARIE' | 'LIVRE' | 'ANNULE';
export type EcheanceStatut = 'EN_ATTENTE' | 'PAYEE' | 'EN_RETARD';
export type ContractStatus = 'PENDING' | 'GENERATED' | 'SIGNED';
export type TypeFinancement = 'COMPTANT' | 'CREDIT_IMMOBILIER' | 'PTZ' | 'MIXTE';
export type MotifAnnulation =
  | 'CREDIT_REFUSE'
  | 'DESISTEMENT_ACHETEUR'
  | 'CSP_NON_REALISEE'
  | 'ACCORD_PARTIES'
  | 'LITIGE'
  | 'AUTRE';

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
  uploadedById: string | null;    // null for portal-uploaded documents
  uploadedByPortal: boolean;
  documentType: string | null;
  createdAt: string;
}

export interface Vente {
  id: string;
  /** Human-readable unique reference, e.g. VTE-2026-ABC-00001. */
  venteRef: string;
  societeId: string;
  propertyId: string;
  contactId: string;
  contactFullName: string;
  agentId: string;
  reservationId: string | null;
  statut: VenteStatut;
  contractStatus: ContractStatus;
  // French legal deadlines
  dateFinDelaiReflexion: string | null;
  dateLimiteFinancement: string | null;
  // Financing risk
  typeFinancement: TypeFinancement | null;
  montantCredit: number | null;
  banqueCredit: string | null;
  creditObtenu: boolean;
  // Cancellation reason
  motifAnnulation: MotifAnnulation | null;
  // Notary
  notaireAcquereurNom: string | null;
  notaireAcquereurEmail: string | null;
  // Post-livraison tracking (Moroccan closing process)
  datePvReception: string | null;
  dateTitreFoncier: string | null;
  prixVente: number | null;
  dateCompromis: string | null;
  dateActeNotarie: string | null;
  dateLivraisonPrevue: string | null;
  dateLivraisonReelle: string | null;
  notes: string | null;
  probability: number;
  stageEntryDate: string;
  expectedClosingDate: string | null;
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
  expectedClosingDate?: string | null;
  notes?: string | null;
}

export interface UpdateVenteStatutRequest {
  statut: VenteStatut;
  /** Required when statut === 'ANNULE'. */
  motifAnnulation?: MotifAnnulation | null;
  dateTransition?: string | null;
  expectedClosingDate?: string | null;
  notes?: string | null;
  datePvReception?: string | null;
}

export interface UpdateFinancingRequest {
  typeFinancement?: TypeFinancement | null;
  montantCredit?: number | null;
  banqueCredit?: string | null;
  creditObtenu?: boolean | null;
  dateLimiteFinancement?: string | null;
  notaireAcquereurNom?: string | null;
  notaireAcquereurEmail?: string | null;
  datePvReception?: string | null;
  dateTitreFoncier?: string | null;
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

  downloadDocument(venteId: string, docId: string): Observable<Blob> {
    return this.http.get(`${BASE}/${venteId}/documents/${docId}/download`,
      { responseType: 'blob' });
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

  generateContract(id: string): Observable<Vente> {
    return this.http.post<Vente>(`${BASE}/${id}/contract/generate`, {});
  }

  signContract(id: string): Observable<Vente> {
    return this.http.post<Vente>(`${BASE}/${id}/contract/sign`, {});
  }

  updateFinancement(id: string, req: UpdateFinancingRequest): Observable<Vente> {
    return this.http.patch<Vente>(`${BASE}/${id}/financement`, req);
  }
}
