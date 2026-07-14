import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('trackPageView() POSTs the path with a generated session id', () => {
    service.trackPageView('/zlecenia');
    const req = httpMock.expectOne('/api/analytics/pageview');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.path).toBe('/zlecenia');
    expect(req.request.body.sessionId).toBeTruthy();
    req.flush(null);
  });

  it('trackPageView() reuses the same session id across calls', () => {
    service.trackPageView('/a');
    const first = httpMock.expectOne('/api/analytics/pageview');
    const firstSessionId = first.request.body.sessionId;
    first.flush(null);

    service.trackPageView('/b');
    const second = httpMock.expectOne('/api/analytics/pageview');
    expect(second.request.body.sessionId).toBe(firstSessionId);
    second.flush(null);
  });

  it('trackPageView() swallows request errors silently', () => {
    expect(() => {
      service.trackPageView('/broken');
      const req = httpMock.expectOne('/api/analytics/pageview');
      req.flush('error', { status: 500, statusText: 'Server Error' });
    }).not.toThrow();
  });
});
