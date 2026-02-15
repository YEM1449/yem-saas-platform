import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.verifySession().pipe(
    map((status) => {
      if (status === 'valid' || status === 'unknown') {
        // 'valid' = confirmed by backend
        // 'unknown' = network/5xx — keep user in app (token still present)
        return true;
      }
      // 'invalid' = 401/403 or no token → redirect to login
      return router.createUrlTree(['/login']);
    })
  );
};
