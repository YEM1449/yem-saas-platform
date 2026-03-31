import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Immeuble {
  id: string;
  projectId: string;
  projectName: string;
  nom: string;
  adresse: string | null;
  nbEtages: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateImmeubleRequest {
  projectId: string;
  nom: string;
  adresse?: string | null;
  nbEtages?: number | null;
  description?: string | null;
}

export interface UpdateImmeubleRequest {
  nom?: string | null;
  adresse?: string | null;
  nbEtages?: number | null;
  description?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ImmeubleService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  list(projectId?: string): Observable<Immeuble[]> {
    const params: Record<string, string> = {};
    if (projectId) params['projectId'] = projectId;
    return this.http.get<Immeuble[]>(`${this.apiUrl}/api/immeubles`, { params });
  }

  getById(id: string): Observable<Immeuble> {
    return this.http.get<Immeuble>(`${this.apiUrl}/api/immeubles/${id}`);
  }

  create(req: CreateImmeubleRequest): Observable<Immeuble> {
    return this.http.post<Immeuble>(`${this.apiUrl}/api/immeubles`, req);
  }

  update(id: string, req: UpdateImmeubleRequest): Observable<Immeuble> {
    return this.http.put<Immeuble>(`${this.apiUrl}/api/immeubles/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/immeubles/${id}`);
  }
}
