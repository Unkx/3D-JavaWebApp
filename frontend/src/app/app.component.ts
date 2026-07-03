import { Component, ChangeDetectionStrategy, inject, signal, effect, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { DOCUMENT, SlicePipe } from '@angular/common';
import { Subscription, filter } from 'rxjs';
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
export class AppComponent implements OnDestroy {
  auth = inject(AuthService);
  theme = inject(ThemeService);
  menuOpen = signal(false);
  offline = signal(!navigator.onLine);
  isFullscreenRoute = signal(false);
  private doc = inject(DOCUMENT);
  private conversationService = inject(ConversationService);
  private router = inject(Router);
  unreadCount = signal(0);
  private unreadInterval: ReturnType<typeof setInterval> | null = null;
  private onOnline = () => this.offline.set(false);
  private onOffline = () => this.offline.set(true);
  private routerSub: Subscription;

  constructor() {
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);

    this.routerSub = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        let route = this.router.routerState.snapshot.root;
        while (route.firstChild) {
          route = route.firstChild;
        }
        this.isFullscreenRoute.set(!!route.data['fullscreen']);
      });

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

  ngOnDestroy(): void {
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
    this.routerSub.unsubscribe();
  }
}
