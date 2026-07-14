import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly SESSION_KEY = 'druk3d-session-id';
  private http = inject(HttpClient);

  trackPageView(path: string): void {
    this.http.post('/api/analytics/pageview', {
      path,
      sessionId: this.sessionId(),
      referrer: document.referrer || null,
    }).subscribe({ next: () => {}, error: () => {} });
  }

  private sessionId(): string {
    let id = sessionStorage.getItem(this.SESSION_KEY);
    if (!id) {
      id = crypto.randomUUID();
      sessionStorage.setItem(this.SESSION_KEY, id);
    }
    return id;
  }
}
