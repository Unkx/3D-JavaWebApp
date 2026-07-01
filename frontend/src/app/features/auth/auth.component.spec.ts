import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthComponent } from './auth.component';
import { AuthService } from '../../services/auth.service';

describe('AuthComponent', () => {
  let authStub: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    forgotPassword: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };
  let queryParams: Record<string, string>;

  function createComponent(): AuthComponent {
    return TestBed.runInInjectionContext(() => new AuthComponent());
  }

  beforeEach(() => {
    queryParams = {};
    authStub = {
      isLoggedIn: vi.fn().mockReturnValue(false),
      login: vi.fn(),
      register: vi.fn(),
      forgotPassword: vi.fn()
    };
    router = { navigate: vi.fn(), navigateByUrl: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authStub },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } }
        }
      ]
    });
  });

  describe('ngOnInit', () => {
    it('redirects home immediately when already logged in', () => {
      authStub.isLoggedIn.mockReturnValue(true);
      const component = createComponent();
      component.ngOnInit();
      expect(router.navigate).toHaveBeenCalledWith(['/']);
    });

    it('reads returnUrl from query params, defaulting to "/"', () => {
      const component = createComponent();
      component.ngOnInit();
      expect(component.returnUrl).toBe('/');
    });

    it('switches to the register tab when tab=register in query params', () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          { provide: AuthService, useValue: authStub },
          { provide: Router, useValue: router },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({ tab: 'register', returnUrl: '/zlecenia' }) } }
          }
        ]
      });
      const component = createComponent();
      component.ngOnInit();
      expect(component.activeTab()).toBe('register');
      expect(component.returnUrl).toBe('/zlecenia');
    });
  });

  describe('login form validation', () => {
    it('is invalid when empty', () => {
      const component = createComponent();
      expect(component.loginForm.invalid).toBe(true);
    });

    it('flags an invalid email', () => {
      const component = createComponent();
      component.le.email.setValue('not-an-email');
      component.le.password.setValue('secret');
      expect(component.le.email.hasError('email')).toBe(true);
      expect(component.loginForm.invalid).toBe(true);
    });

    it('is valid with a proper email and non-empty password', () => {
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('secret');
      expect(component.loginForm.valid).toBe(true);
    });
  });

  describe('login()', () => {
    it('does nothing but mark fields touched when the form is invalid', () => {
      const component = createComponent();
      component.login();
      expect(authStub.login).not.toHaveBeenCalled();
      expect(component.le.email.touched).toBe(true);
    });

    it('logs in and navigates to returnUrl on success', () => {
      authStub.login.mockReturnValue(of({ token: 't', email: 'a@b.com', role: 'USER', userId: 'u1' }));
      const component = createComponent();
      component.returnUrl = '/zlecenia';
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('secret');

      component.login();

      expect(authStub.login).toHaveBeenCalledWith({ email: 'a@b.com', password: 'secret' });
      expect(component.loading()).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/zlecenia');
    });

    it('sets serverError from the API message on failure', () => {
      authStub.login.mockReturnValue(throwError(() => new HttpErrorResponse({ error: { message: 'Zły login' }, status: 401 })));
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('wrong');

      component.login();

      expect(component.loading()).toBe(false);
      expect(component.serverError()).toBe('Zły login');
    });

    it('falls back to a default error message when the API omits one', () => {
      authStub.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('wrong');

      component.login();

      expect(component.serverError()).toBe('Nieprawidłowy email lub hasło.');
    });
  });

  describe('register form validation', () => {
    it('flags a mismatch between password and passwordConfirm', () => {
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('secret1');
      component.re.passwordConfirm.setValue('secret2');
      expect(component.registerForm.hasError('mismatch')).toBe(true);
      expect(component.registerForm.invalid).toBe(true);
    });

    it('is valid when passwords match and meet minLength', () => {
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('secret1');
      component.re.passwordConfirm.setValue('secret1');
      expect(component.registerForm.valid).toBe(true);
    });

    it('rejects passwords shorter than 6 characters', () => {
      const component = createComponent();
      component.re.password.setValue('abc');
      expect(component.re.password.hasError('minlength')).toBe(true);
    });
  });

  describe('register()', () => {
    it('does nothing but mark fields touched when the form is invalid', () => {
      const component = createComponent();
      component.register();
      expect(authStub.register).not.toHaveBeenCalled();
      expect(component.re.email.touched).toBe(true);
    });

    it('registers and navigates to /profil on success, trimming an empty adminCode away', () => {
      authStub.register.mockReturnValue(of({ token: 't', email: 'a@b.com', role: 'USER', userId: 'u1' }));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('secret1');
      component.re.passwordConfirm.setValue('secret1');
      component.re.adminCode.setValue('   ');

      component.register();

      expect(authStub.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'secret1', adminCode: undefined });
      expect(router.navigate).toHaveBeenCalledWith(['/profil']);
    });

    it('passes a trimmed adminCode through when provided', () => {
      authStub.register.mockReturnValue(of({ token: 't', email: 'a@b.com', role: 'ADMIN', userId: 'u1' }));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('secret1');
      component.re.passwordConfirm.setValue('secret1');
      component.re.adminCode.setValue('  CODE1  ');

      component.register();

      expect(authStub.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'secret1', adminCode: 'CODE1' });
    });

    it('sets serverError on failure', () => {
      authStub.register.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('secret1');
      component.re.passwordConfirm.setValue('secret1');

      component.register();

      expect(component.serverError()).toBe('Rejestracja nie powiodła się.');
    });
  });

  describe('submitForgot()', () => {
    it('does nothing but mark fields touched when the form is invalid', () => {
      const component = createComponent();
      component.submitForgot();
      expect(authStub.forgotPassword).not.toHaveBeenCalled();
      expect(component.fe.email.touched).toBe(true);
    });

    it('sets forgotSent on success', () => {
      authStub.forgotPassword.mockReturnValue(of(undefined));
      const component = createComponent();
      component.fe.email.setValue('a@b.com');

      component.submitForgot();

      expect(authStub.forgotPassword).toHaveBeenCalledWith('a@b.com');
      expect(component.forgotSent()).toBe(true);
      expect(component.loading()).toBe(false);
    });

    it('also sets forgotSent on failure, to avoid email enumeration', () => {
      authStub.forgotPassword.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      const component = createComponent();
      component.fe.email.setValue('a@b.com');

      component.submitForgot();

      expect(component.forgotSent()).toBe(true);
    });
  });

  describe('switchTab()', () => {
    it('resets serverError, forgotSent, and the forgot form', () => {
      const component = createComponent();
      component.serverError.set('oops');
      component.forgotSent.set(true);
      component.fe.email.setValue('a@b.com');

      component.switchTab('register');

      expect(component.activeTab()).toBe('register');
      expect(component.serverError()).toBeNull();
      expect(component.forgotSent()).toBe(false);
      expect(component.fe.email.value).toBeNull();
    });
  });
});
