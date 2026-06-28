import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

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
              <input
                id="rp-pw"
                type="password"
                class="field__input"
                [class.field__input--error]="f['password'].invalid && f['password'].touched"
                formControlName="password"
                placeholder="min. 6 znaków"
                autocomplete="new-password"
                [attr.aria-invalid]="f['password'].invalid && f['password'].touched ? 'true' : null"
                aria-describedby="rp-pw-err"
              />
              @if (f['password'].invalid && f['password'].touched) {
                <p id="rp-pw-err" class="field__error" role="alert">
                  @if (f['password'].errors?.['required']) { Hasło jest wymagane. }
                  @else { Hasło musi mieć co najmniej 6 znaków. }
                </p>
              }
            </div>

            <div class="field">
              <label class="field__label" for="rp-pw2">Powtórz hasło</label>
              <input
                id="rp-pw2"
                type="password"
                class="field__input"
                [class.field__input--error]="(f['confirm'].invalid || form.errors?.['mismatch']) && f['confirm'].touched"
                formControlName="confirm"
                placeholder="••••••••"
                autocomplete="new-password"
                [attr.aria-invalid]="(f['confirm'].invalid || form.errors?.['mismatch']) && f['confirm'].touched ? 'true' : null"
                aria-describedby="rp-pw2-err"
              />
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

  form = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(6)]],
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
