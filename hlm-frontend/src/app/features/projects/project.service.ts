import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Project,
  ProjectKpi,
  ProjectCreateRequest,
  ProjectUpdateRequest,
} from '../../core/models/project.model';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/projects`;

  list(activeOnly = false): Observable<Project[]> {
    const params = new HttpParams().set('activeOnly', String(activeOnly));
    return this.http.get<Project[]>(this.base, { params });
  }

  getById(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.base}/${id}`);
  }

  create(request: ProjectCreateRequest): Observable<Project> {
    return this.http.post<Project>(this.base, request);
  }

  update(id: string, request: ProjectUpdateRequest): Observable<Project> {
    return this.http.put<Project>(`${this.base}/${id}`, request);
  }

  /** Archives the project (sets status = ARCHIVED). Returns 204 No Content. */
  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  getKpis(id: string): Observable<ProjectKpi> {
    return this.http.get<ProjectKpi>(`${this.base}/${id}/kpis`);
  }
}
