import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

type Tab = 'login' | 'register';

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

  activeTab   = signal<Tab>('login');
  loading     = signal(false);
  serverError = signal<string | null>(null);
  returnUrl   = '/';

  loginForm = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  registerForm = this.fb.group({
    email:           ['', [Validators.required, Validators.email]],
    password:        ['', [Validators.required, Validators.minLength(6)]],
    passwordConfirm: ['', [Validators.required]]
  }, { validators: this.passwordsMatch });

  get le() { return this.loginForm.controls; }
  get re() { return this.registerForm.controls; }

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) { this.router.navigate(['/']); return; }
    this.returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
    const tab = this.route.snapshot.queryParamMap.get('tab');
    if (tab === 'register') this.activeTab.set('register');
  }

  switchTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.serverError.set(null);
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

  register(): void {
    if (this.registerForm.invalid) { this.registerForm.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    const { email, password } = this.registerForm.getRawValue();
    this.auth.register({ email: email!, password: password! }).subscribe({
      next: () => { this.loading.set(false); this.router.navigate(['/profil']); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Rejestracja nie powiodła się.');
      }
    });
  }

  private passwordsMatch(group: AbstractControl) {
    const pw  = group.get('password')?.value;
    const pw2 = group.get('passwordConfirm')?.value;
    return pw && pw2 && pw !== pw2 ? { mismatch: true } : null;
  }
}
