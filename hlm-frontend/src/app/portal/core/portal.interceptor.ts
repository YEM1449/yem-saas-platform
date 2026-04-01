import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { PortalSessionStore } from './portal-session.store';

/**
 * Ensures portal requests include credentials so the httpOnly portal cookie is sent.
 * On 401, clears the cached session flag and redirects to /portal/login.
 */
export const portalInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const session = inject(PortalSessionStore);

  const isPortalApi   = req.url.includes('/api/portal/');
  const authReq = isPortalApi ? req.clone({ withCredentials: true }) : req;

  return next(authReq).pipe(
    tap({
      error: (err) => {
        if (err.status === 401 && isPortalApi) {
          session.clear();
          void router.navigateByUrl('/portal/login');
        }
      },
    })
  );
};
