import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminUser, CreateUserRequest, ResetPasswordResponse } from './admin-user.model';

@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/admin/users`;

  list(q?: string): Observable<AdminUser[]> {
    let params = new HttpParams();
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<AdminUser[]>(this.base, { params });
  }

  create(req: CreateUserRequest): Observable<AdminUser> {
    return this.http.post<AdminUser>(this.base, req);
  }

  changeRole(id: string, role: string): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.base}/${id}/role`, { role });
  }

  setEnabled(id: string, enabled: boolean): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.base}/${id}/enabled`, { enabled });
  }

  resetPassword(id: string): Observable<ResetPasswordResponse> {
    return this.http.post<ResetPasswordResponse>(`${this.base}/${id}/reset-password`, {});
  }
}
