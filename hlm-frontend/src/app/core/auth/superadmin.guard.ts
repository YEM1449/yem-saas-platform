import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const superadminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.verifySession().pipe(
    map((status) => {
      if (status === 'invalid') {
        return router.createUrlTree(['/login']);
      }
      if (status === 'valid' && auth.user?.role === 'ROLE_SUPER_ADMIN') {
        return true;
      }
      // Valid session but not super admin — redirect to app
      return router.createUrlTree(['/app']);
    })
  );
};
