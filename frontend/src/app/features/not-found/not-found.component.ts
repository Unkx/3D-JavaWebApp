import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FailedPrintSceneComponent } from '../../components/failed-print-scene.component';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, FailedPrintSceneComponent],
  template: `
    <div class="not-found">
      <app-failed-print-scene />
      <div class="not-found__content">
        <span class="not-found__eyebrow">BŁĄD 404</span>
        <h1 class="not-found__headline">Ten wydruk się nie udał.</h1>
        <p class="not-found__subtext">
          Strona, której szukasz, odkleiła się od stołu roboczego — albo nigdy nie istniała.
        </p>
        <div class="not-found__actions">
          <a routerLink="/" class="btn btn--primary btn--lg">Strona główna</a>
          <a routerLink="/zlecenia" class="btn btn--outline btn--lg">Przeglądaj zlecenia</a>
          <a href="mailto:admin@druk3d.pl?subject=Zg%C5%82oszenie%20problemu%20-%20404" class="not-found__report">
            Zgłoś problem
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .not-found {
      position: fixed;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--bg);
      overflow: hidden;
    }

    .not-found__content {
      position: relative;
      z-index: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.75rem;
      max-width: 32rem;
      padding: 2rem;
      /* Halo against --bg so copy stays legible wherever the animated scene overlaps it. */
      text-shadow: 0 0 18px var(--bg), 0 0 8px var(--bg), 0 0 8px var(--bg);
    }

    .not-found__eyebrow {
      font-family: var(--font-display);
      font-size: 0.8125rem;
      font-weight: 800;
      letter-spacing: 0.12em;
      color: var(--text-primary);
    }

    .not-found__headline {
      font-family: var(--font-display);
      font-size: clamp(1.75rem, 4vw, 2.75rem);
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
    }

    .not-found__subtext {
      font-size: 1rem;
      color: var(--text-body);
      margin: 0 0 0.5rem;
    }

    .not-found__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }

    .not-found__report {
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--ts);
      text-decoration: underline;
    }

    .not-found__report:hover { color: var(--accent); }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.7rem 1.375rem;
      border-radius: var(--radius-md);
      font-size: 0.9375rem;
      font-weight: 600;
      font-family: inherit;
      cursor: pointer;
      text-decoration: none;
      border: none;
      text-shadow: none;
      transition: background var(--t-fast), box-shadow var(--t-base), transform var(--t-fast), border-color var(--t-fast);
    }

    .btn--primary {
      background: var(--accent);
      color: #fff;
      box-shadow: var(--shadow-accent);
    }

    .btn--primary:hover {
      background: var(--ad);
      box-shadow: var(--shadow-accent-lg);
      transform: scale(1.01);
    }

    .btn--outline {
      background: transparent;
      color: var(--tb);
      border: 1px solid var(--border);
    }

    .btn--outline:hover {
      background: var(--al);
      border-color: var(--ab);
      color: var(--accent);
      transform: scale(1.01);
    }

    .btn--lg {
      padding: 0.8125rem 1.625rem;
      font-size: 1rem;
    }

    @media (max-width: 480px) {
      .not-found__actions { flex-direction: column; width: 100%; }
      .btn--lg { width: 100%; justify-content: center; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotFoundComponent {}
