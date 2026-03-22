import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap, map, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ActivationRequest, InvitationDetails, LoginRequest, LoginResponse, MeResponse } from '../models/login.model';

export type SessionStatus = 'valid' | 'invalid' | 'unknown';

const TOKEN_KEY = 'hlm_access_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private cachedUser: MeResponse | null = null;

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get isLoggedIn(): boolean {
    return !!this.token;
  }

  get user(): MeResponse | null {
    return this.cachedUser;
  }

  login(req: LoginRequest): Observable<LoginResponse> {
    this.clearSession();
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, req)
      .pipe(tap((res) => {
        // Only store token if this is a full auth (not a société selection prompt)
        if (!res.requiresSocieteSelection) {
          localStorage.setItem(TOKEN_KEY, res.accessToken);
        } else {
          localStorage.removeItem(TOKEN_KEY);
        }
      }));
  }

  switchSociete(partialToken: string, societeId: string): Observable<LoginResponse> {
    this.cachedUser = null;
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/switch-societe`,
        { societeId },
        { headers: { Authorization: `Bearer ${partialToken}` } }
      )
      .pipe(tap((res) => localStorage.setItem(TOKEN_KEY, res.accessToken)));
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${environment.apiUrl}/auth/me`);
  }

  validateInvitation(token: string): Observable<InvitationDetails> {
    return this.http.get<InvitationDetails>(`${environment.apiUrl}/auth/invitation/${token}`);
  }

  activateAccount(token: string, req: ActivationRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/invitation/${token}/activer`, req)
      .pipe(tap((res) => localStorage.setItem(TOKEN_KEY, res.accessToken)));
  }

  /**
   * Verify session by calling /auth/me.
   * Caches the result so subsequent guard checks don't re-fetch.
   *
   * Returns:
   * - 'valid'   — token verified by backend
   * - 'invalid' — 401/403 from backend → session cleared
   * - 'unknown' — network/5xx error → token kept, user stays in app
   */
  verifySession(): Observable<SessionStatus> {
    if (this.cachedUser) {
      return of('valid');
    }
    if (!this.token) {
      return of('invalid');
    }
    return this.me().pipe(
      tap((user) => (this.cachedUser = user)),
      map((): SessionStatus => 'valid'),
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.clearSession();
          return of('invalid' as SessionStatus);
        }
        // Network error / 5xx — keep token, don't force logout
        return of('unknown' as SessionStatus);
      })
    );
  }

  logout(): void {
    this.clearSession();
    this.router.navigateByUrl('/login');
  }

  private clearSession(): void {
    this.cachedUser = null;
    localStorage.removeItem(TOKEN_KEY);
  }
}
