import { Injectable, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

// Tune these to whatever idle policy is wanted; both are in milliseconds.
const IDLE_TIMEOUT_MS = 30 * 60 * 1000;   // total inactivity before auto logout
const WARNING_BEFORE_MS = 60 * 1000;      // show the countdown banner this long before logout
const ACTIVITY_THROTTLE_MS = 5000;        // ignore activity bursts faster than this
const STORAGE_KEY = 'idle_last_activity';
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'click'] as const;

/**
 * Logs the user out after a period of no interaction. Shares the "last activity"
 * timestamp across tabs via localStorage/the storage event, so a background tab
 * doesn't quietly stay authenticated while the user is idle in every other tab.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  private auth = inject(AuthService);

  readonly warningVisible = signal(false);
  readonly secondsRemaining = signal(0);

  private warnTimer: ReturnType<typeof setTimeout> | null = null;
  private logoutTimer: ReturnType<typeof setTimeout> | null = null;
  private countdownInterval: ReturnType<typeof setInterval> | null = null;
  private started = false;
  private lastRegisteredAt = 0;

  private readonly onActivity = () => this.registerActivity();
  private readonly onStorage = (e: StorageEvent) => {
    if (e.key === STORAGE_KEY && e.newValue) {
      this.reschedule(Number(e.newValue));
    }
  };

  start(): void {
    if (this.started) return;
    this.started = true;
    ACTIVITY_EVENTS.forEach(evt => window.addEventListener(evt, this.onActivity, { passive: true }));
    window.addEventListener('storage', this.onStorage);
    this.registerActivity();
  }

  stop(): void {
    if (!this.started) return;
    this.started = false;
    ACTIVITY_EVENTS.forEach(evt => window.removeEventListener(evt, this.onActivity));
    window.removeEventListener('storage', this.onStorage);
    this.clearTimers();
    this.warningVisible.set(false);
  }

  /** "Stay logged in" button — counts as activity like any other interaction. */
  extend(): void {
    this.registerActivity();
  }

  private registerActivity(): void {
    const now = Date.now();
    if (now - this.lastRegisteredAt < ACTIVITY_THROTTLE_MS && !this.warningVisible()) return;
    this.lastRegisteredAt = now;
    localStorage.setItem(STORAGE_KEY, String(now));
    this.reschedule(now);
  }

  private reschedule(lastActivity: number): void {
    this.clearTimers();
    this.warningVisible.set(false);

    const remaining = IDLE_TIMEOUT_MS - (Date.now() - lastActivity);
    if (remaining <= 0) {
      this.logout();
      return;
    }

    const warnIn = remaining - WARNING_BEFORE_MS;
    if (warnIn <= 0) {
      this.showWarning(remaining);
    } else {
      this.warnTimer = setTimeout(() => this.showWarning(WARNING_BEFORE_MS), warnIn);
      this.logoutTimer = setTimeout(() => this.logout(), remaining);
    }
  }

  private showWarning(msRemaining: number): void {
    this.warningVisible.set(true);
    this.secondsRemaining.set(Math.ceil(msRemaining / 1000));
    this.logoutTimer = setTimeout(() => this.logout(), msRemaining);
    this.countdownInterval = setInterval(() => {
      this.secondsRemaining.update(s => Math.max(0, s - 1));
    }, 1000);
  }

  private logout(): void {
    this.clearTimers();
    this.warningVisible.set(false);
    if (this.auth.isLoggedIn()) {
      this.auth.logout();
    }
  }

  private clearTimers(): void {
    if (this.warnTimer) { clearTimeout(this.warnTimer); this.warnTimer = null; }
    if (this.logoutTimer) { clearTimeout(this.logoutTimer); this.logoutTimer = null; }
    if (this.countdownInterval) { clearInterval(this.countdownInterval); this.countdownInterval = null; }
  }
}
