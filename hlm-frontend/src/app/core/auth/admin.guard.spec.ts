import { TestBed } from '@angular/core/testing';
import { Router, RouterStateSnapshot, ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';
import { MeResponse } from '../models/login.model';

describe('adminGuard', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const run = () =>
    TestBed.runInInjectionContext(() =>
      adminGuard(
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot
      )
    ) as ReturnType<typeof adminGuard>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', ['verifySession'], { user: null });
    routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);
    routerSpy.createUrlTree.and.callFake((commands: unknown[]) => commands as unknown as ReturnType<Router['createUrlTree']>);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router,       useValue: routerSpy },
      ],
    });
  });

  it('should return true for ROLE_ADMIN', (done) => {
    Object.defineProperty(authSpy, 'user', { get: () => ({ role: 'ROLE_ADMIN' } as MeResponse) });
    authSpy.verifySession.and.returnValue(of('valid'));

    (run() as ReturnType<typeof of>).subscribe((result: unknown) => {
      expect(result).toBeTrue();
      done();
    });
  });

  it('should return true for ROLE_SUPER_ADMIN (super admin can access admin pages)', (done) => {
    Object.defineProperty(authSpy, 'user', { get: () => ({ role: 'ROLE_SUPER_ADMIN' } as MeResponse) });
    authSpy.verifySession.and.returnValue(of('valid'));

    (run() as ReturnType<typeof of>).subscribe((result: unknown) => {
      expect(result).toBeTrue();
      done();
    });
  });

  it('should redirect to /login when session is invalid', (done) => {
    authSpy.verifySession.and.returnValue(of('invalid'));

    (run() as ReturnType<typeof of>).subscribe((result: unknown) => {
      expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/login']);
      done();
    });
  });

  it('should redirect to /app for ROLE_MANAGER', (done) => {
    Object.defineProperty(authSpy, 'user', { get: () => ({ role: 'ROLE_MANAGER' } as MeResponse) });
    authSpy.verifySession.and.returnValue(of('valid'));

    (run() as ReturnType<typeof of>).subscribe((result: unknown) => {
      expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/app']);
      done();
    });
  });

  it('should redirect to /app for ROLE_AGENT', (done) => {
    Object.defineProperty(authSpy, 'user', { get: () => ({ role: 'ROLE_AGENT' } as MeResponse) });
    authSpy.verifySession.and.returnValue(of('valid'));

    (run() as ReturnType<typeof of>).subscribe((result: unknown) => {
      expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/app']);
      done();
    });
  });
});
