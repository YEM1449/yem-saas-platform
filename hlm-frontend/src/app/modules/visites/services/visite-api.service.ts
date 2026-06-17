import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

const BASE = `${environment.apiUrl}/api/visites`;

/** Visit lifecycle (RG-V02) — in sync with backend {@code StatutVisite}. */
export type StatutVisite = 'PLANIFIEE' | 'CONFIRMEE' | 'REALISEE' | 'ANNULEE' | 'NO_SHOW';

/** Visit channel (RG-V03) — in sync with backend {@code TypeVisite}. */
export type TypeVisite = 'SUR_SITE' | 'AGENCE' | 'VISIO' | 'TELEPHONIQUE';

/** Outcome recorded at REALISEE (RG-V06) — in sync with backend {@code ResultatVisite}. */
export type ResultatVisite = 'INTERESSE' | 'A_RELANCER' | 'PAS_INTERESSE' | 'OPPORTUNITE_CREEE';

/** Read model — mirrors backend {@code VisiteResponse}. {@code dateHeure} is a UTC instant. */
export interface Visite {
  id: string;
  agentId: string | null;
  agentNom: string | null;
  contactId: string | null;
  contactNom: string | null;
  propertyId: string | null;
  projectId: string | null;
  dateHeure: string;
  dureeMinutes: number;
  type: TypeVisite;
  statut: StatutVisite;
  lieu: string | null;
  compteRendu: string | null;
  resultat: ResultatVisite | null;
  venteId: string | null;
  annulationRaison: string | null;
  createdAt: string;
}

export interface CreateVisiteRequest {
  contactId: string;
  propertyId?: string | null;
  projectId?: string | null;
  agentId?: string | null;
  dateHeure: string;            // UTC instant ISO
  dureeMinutes?: number | null;
  type: TypeVisite;
  lieu?: string | null;
  override?: boolean;
}

export interface UpdateVisiteRequest {
  propertyId?: string | null;
  projectId?: string | null;
  dateHeure: string;
  dureeMinutes?: number | null;
  type: TypeVisite;
  lieu?: string | null;
  override?: boolean;
}

export interface CompteRenduRequest {
  compteRendu: string;
  resultat: ResultatVisite;
}

export interface AgendaQuery {
  agentId?: string | null;
  statut?: StatutVisite | null;
  from: string;                 // UTC instant ISO
  to: string;                   // UTC instant ISO
}

/**
 * Thin HTTP wrapper over {@code /api/visites}. All calls are société-scoped server-side
 * (RG-V04); the frontend never sends societeId. Stateless — agenda state lives in
 * {@code VisiteStateService}.
 */
@Injectable({ providedIn: 'root' })
export class VisiteApiService {
  private http = inject(HttpClient);

  agenda(q: AgendaQuery): Observable<Visite[]> {
    let params = new HttpParams().set('from', q.from).set('to', q.to);
    if (q.agentId) params = params.set('agentId', q.agentId);
    if (q.statut) params = params.set('statut', q.statut);
    return this.http.get<Visite[]>(BASE, { params });
  }

  get(id: string): Observable<Visite> {
    return this.http.get<Visite>(`${BASE}/${id}`);
  }

  create(req: CreateVisiteRequest): Observable<Visite> {
    return this.http.post<Visite>(BASE, req);
  }

  update(id: string, req: UpdateVisiteRequest): Observable<Visite> {
    return this.http.put<Visite>(`${BASE}/${id}`, req);
  }

  confirmer(id: string): Observable<Visite> {
    return this.http.post<Visite>(`${BASE}/${id}/confirmer`, {});
  }

  noShow(id: string): Observable<Visite> {
    return this.http.post<Visite>(`${BASE}/${id}/no-show`, {});
  }

  annuler(id: string, raison: string): Observable<Visite> {
    return this.http.post<Visite>(`${BASE}/${id}/annuler`, { raison });
  }

  enregistrerCompteRendu(id: string, req: CompteRenduRequest): Observable<Visite> {
    return this.http.post<Visite>(`${BASE}/${id}/compte-rendu`, req);
  }

  /** Export .ics (P5). Returned as a Blob for download. */
  ics(id: string): Observable<Blob> {
    return this.http.get(`${BASE}/${id}/ics`, { responseType: 'blob' });
  }
}
