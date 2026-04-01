import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';
import {
  MagicLinkRequest,
  MagicLinkResponse,
  PortalTenantInfo,
  PortalTokenVerifyResponse,
} from '../../core/models/portal.model';
import { PortalSessionStore } from './portal-session.store';

@Injectable({ providedIn: 'root' })
export class PortalAuthService {
  private http   = inject(HttpClient);
  private router = inject(Router);
  private session = inject(PortalSessionStore);

  get isLoggedIn(): boolean {
    return this.session.isAuthenticated();
  }

  requestLink(req: MagicLinkRequest): Observable<MagicLinkResponse> {
    return this.http.post<MagicLinkResponse>('/api/portal/auth/request-link', req, {
      withCredentials: true,
    });
  }

  verifyToken(rawToken: string): Observable<void> {
    return this.http
      .get<PortalTokenVerifyResponse>(`/api/portal/auth/verify?token=${encodeURIComponent(rawToken)}`, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.session.markAuthenticated()),
        map(() => undefined)
      );
  }

  getTenantInfo(): Observable<PortalTenantInfo> {
    return this.http.get<PortalTenantInfo>('/api/portal/tenant-info', {
      withCredentials: true,
    });
  }

  validateSession(): Observable<boolean> {
    if (this.session.isAuthenticated()) {
      return of(true);
    }
    return this.getTenantInfo().pipe(
      tap(() => this.session.markAuthenticated()),
      map(() => true),
      catchError(() => of(false))
    );
  }

  logout(): void {
    this.http
      .post('/api/portal/auth/logout', null, { withCredentials: true })
      .subscribe({ complete: () => this.finishLogout(), error: () => this.finishLogout() });
  }

  clearSession(): void {
    this.session.clear();
  }

  private finishLogout(): void {
    this.session.clear();
    this.router.navigateByUrl('/portal/login');
  }
}
