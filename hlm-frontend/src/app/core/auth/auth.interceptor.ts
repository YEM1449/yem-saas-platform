import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;

  // Don't attach token to login requests (public endpoint)
  const skipAuth = req.url.endsWith('/auth/login');

  const authReq = token && !skipAuth
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    tap({
      error: (err) => {
        // On 401 from protected endpoints, clear token and redirect to login.
        // Skip for /auth/login itself to avoid redirect loop on bad credentials.
        if (err.status === 401 && !skipAuth) {
          auth.logout();
        }
      },
    })
  );
};
