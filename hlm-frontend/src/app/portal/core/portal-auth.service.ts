import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import {
  MagicLinkRequest,
  MagicLinkResponse,
  PortalTenantInfo,
  PortalTokenVerifyResponse,
} from '../../core/models/portal.model';

const PORTAL_TOKEN_KEY = 'hlm_portal_token';

@Injectable({ providedIn: 'root' })
export class PortalAuthService {
  private http   = inject(HttpClient);
  private router = inject(Router);

  get token(): string | null {
    return localStorage.getItem(PORTAL_TOKEN_KEY);
  }

  get isLoggedIn(): boolean {
    return !!this.token;
  }

  requestLink(req: MagicLinkRequest): Observable<MagicLinkResponse> {
    return this.http.post<MagicLinkResponse>('/api/portal/auth/request-link', req);
  }

  verifyToken(rawToken: string): Observable<PortalTokenVerifyResponse> {
    return this.http
      .get<PortalTokenVerifyResponse>(`/api/portal/auth/verify?token=${encodeURIComponent(rawToken)}`)
      .pipe(tap(res => localStorage.setItem(PORTAL_TOKEN_KEY, res.accessToken)));
  }

  getTenantInfo(): Observable<PortalTenantInfo> {
    return this.http.get<PortalTenantInfo>('/api/portal/tenant-info');
  }

  logout(): void {
    localStorage.removeItem(PORTAL_TOKEN_KEY);
    this.router.navigateByUrl('/portal/login');
  }
}
