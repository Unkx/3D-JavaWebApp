import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  template: `
    <footer class="site-footer">
      <div class="site-footer__grid">
        <div class="site-footer__brand">
          <span class="site-footer__title">Druk3D</span>
          <p class="site-footer__tagline">Platforma łącząca zleceniodawców z drukarzami 3D.</p>
        </div>

        <nav class="site-footer__col" aria-label="Nawigacja">
          <span class="site-footer__heading">Nawigacja</span>
          <a routerLink="/zlecenia">Zlecenia</a>
          <a routerLink="/faq">FAQ</a>
        </nav>

        <nav class="site-footer__col" aria-label="Informacje prawne">
          <span class="site-footer__heading">Informacje prawne</span>
          <a routerLink="/polityka-prywatnosci">Polityka prywatności</a>
          <a routerLink="/regulamin">Regulamin</a>
        </nav>

        <div class="site-footer__col">
          <span class="site-footer__heading">Kontakt</span>
          <a href="mailto:kontakt@druk3d.pl">kontakt&#64;druk3d.pl</a>
        </div>
      </div>

      <div class="site-footer__bottom">
        <span>&#169; {{ currentYear }} Druk3D. Wszelkie prawa zastrzeżone.</span>
      </div>
    </footer>
  `,
  styles: [`
    :host { display: block; }

    .site-footer {
      border-top: 1px solid var(--border);
      background: var(--surface);
      padding: 3rem 2.5rem 2rem;
      margin-top: 3rem;
    }

    .site-footer__grid {
      display: grid;
      grid-template-columns: 1.4fr 1fr 1fr 1fr;
      gap: 2rem;
      max-width: 1160px;
      margin: 0 auto;
    }

    .site-footer__brand {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .site-footer__title {
      font-family: var(--font-display);
      font-size: 1.0625rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: var(--accent);
    }

    .site-footer__tagline {
      font-size: 0.875rem;
      color: var(--ts);
      line-height: 1.5;
      margin: 0;
      max-width: 26ch;
    }

    .site-footer__col {
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
    }

    .site-footer__heading {
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--text);
      margin-bottom: 0.25rem;
    }

    .site-footer__col a {
      font-size: 0.875rem;
      color: var(--ts);
      text-decoration: none;
      transition: color var(--t-fast);
      width: fit-content;
    }

    .site-footer__col a:hover {
      color: var(--accent);
    }

    .site-footer__bottom {
      max-width: 1160px;
      margin: 2.5rem auto 0;
      padding-top: 1.5rem;
      border-top: 1px solid var(--border);
      font-size: 0.8125rem;
      color: var(--ts);
    }

    @media (max-width: 768px) {
      .site-footer { padding: 2.5rem 1rem 1.5rem; }
      .site-footer__grid {
        grid-template-columns: 1fr 1fr;
        gap: 1.75rem;
      }
      .site-footer__brand { grid-column: 1 / -1; }
    }

    @media (max-width: 480px) {
      .site-footer__grid { grid-template-columns: 1fr; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FooterComponent {
  readonly currentYear = new Date().getFullYear();
}
