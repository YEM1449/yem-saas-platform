import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService, SessionStatus } from './auth.service';
import { I18nService } from '../i18n/i18n.service';
import { environment } from '../../../environments/environment';

const API = environment.apiUrl;

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: Router, useValue: router },
        // AuthService adopts the user's language via I18nService; stub it (no ngx-translate in unit tests).
        { provide: I18nService, useValue: { adoptUserPreference: () => {} } },
      ],
    });

    service = TestBed.inject(AuthService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('user getter', () => {
    it('returns null before any session is established', () => {
      expect(service.user).toBeNull();
    });
  });

  describe('login()', () => {
    it('POST /auth/login with credentials and withCredentials:true', () => {
      service.login({ email: 'admin@acme.com', password: 'Admin123!Secure' }).subscribe();
      const req = http.expectOne(`${API}/auth/login`);
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.body.email).toBe('admin@acme.com');
      req.flush({ token: null, requiresSocieteSelection: false });
    });
  });

  describe('me()', () => {
    it('GET /auth/me with withCredentials:true', () => {
      service.me().subscribe();
      const req = http.expectOne(`${API}/auth/me`);
      expect(req.request.method).toBe('GET');
      expect(req.request.withCredentials).toBeTrue();
      req.flush({ id: 'u1', email: 'admin@acme.com', roles: ['ROLE_ADMIN'] });
    });
  });

  describe('verifySession()', () => {
    it('returns "valid" and caches the user when /auth/me succeeds', () => {
      let status: SessionStatus | undefined;
      service.verifySession().subscribe(s => (status = s));
      http.expectOne(`${API}/auth/me`).flush({ id: 'u1', email: 'admin@acme.com', roles: ['ROLE_ADMIN'] });
      expect(status).toBe('valid');
      expect(service.user).toBeTruthy();
    });

    it('returns "invalid" on 401 and clears the session', () => {
      let status: SessionStatus | undefined;
      service.verifySession().subscribe(s => (status = s));
      http.expectOne(`${API}/auth/me`).flush(null, { status: 401, statusText: 'Unauthorized' });
      expect(status).toBe('invalid');
      expect(service.user).toBeNull();
    });

    it('returns "unknown" on 500 (network/server error — keep user in app)', () => {
      let status: SessionStatus | undefined;
      service.verifySession().subscribe(s => (status = s));
      http.expectOne(`${API}/auth/me`).flush(null, { status: 500, statusText: 'Server Error' });
      expect(status).toBe('unknown');
    });

    it('returns "valid" immediately if user is already cached (no HTTP call)', () => {
      // First call to populate the cache
      service.verifySession().subscribe();
      http.expectOne(`${API}/auth/me`).flush({ id: 'u1', email: 'a@b.com', roles: [] });

      // Second call must NOT issue another HTTP request
      let status: SessionStatus | undefined;
      service.verifySession().subscribe(s => (status = s));
      http.expectNone(`${API}/auth/me`);
      expect(status).toBe('valid');
    });
  });

  describe('validateInvitation()', () => {
    it('GET /auth/invitation/:token', () => {
      service.validateInvitation('tok-abc').subscribe();
      const req = http.expectOne(`${API}/auth/invitation/tok-abc`);
      expect(req.request.method).toBe('GET');
      req.flush({ email: 'user@acme.com', societeNom: 'ACME', expiresAt: '2026-12-31' });
    });
  });

  describe('clearCachedUser()', () => {
    it('resets cached user to null so next verifySession re-fetches', () => {
      service.verifySession().subscribe();
      http.expectOne(`${API}/auth/me`).flush({ id: 'u1', email: 'a@b.com', roles: [] });
      expect(service.user).toBeTruthy();

      service.clearCachedUser();
      expect(service.user).toBeNull();
    });
  });
});
