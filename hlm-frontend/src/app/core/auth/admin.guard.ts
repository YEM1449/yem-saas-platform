import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.verifySession().pipe(
    map((status) => {
      if (status === 'valid' && auth.user?.role === 'ROLE_ADMIN') {
        return true;
      }
      if (status === 'invalid') {
        return router.createUrlTree(['/login']);
      }
      // Not admin or unknown session — redirect to app home
      return router.createUrlTree(['/app']);
    })
  );
};
