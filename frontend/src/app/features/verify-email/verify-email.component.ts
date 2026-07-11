import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-verify-email',
  imports: [RouterLink],
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

        @if (loading()) {
          <p class="form__switch">Potwierdzanie...</p>
        } @else if (success()) {
          <div class="alert alert--success" role="status" aria-live="polite">
            Konto potwierdzone!
          </div>
          <p class="form__switch">
            <a routerLink="/logowanie" class="link-btn">Zaloguj się</a>
          </p>
        } @else {
          <div class="alert alert--error" role="alert">
            ⚠️ Link wygasł lub jest nieprawidłowy.
          </div>
          @if (resendSent()) {
            <div class="alert alert--success" role="status" aria-live="polite">
              Wysłano nowy link. Sprawdź swoją skrzynkę pocztową.
            </div>
          } @else {
            <div class="field">
              <label class="field__label" for="ve-email">Email</label>
              <input
                id="ve-email"
                type="email"
                class="field__input"
                [value]="email() ?? ''"
                (input)="email.set($any($event.target).value)"
                placeholder="jan@kowalski.pl"
                autocomplete="email"
              />
            </div>
            <button type="button" class="btn btn--primary btn--full" (click)="resend()" [disabled]="!email()">
              Wyślij nowy link
            </button>
          }
          <p class="form__switch">
            <a routerLink="/logowanie" class="link-btn">Wróć do logowania</a>
          </p>
        }

      </div>
    </div>
  `,
  styles: [`
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
export class VerifyEmailComponent implements OnInit {
  private auth  = inject(AuthService);
  private route = inject(ActivatedRoute);

  loading    = signal(true);
  success    = signal(false);
  resendSent = signal(false);
  email      = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => { this.loading.set(false); }
    });
  }

  resend(): void {
    const email = this.email();
    if (!email) return;
    this.auth.resendVerification(email).subscribe({
      next: () => this.resendSent.set(true),
      error: () => this.resendSent.set(true) // anti-enumeration: same as forgot-password
    });
  }
}
