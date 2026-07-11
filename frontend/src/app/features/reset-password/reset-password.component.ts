import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { passwordComplexity } from '../auth/auth.component';

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../auth/auth.component.css'],
  template: `
    <div class="auth-page">
      <div class="auth-card">

        <a routerLink="/" class="auth-logo" aria-label="Strona główna">
          <span class="auth-logo__icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M10 2L18 6.5V13.5L10 18L2 13.5V6.5L10 2Z" fill="var(--accent)" opacity="0.9"/>
              <path d="M10 2L18 6.5L10 11L2 6.5L10 2Z" fill="white" opacity="0.22"/>
              <path d="M10 11V18L2 13.5V6.5L10 11Z" fill="white" opacity="0.1"/>
            </svg>
          </span>
          <span>Druk3D</span>
        </a>

        @if (!token()) {
          <div class="alert alert--error" role="alert">
            ⚠️ Nieprawidłowy link resetujący.
          </div>
          <p class="form__switch">
            <a routerLink="/logowanie" class="link-btn">Wróć do logowania</a>
          </p>
        } @else if (success()) {
          <div class="alert alert--success" role="status" aria-live="polite">
            Hasło zostało zmienione.
          </div>
          <p class="form__switch">
            <a routerLink="/logowanie" class="link-btn">Zaloguj się</a>
          </p>
        } @else {
          <h1 class="form__title">Nowe hasło</h1>

          @if (serverError()) {
            <div class="alert alert--error" role="alert" aria-live="assertive">
              ⚠️ {{ serverError() }}
            </div>
          }

          <form class="form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div class="field">
              <label class="field__label" for="rp-pw">Nowe hasło</label>
              <div class="field__input-wrap">
                <input
                  id="rp-pw"
                  [type]="passwordVisible() ? 'text' : 'password'"
                  class="field__input"
                  [class.field__input--error]="f['password'].invalid && f['password'].touched"
                  formControlName="password"
                  placeholder="min. 8 znaków"
                  autocomplete="new-password"
                  [attr.aria-invalid]="f['password'].invalid && f['password'].touched ? 'true' : null"
                  aria-describedby="rp-pw-err"
                />
                <button
                  type="button"
                  class="field__eye-toggle"
                  (click)="passwordVisible.set(!passwordVisible())"
                  [attr.aria-label]="passwordVisible() ? 'Ukryj hasło' : 'Pokaż hasło'"
                  tabindex="-1"
                >
                  @if (passwordVisible()) {
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/>
                      <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 11 8 11 8a13.16 13.16 0 0 1-1.67 2.68"/>
                      <path d="M6.61 6.61A13.526 13.526 0 0 0 1 12s4 8 11 8a9.74 9.74 0 0 0 5.39-1.61"/>
                      <line x1="2" x2="22" y1="2" y2="22"/>
                    </svg>
                  } @else {
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  }
                </button>
              </div>
              @if (f['password'].invalid && f['password'].touched) {
                <p id="rp-pw-err" class="field__error" role="alert">
                  @if (f['password'].errors?.['required']) { Hasło jest wymagane. }
                  @else if (f['password'].errors?.['minlength']) { Hasło musi mieć co najmniej 8 znaków. }
                  @else { Hasło musi zawierać wielką literę, małą literę i cyfrę. }
                </p>
              }
            </div>

            <div class="field">
              <label class="field__label" for="rp-pw2">Powtórz hasło</label>
              <div class="field__input-wrap">
                <input
                  id="rp-pw2"
                  [type]="passwordConfirmVisible() ? 'text' : 'password'"
                  class="field__input"
                  [class.field__input--error]="(f['confirm'].invalid || form.errors?.['mismatch']) && f['confirm'].touched"
                  formControlName="confirm"
                  placeholder="••••••••"
                  autocomplete="new-password"
                  [attr.aria-invalid]="(f['confirm'].invalid || form.errors?.['mismatch']) && f['confirm'].touched ? 'true' : null"
                  aria-describedby="rp-pw2-err"
                />
                <button
                  type="button"
                  class="field__eye-toggle"
                  (click)="passwordConfirmVisible.set(!passwordConfirmVisible())"
                  [attr.aria-label]="passwordConfirmVisible() ? 'Ukryj hasło' : 'Pokaż hasło'"
                  tabindex="-1"
                >
                  @if (passwordConfirmVisible()) {
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/>
                      <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 11 8 11 8a13.16 13.16 0 0 1-1.67 2.68"/>
                      <path d="M6.61 6.61A13.526 13.526 0 0 0 1 12s4 8 11 8a9.74 9.74 0 0 0 5.39-1.61"/>
                      <line x1="2" x2="22" y1="2" y2="22"/>
                    </svg>
                  } @else {
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  }
                </button>
              </div>
              @if (f['confirm'].touched) {
                @if (f['confirm'].errors?.['required']) {
                  <p id="rp-pw2-err" class="field__error" role="alert">Powtórz hasło.</p>
                } @else if (form.errors?.['mismatch']) {
                  <p id="rp-pw2-err" class="field__error" role="alert">Hasła nie są identyczne.</p>
                }
              }
            </div>

            <button
              type="submit"
              class="btn btn--primary btn--full"
              [disabled]="loading()"
              [attr.aria-busy]="loading()"
            >
              @if (loading()) {
                <span class="spinner" aria-hidden="true"></span> Zapisywanie...
              } @else { Ustaw nowe hasło }
            </button>
          </form>
        }

      </div>
    </div>
  `,
  styles: [`
    .form__title {
      font-size: 1.1rem;
      font-weight: 600;
      margin-bottom: 1rem;
    }
    .alert--success {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #166534;
      border-radius: 6px;
      padding: 0.75rem 1rem;
      font-size: 0.875rem;
      margin-bottom: 1rem;
    }
  `]
})
export class ResetPasswordComponent implements OnInit {
  private fb    = inject(FormBuilder);
  private auth  = inject(AuthService);
  private route = inject(ActivatedRoute);

  token       = signal<string | null>(null);
  loading     = signal(false);
  success     = signal(false);
  serverError = signal<string | null>(null);
  passwordVisible        = signal(false);
  passwordConfirmVisible = signal(false);

  form = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(8), passwordComplexity]],
    confirm:  ['', [Validators.required]]
  }, { validators: this.passwordsMatch });

  get f() { return this.form.controls; }

  ngOnInit(): void {
    const t = this.route.snapshot.queryParamMap.get('token');
    this.token.set(t);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    const { password } = this.form.getRawValue();
    this.auth.resetPassword(this.token()!, password!).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Link wygasł lub jest nieprawidłowy. Poproś o nowy.');
      }
    });
  }

  private passwordsMatch(group: AbstractControl) {
    const pw  = group.get('password')?.value;
    const pw2 = group.get('confirm')?.value;
    return pw && pw2 && pw !== pw2 ? { mismatch: true } : null;
  }
}
