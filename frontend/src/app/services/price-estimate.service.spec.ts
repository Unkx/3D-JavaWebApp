import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PriceEstimateService, PriceEstimateResponse } from './price-estimate.service';

describe('PriceEstimateService', () => {
  let service: PriceEstimateService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PriceEstimateService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getEstimate() POSTs the request body and returns the response', () => {
    const response: PriceEstimateResponse = {
      priceLow: 10, priceHigh: 20, reasoning: 'r', assumedWeightGrams: 50,
      assumedPrintHours: 3, warnings: [], aiGenerated: true
    };
    let result: PriceEstimateResponse | undefined;
    service.getEstimate({ description: 'A cool model', material: 'PLA', size: 'medium', quality: 'normal' })
      .subscribe(r => (result = r));

    const req = httpMock.expectOne('/api/ai/price-estimate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ description: 'A cool model', material: 'PLA', size: 'medium', quality: 'normal' });
    req.flush(response);

    expect(result).toEqual(response);
  });

  it('propagates HTTP errors', () => {
    let error: unknown;
    service.getEstimate({ description: 'x' }).subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/ai/price-estimate').flush('fail', { status: 502, statusText: 'Bad Gateway' });
    expect((error as { status: number }).status).toBe(502);
  });
});
