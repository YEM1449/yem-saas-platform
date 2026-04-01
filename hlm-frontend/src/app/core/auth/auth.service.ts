import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap, map, catchError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MeResponse, SwitchSocieteRequest, ActivationRequest, InvitationDetails } from '../models/login.model';

export type SessionStatus = 'valid' | 'invalid' | 'unknown';

const TOKEN_KEY = 'hlm_access_token';
const SUPPORTED_LANGS = ['fr', 'en', 'ar'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private translate = inject(TranslateService);

  private cachedUser: MeResponse | null = null;

  get token(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  get isLoggedIn(): boolean {
    return !!this.token;
  }

  get user(): MeResponse | null {
    return this.cachedUser;
  }

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, req)
      .pipe(tap((res) => {
        if (!res.requiresSocieteSelection) {
          sessionStorage.setItem(TOKEN_KEY, res.accessToken);
        }
      }));
  }

  switchSociete(partialToken: string, societeId: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(
        `${environment.apiUrl}/auth/switch-societe`,
        { societeId } as SwitchSocieteRequest,
        { headers: { Authorization: `Bearer ${partialToken}` } }
      )
      .pipe(tap((res) => sessionStorage.setItem(TOKEN_KEY, res.accessToken)));
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${environment.apiUrl}/auth/me`);
  }

  validateInvitation(token: string): Observable<InvitationDetails> {
    return this.http.get<InvitationDetails>(`${environment.apiUrl}/auth/invitation/${token}`);
  }

  activateAccount(token: string, req: ActivationRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/invitation/${token}/activer`, req)
      .pipe(tap((res) => sessionStorage.setItem(TOKEN_KEY, res.accessToken)));
  }

  /**
   * Verify session by calling /auth/me.
   * Caches the result so subsequent guard checks don't re-fetch.
   * Also applies the user's saved language preference if available.
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
      tap((user) => {
        this.cachedUser = user;
        // Apply the user's persisted language preference
        if (user.langueInterface && SUPPORTED_LANGS.includes(user.langueInterface)) {
          this.translate.use(user.langueInterface);
          localStorage.setItem('hlm_lang', user.langueInterface);
          document.documentElement.dir = user.langueInterface === 'ar' ? 'rtl' : 'ltr';
          document.documentElement.lang = user.langueInterface;
        }
      }),
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
    sessionStorage.removeItem(TOKEN_KEY);
  }
}
