import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

/**
 * Auth interceptor for CRM routes.
 *
 * - Adds `withCredentials: true` to every request so the browser automatically
 *   sends the httpOnly `hlm_auth` cookie cross-origin (Angular on :4200 → API
 *   on :8080 in dev; same-domain via proxy in prod).
 * - No Authorization header is injected here: the JWT lives in an httpOnly
 *   cookie that JavaScript cannot read.
 * - The only exception is `switchSociete`, where the partial token is passed
 *   explicitly by the service via an Authorization header.
 * - On 401 from a protected endpoint, redirects to /login so the user can
 *   re-authenticate and the backend will issue a fresh cookie.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  const skipAuth = req.url.endsWith('/auth/login');
  const isAuthMe = req.url.endsWith('/auth/me');

  // Attach credentials (sends the httpOnly cookie); do NOT inject a token header.
  const authReq = req.clone({ withCredentials: true });

  return next(authReq).pipe(
    tap({
      error: (err) => {
        // On 401 from protected endpoints redirect to login.
        // Skip for /auth/login to avoid a redirect loop on bad credentials.
        // Skip for /auth/me — verifySession handles that path.
        if (err.status === 401 && !skipAuth && !isAuthMe) {
          void router.navigateByUrl('/login');
        }
      },
    })
  );
};
