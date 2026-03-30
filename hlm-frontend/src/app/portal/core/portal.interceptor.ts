import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

const PORTAL_TOKEN_KEY = 'hlm_portal_token';

/**
 * Attaches the portal JWT to outgoing requests to /api/portal/**.
 * On 401, clears the portal session and redirects to /portal/login.
 */
export const portalInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token = localStorage.getItem(PORTAL_TOKEN_KEY);

  const isPortalApi   = req.url.includes('/api/portal/');
  const isPortalAuth  = req.url.includes('/api/portal/auth/');

  const authReq = isPortalApi && !isPortalAuth && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    tap({
      error: (err) => {
        if (err.status === 401 && isPortalApi && !isPortalAuth) {
          localStorage.removeItem(PORTAL_TOKEN_KEY);
          void router.navigateByUrl('/portal/login');
        }
      },
    })
  );
};
