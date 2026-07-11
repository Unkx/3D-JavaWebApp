import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService, AuthUser } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn> };

  const user: AuthUser = { token: 'abc123', email: 'a@b.com', role: 'USER', userId: 'u1' };

  beforeEach(() => {
    localStorage.clear();
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created with no user when storage is empty', () => {
    expect(service.currentUser()).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('loads a persisted user from localStorage on construction', () => {
    localStorage.setItem('auth_token', JSON.stringify(user));
    // The service reads storage at construction time, and the outer beforeEach already
    // constructed+cached one instance with empty storage — reset so a fresh instance is built.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: router }]
    });
    const fresh = TestBed.inject(AuthService);
    expect(fresh.currentUser()).toEqual(user);
    expect(fresh.isLoggedIn()).toBe(true);
  });

  it('treats malformed localStorage content as no user', () => {
    localStorage.setItem('auth_token', '{not-json');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: router }]
    });
    const fresh = TestBed.inject(AuthService);
    expect(fresh.currentUser()).toBeNull();
  });

  it('login() POSTs credentials and persists the returned user', () => {
    let result: AuthUser | undefined;
    service.login({ email: user.email, password: 'pw' }).subscribe(u => (result = u));

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: user.email, password: 'pw' });
    req.flush(user);

    expect(result).toEqual(user);
    expect(service.currentUser()).toEqual(user);
    expect(service.isLoggedIn()).toBe(true);
    expect(JSON.parse(localStorage.getItem('auth_token')!)).toEqual(user);
  });

  it('register() POSTs payload and persists the returned user', () => {
    const admin: AuthUser = { ...user, role: 'ADMIN' };
    service.register({ email: user.email, password: 'pw', adminCode: 'CODE' }).subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: user.email, password: 'pw', adminCode: 'CODE' });
    req.flush(admin);

    expect(service.currentUser()).toEqual(admin);
    expect(service.isAdmin()).toBe(true);
  });

  it('loginWithFacebook() POSTs the access token and persists the returned user', () => {
    service.loginWithFacebook('fb-token-123').subscribe();
    const req = httpMock.expectOne('/api/auth/facebook');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accessToken: 'fb-token-123' });
    req.flush(user);
    expect(service.currentUser()).toEqual(user);
  });

  it('loginWithGoogle() POSTs the id token and persists the returned user', () => {
    service.loginWithGoogle('google-id-token-123').subscribe();
    const req = httpMock.expectOne('/api/auth/google');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ idToken: 'google-id-token-123' });
    req.flush(user);
    expect(service.currentUser()).toEqual(user);
  });

  it('forgotPassword() POSTs the email and does not persist a user', () => {
    service.forgotPassword('a@b.com').subscribe();
    const req = httpMock.expectOne('/api/auth/forgot-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com' });
    req.flush(null);
    expect(service.currentUser()).toBeNull();
  });

  it('resetPassword() POSTs token and newPassword', () => {
    service.resetPassword('tok', 'newpw').subscribe();
    const req = httpMock.expectOne('/api/auth/reset-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok', newPassword: 'newpw' });
    req.flush(null);
  });

  it('redeemAdminCode() POSTs the code and persists the refreshed user', () => {
    const admin: AuthUser = { ...user, role: 'ADMIN' };
    service.redeemAdminCode('CODE1').subscribe();
    const req = httpMock.expectOne('/api/admin/redeem');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'CODE1' });
    req.flush(admin);
    expect(service.isAdmin()).toBe(true);
  });

  it('generateAdminCode() POSTs an empty body', () => {
    service.generateAdminCode().subscribe();
    const req = httpMock.expectOne('/api/admin/codes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ code: 'X', used: false, createdByEmail: 'a@b.com', usedByEmail: null, createdAt: '', redeemedAt: null });
  });

  it('listAdminCodes() GETs the codes list', () => {
    service.listAdminCodes().subscribe();
    const req = httpMock.expectOne('/api/admin/codes');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getToken() returns the current user token, or null when logged out', () => {
    expect(service.getToken()).toBeNull();
    service.login({ email: user.email, password: 'pw' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(user);
    expect(service.getToken()).toBe(user.token);
  });

  it('logout() clears storage, resets the user signal, and navigates home', () => {
    service.login({ email: user.email, password: 'pw' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(user);

    service.logout();

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('propagates HTTP errors from login()', () => {
    let error: unknown;
    service.login({ email: user.email, password: 'wrong' }).subscribe({
      error: (e) => (error = e)
    });
    httpMock.expectOne('/api/auth/login').flush({ message: 'Bad credentials' }, { status: 401, statusText: 'Unauthorized' });
    expect((error as { status: number }).status).toBe(401);
    expect(service.currentUser()).toBeNull();
  });
});
