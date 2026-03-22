import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.verifySession().pipe(
    map((status) => {
      if (status === 'invalid') {
        return router.createUrlTree(['/login']);
      }
      // SUPER_ADMIN can access admin pages too (they manage company memberships)
      if (status === 'valid' &&
          (auth.user?.role === 'ROLE_ADMIN' || auth.user?.role === 'ROLE_SUPER_ADMIN')) {
        return true;
      }
      // MANAGER / AGENT — redirect to app home
      return router.createUrlTree(['/app']);
    })
  );
};
