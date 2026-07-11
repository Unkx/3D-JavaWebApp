import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthComponent } from './auth.component';
import { AuthService } from '../../services/auth.service';
import { FacebookAuthService } from '../../services/facebook-auth.service';
import { GoogleAuthService } from '../../services/google-auth.service';

describe('AuthComponent', () => {
  let authStub: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    forgotPassword: ReturnType<typeof vi.fn>;
    loginWithFacebook: ReturnType<typeof vi.fn>;
    loginWithGoogle: ReturnType<typeof vi.fn>;
    resendVerification: ReturnType<typeof vi.fn>;
  };
  let facebookAuthStub: { login: ReturnType<typeof vi.fn> };
  let googleAuthStub: { renderButton: ReturnType<typeof vi.fn> };
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
      forgotPassword: vi.fn(),
      loginWithFacebook: vi.fn(),
      loginWithGoogle: vi.fn(),
      resendVerification: vi.fn()
    };
    facebookAuthStub = { login: vi.fn() };
    googleAuthStub = { renderButton: vi.fn().mockResolvedValue(undefined) };
    router = { navigate: vi.fn(), navigateByUrl: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authStub },
        { provide: FacebookAuthService, useValue: facebookAuthStub },
        { provide: GoogleAuthService, useValue: googleAuthStub },
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
          { provide: FacebookAuthService, useValue: facebookAuthStub },
          { provide: GoogleAuthService, useValue: googleAuthStub },
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

  describe('login() when the account is unverified', () => {
    it('sets unverifiedEmail so the resend button can appear', () => {
      authStub.login.mockReturnValue(
        throwError(() => new HttpErrorResponse({ error: { message: 'Potwierdź adres email.' }, status: 403 }))
      );
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('secret');

      component.login();

      expect(component.serverError()).toBe('Potwierdź adres email.');
      expect(component.unverifiedEmail()).toBe('a@b.com');
    });

    it('does not set unverifiedEmail for other error statuses', () => {
      authStub.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('wrong');

      component.login();

      expect(component.unverifiedEmail()).toBeNull();
    });
  });

  describe('resendVerification()', () => {
    it('calls the API with unverifiedEmail and sets resendVerificationSent', () => {
      authStub.resendVerification.mockReturnValue(of(undefined));
      const component = createComponent();
      component.unverifiedEmail.set('a@b.com');

      component.resendVerification();

      expect(authStub.resendVerification).toHaveBeenCalledWith('a@b.com');
      expect(component.resendVerificationSent()).toBe(true);
    });
  });

  describe('register form validation', () => {
    it('flags a mismatch between password and passwordConfirm', () => {
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret13');
      expect(component.registerForm.hasError('mismatch')).toBe(true);
      expect(component.registerForm.invalid).toBe(true);
    });

    it('is valid when passwords match and meet strength rules', () => {
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');
      expect(component.registerForm.valid).toBe(true);
    });

    it('rejects passwords shorter than 8 characters', () => {
      const component = createComponent();
      component.re.password.setValue('Abc123');
      expect(component.re.password.hasError('minlength')).toBe(true);
    });

    it('rejects passwords missing an uppercase letter', () => {
      const component = createComponent();
      component.re.password.setValue('secret123');
      expect(component.re.password.hasError('complexity')).toBe(true);
    });

    it('rejects passwords missing a digit', () => {
      const component = createComponent();
      component.re.password.setValue('SecretPass');
      expect(component.re.password.hasError('complexity')).toBe(true);
    });
  });

  describe('register()', () => {
    it('does nothing but mark fields touched when the form is invalid', () => {
      const component = createComponent();
      component.register();
      expect(authStub.register).not.toHaveBeenCalled();
      expect(component.re.email.touched).toBe(true);
    });

    it('registers and shows the registerSent state on success, trimming an empty adminCode away', () => {
      authStub.register.mockReturnValue(of(undefined));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');
      component.re.adminCode.setValue('   ');

      component.register();

      expect(authStub.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'Secret12', adminCode: undefined });
      expect(component.registerSent()).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('passes a trimmed adminCode through when provided', () => {
      authStub.register.mockReturnValue(of(undefined));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');
      component.re.adminCode.setValue('  CODE1  ');

      component.register();

      expect(authStub.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'Secret12', adminCode: 'CODE1' });
    });

    it('sets serverError on failure', () => {
      authStub.register.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');

      component.register();

      expect(component.serverError()).toBe('Rejestracja nie powiodła się.');
      expect(component.registerSent()).toBe(false);
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

  describe('loginWithFacebook()', () => {
    it('logs in and navigates to returnUrl when Facebook login succeeds', async () => {
      facebookAuthStub.login.mockResolvedValue('fb-token');
      authStub.loginWithFacebook.mockReturnValue(of({ token: 't', email: 'a@b.com', role: 'USER', userId: 'u1' }));
      const component = createComponent();
      component.returnUrl = '/zlecenia';

      await component.loginWithFacebook();

      expect(authStub.loginWithFacebook).toHaveBeenCalledWith('fb-token');
      expect(component.loading()).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/zlecenia');
    });

    it('resets loading without error when the user cancels the Facebook popup', async () => {
      facebookAuthStub.login.mockResolvedValue(null);
      const component = createComponent();

      await component.loginWithFacebook();

      expect(component.loading()).toBe(false);
      expect(component.serverError()).toBeNull();
      expect(authStub.loginWithFacebook).not.toHaveBeenCalled();
    });

    it('sets serverError when the backend rejects the Facebook token', async () => {
      facebookAuthStub.login.mockResolvedValue('fb-token');
      authStub.loginWithFacebook.mockReturnValue(
        throwError(() => new HttpErrorResponse({ error: { message: 'Zły token' }, status: 401 }))
      );
      const component = createComponent();

      await component.loginWithFacebook();

      expect(component.loading()).toBe(false);
      expect(component.serverError()).toBe('Zły token');
    });
  });

  describe('initGoogleButton()', () => {
    it('renders the Google button into the expected container', async () => {
      const component = createComponent();

      await component.initGoogleButton();

      expect(googleAuthStub.renderButton).toHaveBeenCalledWith('google-btn-container', expect.any(Function));
    });
  });

  describe('handleGoogleToken()', () => {
    it('logs in and navigates to returnUrl on success', () => {
      authStub.loginWithGoogle.mockReturnValue(of({ token: 't', email: 'a@b.com', role: 'USER', userId: 'u1' }));
      const component = createComponent();
      component.returnUrl = '/zlecenia';

      component.handleGoogleToken('google-token-123');

      expect(authStub.loginWithGoogle).toHaveBeenCalledWith('google-token-123');
      expect(component.loading()).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/zlecenia');
    });

    it('sets serverError when the backend rejects the Google token', () => {
      authStub.loginWithGoogle.mockReturnValue(
        throwError(() => new HttpErrorResponse({ error: { message: 'Konto z tym adresem email już istnieje.' }, status: 409 }))
      );
      const component = createComponent();

      component.handleGoogleToken('google-token-123');

      expect(component.loading()).toBe(false);
      expect(component.serverError()).toBe('Konto z tym adresem email już istnieje.');
    });

    it('falls back to a default error message when the API omits one', () => {
      authStub.loginWithGoogle.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      const component = createComponent();

      component.handleGoogleToken('google-token-123');

      expect(component.serverError()).toBe('Logowanie przez Google nie powiodło się.');
    });
  });
});
