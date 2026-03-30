import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

const TOKEN_KEY = 'hlm_access_token';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Read session state directly here to avoid a DI cycle:
  // AuthService -> HttpClient -> authInterceptor -> AuthService.
  const router = inject(Router);
  const token = localStorage.getItem(TOKEN_KEY);

  // Don't attach token to login requests (public endpoint)
  const skipAuth = req.url.endsWith('/auth/login');
  // Don't auto-logout on /auth/me — verifySession handles that path
  const isAuthMe = req.url.endsWith('/auth/me');

  const authReq = token && !skipAuth
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    tap({
      error: (err) => {
        // On 401 from protected endpoints, clear token and redirect to login.
        // Skip for /auth/login itself to avoid redirect loop on bad credentials.
        if (err.status === 401 && !skipAuth && !isAuthMe) {
          localStorage.removeItem(TOKEN_KEY);
          void router.navigateByUrl('/login');
        }
      },
    })
  );
};
