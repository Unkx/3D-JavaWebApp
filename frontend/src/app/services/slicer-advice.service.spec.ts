import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SlicerAdviceService, SlicerAdviceResponse } from './slicer-advice.service';

describe('SlicerAdviceService', () => {
  let service: SlicerAdviceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(SlicerAdviceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getAdvice() POSTs the request body and returns the response', () => {
    const response: SlicerAdviceResponse = {
      recommendedMaterial: 'PETG', materialReason: 'durable', nozzleTemp: 230, bedTemp: 80,
      layerHeight: '0.2', infillPercent: 20, infillPattern: 'grid', supportsNeeded: false,
      supportType: 'none', printSpeed: 'normal', warnings: [], tips: [], aiGenerated: true
    };
    let result: SlicerAdviceResponse | undefined;
    service.getAdvice({ description: 'A vase', material: 'PETG', size: 'large', quality: 'ultra' })
      .subscribe(r => (result = r));

    const req = httpMock.expectOne('/api/ai/slicer-advice');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ description: 'A vase', material: 'PETG', size: 'large', quality: 'ultra' });
    req.flush(response);

    expect(result).toEqual(response);
  });

  it('propagates HTTP errors', () => {
    let error: unknown;
    service.getAdvice({ description: 'x' }).subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/ai/slicer-advice').flush('fail', { status: 500, statusText: 'Server Error' });
    expect((error as { status: number }).status).toBe(500);
  });
});
