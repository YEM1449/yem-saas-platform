import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap, map, catchError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MeResponse, SwitchSocieteRequest, ActivationRequest, InvitationDetails } from '../models/login.model';

export type SessionStatus = 'valid' | 'invalid' | 'unknown';

const SUPPORTED_LANGS = ['fr', 'en', 'ar'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private translate = inject(TranslateService);

  private cachedUser: MeResponse | null = null;

  get user(): MeResponse | null {
    return this.cachedUser;
  }

  /**
   * Authenticate with email + password.
   * On success the backend sets the httpOnly auth cookie — no token is stored in JS.
   * For multi-société users a short-lived partial token is returned in the body
   * so the client can call switchSociete().
   */
  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, req, {
      withCredentials: true,
    });
  }

  /**
   * Exchange a partial token for a full société-scoped JWT.
   * The partial token is passed as Bearer in the Authorization header (service level).
   * On success the backend sets the httpOnly auth cookie.
   */
  switchSociete(partialToken: string, societeId: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${environment.apiUrl}/auth/switch-societe`,
      { societeId } as SwitchSocieteRequest,
      {
        headers: { Authorization: `Bearer ${partialToken}` },
        withCredentials: true,
      }
    );
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${environment.apiUrl}/auth/me`, { withCredentials: true });
  }

  validateInvitation(token: string): Observable<InvitationDetails> {
    return this.http.get<InvitationDetails>(`${environment.apiUrl}/auth/invitation/${token}`);
  }

  /**
   * Activate an account via invitation link.
   * On success the backend sets the httpOnly auth cookie.
   */
  activateAccount(token: string, req: ActivationRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${environment.apiUrl}/auth/invitation/${token}/activer`,
      req,
      { withCredentials: true }
    );
  }

  /**
   * Verify session by calling /auth/me.
   * Caches the result so subsequent guard checks don't re-fetch.
   * Also applies the user's saved language preference if available.
   *
   * With httpOnly cookies there is no JS-readable token — session validity
   * is determined solely by the server response to /auth/me.
   *
   * Returns:
   * - 'valid'   — cookie verified by backend
   * - 'invalid' — 401/403 from backend → session cleared
   * - 'unknown' — network/5xx error → keep user in app
   */
  verifySession(): Observable<SessionStatus> {
    if (this.cachedUser) {
      return of('valid');
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
        // Network error / 5xx — keep session, don't force logout
        return of('unknown' as SessionStatus);
      })
    );
  }

  /**
   * Log out: ask the backend to clear the httpOnly cookie (Max-Age=0),
   * then discard the cached user and navigate to the login page.
   */
  logout(): void {
    this.http
      .post(`${environment.apiUrl}/auth/logout`, null, { withCredentials: true })
      .subscribe({ complete: () => this.finishLogout(), error: () => this.finishLogout() });
  }

  private finishLogout(): void {
    this.clearSession();
    this.router.navigateByUrl('/login');
  }

  /** Clears the cached /auth/me response. Call after impersonation changes to force re-fetch. */
  clearCachedUser(): void {
    this.cachedUser = null;
  }

  private clearSession(): void {
    this.cachedUser = null;
  }
}
