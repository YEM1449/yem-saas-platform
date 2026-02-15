import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap, map, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MeResponse } from '../models/login.model';

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
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, req)
      .pipe(tap((res) => localStorage.setItem(TOKEN_KEY, res.accessToken)));
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${environment.apiUrl}/auth/me`);
  }

  /**
   * Verify session by calling /auth/me.
   * Caches the result so subsequent guard checks don't re-fetch.
   */
  verifySession(): Observable<boolean> {
    if (this.cachedUser) {
      return of(true);
    }
    if (!this.token) {
      return of(false);
    }
    return this.me().pipe(
      tap((user) => (this.cachedUser = user)),
      map(() => true),
      catchError(() => {
        this.clearSession();
        return of(false);
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
