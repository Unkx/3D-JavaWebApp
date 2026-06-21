import { Component, ChangeDetectionStrategy, inject, signal, effect } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { DOCUMENT, SlicePipe } from '@angular/common';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';
import { ConversationService } from './services/conversation.service';

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
  private conversationService = inject(ConversationService);
  unreadCount = signal(0);
  private unreadInterval: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => {
      this.doc.body.style.overflow = this.menuOpen() ? 'hidden' : '';
    });
    effect(() => {
      if (this.auth.isLoggedIn()) {
        this.pollUnread();
        this.unreadInterval = setInterval(() => this.pollUnread(), 30000);
      } else {
        this.unreadCount.set(0);
        if (this.unreadInterval) { clearInterval(this.unreadInterval); this.unreadInterval = null; }
      }
    });
  }

  private pollUnread(): void {
    this.conversationService.getUnreadCount().subscribe({
      next: data => this.unreadCount.set(data.count),
      error: () => {}
    });
  }

  toggleMenu() {
    this.menuOpen.update(v => !v);
  }

  closeMenu() {
    this.menuOpen.set(false);
  }
}
