import { Component, ChangeDetectionStrategy, inject, signal, effect } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { DOCUMENT, SlicePipe } from '@angular/common';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, SlicePipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  auth = inject(AuthService);
  theme = inject(ThemeService);
  menuOpen = signal(false);
  private doc = inject(DOCUMENT);

  constructor() {
    effect(() => {
      this.doc.body.style.overflow = this.menuOpen() ? 'hidden' : '';
    });
  }

  toggleMenu() {
    this.menuOpen.update(v => !v);
  }

  closeMenu() {
    this.menuOpen.set(false);
  }
}
