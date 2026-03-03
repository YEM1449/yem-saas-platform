import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { PortalAuthService } from './portal-auth.service';

/**
 * Attaches the portal JWT to outgoing requests to /api/portal/**.
 * On 401, clears the portal session and redirects to /portal/login.
 */
export const portalInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(PortalAuthService);

  const isPortalApi   = req.url.includes('/api/portal/');
  const isPortalAuth  = req.url.includes('/api/portal/auth/');

  const authReq = isPortalApi && !isPortalAuth && auth.token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } })
    : req;

  return next(authReq).pipe(
    tap({
      error: (err) => {
        if (err.status === 401 && isPortalApi && !isPortalAuth) {
          auth.logout();
        }
      },
    })
  );
};
