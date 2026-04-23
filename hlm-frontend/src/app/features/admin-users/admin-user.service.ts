import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MembreDto,
  InviterUtilisateurRequest,
  ChangerRoleRequest,
  ModifierUtilisateurRequest,
  RetirerUtilisateurRequest,
  UserDataExport,
} from './admin-user.model';
import { PageResponse } from '../../core/models/page-response.model';

export interface UserQuotaResponse {
  userId: string;
  month: string;
  caCible: number | null;
  ventesCountCible: number | null;
  updatedAt: string | null;
}

export interface UserQuotaRequest {
  month: string;
  caCible: number | null;
  ventesCountCible: number | null;
}

export interface ProjectAccessResponse {
  userId: string;
  projectIds: string[];
}

export interface ProjectAccessRequest {
  projectIds: string[];
}

@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/mon-espace/utilisateurs`;

  list(opts?: { search?: string; role?: string; actif?: boolean; page?: number; size?: number }): Observable<PageResponse<MembreDto>> {
    let p = new HttpParams();
    if (opts?.search) p = p.set('search', opts.search);
    if (opts?.role)   p = p.set('role', opts.role);
    if (opts?.actif != null) p = p.set('actif', String(opts.actif));
    if (opts?.page != null)  p = p.set('page', String(opts.page));
    if (opts?.size != null)  p = p.set('size', String(opts.size));
    return this.http.get<PageResponse<MembreDto>>(this.base, { params: p });
  }

  getById(id: string): Observable<MembreDto> {
    return this.http.get<MembreDto>(`${this.base}/${id}`);
  }

  inviter(req: InviterUtilisateurRequest): Observable<MembreDto> {
    return this.http.post<MembreDto>(this.base, req);
  }

  reinviter(id: string): Observable<MembreDto> {
    return this.http.post<MembreDto>(`${this.base}/${id}/reinviter`, {});
  }

  modifierProfil(id: string, req: ModifierUtilisateurRequest): Observable<MembreDto> {
    return this.http.patch<MembreDto>(`${this.base}/${id}`, req);
  }

  changerRole(id: string, req: ChangerRoleRequest): Observable<MembreDto> {
    return this.http.patch<MembreDto>(`${this.base}/${id}/role`, req);
  }

  retirer(id: string, req: RetirerUtilisateurRequest): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { body: req });
  }

  debloquer(id: string): Observable<MembreDto> {
    return this.http.post<MembreDto>(`${this.base}/${id}/debloquer`, {});
  }

  exportDonnees(id: string): Observable<UserDataExport> {
    return this.http.get<UserDataExport>(`${this.base}/${id}/export-donnees`);
  }

  anonymiser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/anonymiser`);
  }

  getQuota(userId: string, month?: string): Observable<UserQuotaResponse> {
    let p = new HttpParams();
    if (month) p = p.set('month', month);
    return this.http.get<UserQuotaResponse>(`${this.base}/${userId}/quota`, { params: p });
  }

  upsertQuota(userId: string, req: UserQuotaRequest): Observable<UserQuotaResponse> {
    return this.http.put<UserQuotaResponse>(`${this.base}/${userId}/quota`, req);
  }

  getProjectAccess(userId: string): Observable<ProjectAccessResponse> {
    return this.http.get<ProjectAccessResponse>(`${this.base}/${userId}/project-access`);
  }

  setProjectAccess(userId: string, req: ProjectAccessRequest): Observable<ProjectAccessResponse> {
    return this.http.put<ProjectAccessResponse>(`${this.base}/${userId}/project-access`, req);
  }
}
