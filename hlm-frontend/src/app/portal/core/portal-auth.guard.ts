import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PortalAuthService } from './portal-auth.service';

/** Protects portal routes: redirects to /portal/login if no portal JWT is present. */
export const portalGuard: CanActivateFn = () => {
  const auth   = inject(PortalAuthService);
  const router = inject(Router);

  if (auth.isLoggedIn) {
    return true;
  }
  return router.createUrlTree(['/portal/login']);
};
