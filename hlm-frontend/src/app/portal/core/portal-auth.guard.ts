import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { PortalAuthService } from './portal-auth.service';

/** Protects portal routes by validating the httpOnly portal session cookie. */
export const portalGuard: CanActivateFn = () => {
  const auth   = inject(PortalAuthService);
  const router = inject(Router);

  return auth.validateSession().pipe(
    map((isValid) => isValid ? true : router.createUrlTree(['/portal/login']))
  );
};
