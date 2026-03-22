import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  SocieteDto, SocieteDetailDto, SocieteStatsDto, SocieteComplianceDto,
  MembreSocieteDto, CreateSocieteRequest, UpdateSocieteRequest,
  AddMembreRequest, UpdateMembreRoleRequest, ImpersonateResponse, PageResponse,
  InviteUserRequest,
} from './societe.model';

@Injectable({ providedIn: 'root' })
export class SocieteService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/admin/societes`;

  list(filter?: { search?: string; pays?: string; planAbonnement?: string; actif?: boolean }, pageable?: { page?: number; size?: number; sort?: string }): Observable<PageResponse<SocieteDto>> {
    let params = new HttpParams()
      .set('page', String(pageable?.page ?? 0))
      .set('size', String(pageable?.size ?? 20))
      .set('sort', pageable?.sort ?? 'nom');
    if (filter?.search) params = params.set('search', filter.search);
    if (filter?.pays) params = params.set('pays', filter.pays);
    if (filter?.planAbonnement) params = params.set('planAbonnement', filter.planAbonnement);
    if (filter?.actif !== undefined) params = params.set('actif', String(filter.actif));
    return this.http.get<PageResponse<SocieteDto>>(this.base, { params });
  }

  getDetail(id: string): Observable<SocieteDetailDto> {
    return this.http.get<SocieteDetailDto>(`${this.base}/${id}`);
  }

  getStats(id: string): Observable<SocieteStatsDto> {
    return this.http.get<SocieteStatsDto>(`${this.base}/${id}/stats`);
  }

  getCompliance(id: string): Observable<SocieteComplianceDto> {
    return this.http.get<SocieteComplianceDto>(`${this.base}/${id}/compliance`);
  }

  create(req: CreateSocieteRequest): Observable<SocieteDetailDto> {
    return this.http.post<SocieteDetailDto>(this.base, req);
  }

  update(id: string, req: UpdateSocieteRequest): Observable<SocieteDetailDto> {
    return this.http.put<SocieteDetailDto>(`${this.base}/${id}`, req);
  }

  desactiver(id: string, raison: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/desactiver`, { raison });
  }

  reactiver(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/reactiver`, {});
  }

  listMembres(id: string): Observable<MembreSocieteDto[]> {
    return this.http.get<MembreSocieteDto[]>(`${this.base}/${id}/membres`);
  }

  addMembre(id: string, req: AddMembreRequest): Observable<MembreSocieteDto> {
    return this.http.post<MembreSocieteDto>(`${this.base}/${id}/membres`, req);
  }

  inviteUser(id: string, req: InviteUserRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/invite`, req);
  }

  updateMembreRole(id: string, userId: string, req: UpdateMembreRoleRequest): Observable<MembreSocieteDto> {
    return this.http.put<MembreSocieteDto>(`${this.base}/${id}/membres/${userId}/role`, req);
  }

  removeMembre(id: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/membres/${userId}`);
  }

  impersonate(id: string, userId: string): Observable<ImpersonateResponse> {
    return this.http.post<ImpersonateResponse>(`${this.base}/${id}/impersonate/${userId}`, {});
  }
}
