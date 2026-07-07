import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { FacebookAuthService } from '../../services/facebook-auth.service';
import { GoogleAuthService } from '../../services/google-auth.service';
import { HttpErrorResponse } from '@angular/common/http';

type Tab = 'login' | 'register' | 'forgot';

@Component({
  selector: 'app-auth',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth.component.html',
  styleUrl: './auth.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuthComponent implements OnInit {
  private fb      = inject(FormBuilder);
  private auth    = inject(AuthService);
  private router  = inject(Router);
  private route   = inject(ActivatedRoute);
  private facebookAuth = inject(FacebookAuthService);
  private googleAuth = inject(GoogleAuthService);

  activeTab   = signal<Tab>('login');
  loading     = signal(false);
  serverError = signal<string | null>(null);
  forgotSent  = signal(false);
  returnUrl   = '/';

  loginForm = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  registerForm = this.fb.group({
    email:           ['', [Validators.required, Validators.email]],
    password:        ['', [Validators.required, Validators.minLength(6)]],
    passwordConfirm: ['', [Validators.required]],
    adminCode:       ['']
  }, { validators: this.passwordsMatch });

  forgotForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  get le() { return this.loginForm.controls; }
  get re() { return this.registerForm.controls; }
  get fe() { return this.forgotForm.controls; }

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) { this.router.navigate(['/']); return; }
    this.returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
    const tab = this.route.snapshot.queryParamMap.get('tab');
    if (tab === 'register') this.activeTab.set('register');
    this.initGoogleButton();
  }

  switchTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.serverError.set(null);
    this.forgotSent.set(false);
    this.forgotForm.reset();
  }

  login(): void {
    if (this.loginForm.invalid) { this.loginForm.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    const { email, password } = this.loginForm.getRawValue();
    this.auth.login({ email: email!, password: password! }).subscribe({
      next: () => { this.loading.set(false); this.router.navigateByUrl(this.returnUrl); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Nieprawidłowy email lub hasło.');
      }
    });
  }

  loginWithFacebook(): Promise<void> {
    this.loading.set(true);
    this.serverError.set(null);
    return this.facebookAuth.login().then(accessToken => {
      if (!accessToken) { this.loading.set(false); return; }
      this.auth.loginWithFacebook(accessToken).subscribe({
        next: () => { this.loading.set(false); this.router.navigateByUrl(this.returnUrl); },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.serverError.set(err.error?.message ?? 'Logowanie przez Facebook nie powiodło się.');
        }
      });
    });
  }

  initGoogleButton(): Promise<void> {
    return this.googleAuth.renderButton('google-btn-container', (idToken) => this.handleGoogleToken(idToken));
  }

  handleGoogleToken(idToken: string): void {
    this.loading.set(true);
    this.serverError.set(null);
    this.auth.loginWithGoogle(idToken).subscribe({
      next: () => { this.loading.set(false); this.router.navigateByUrl(this.returnUrl); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Logowanie przez Google nie powiodło się.');
      }
    });
  }

  register(): void {
    if (this.registerForm.invalid) { this.registerForm.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    const { email, password, adminCode } = this.registerForm.getRawValue();
    this.auth.register({ email: email!, password: password!, adminCode: adminCode?.trim() || undefined }).subscribe({
      next: () => { this.loading.set(false); this.router.navigate(['/profil']); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Rejestracja nie powiodła się.');
      }
    });
  }

  submitForgot(): void {
    if (this.forgotForm.invalid) { this.forgotForm.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    const { email } = this.forgotForm.getRawValue();
    this.auth.forgotPassword(email!).subscribe({
      next: () => {
        this.loading.set(false);
        this.forgotSent.set(true);
      },
      error: () => {
        this.loading.set(false);
        // still show success to avoid email enumeration
        this.forgotSent.set(true);
      }
    });
  }

  private passwordsMatch(group: AbstractControl) {
    const pw  = group.get('password')?.value;
    const pw2 = group.get('passwordConfirm')?.value;
    return pw && pw2 && pw !== pw2 ? { mismatch: true } : null;
  }
}
