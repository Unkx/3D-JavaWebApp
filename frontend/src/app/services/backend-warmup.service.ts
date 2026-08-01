import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class BackendWarmupService {
  private static readonly SLOW_REQUEST_DELAY_MS = 5000;

  slow = signal(false);
  private pending = 0;
  private timer?: ReturnType<typeof setTimeout>;

  requestStarted(): void {
    this.pending++;
    if (!this.timer) {
      this.timer = setTimeout(() => this.slow.set(true), BackendWarmupService.SLOW_REQUEST_DELAY_MS);
    }
  }

  requestEnded(): void {
    this.pending = Math.max(0, this.pending - 1);
    if (this.pending === 0) {
      clearTimeout(this.timer);
      this.timer = undefined;
      this.slow.set(false);
    }
  }
}
