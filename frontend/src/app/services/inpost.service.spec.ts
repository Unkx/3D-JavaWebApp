import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InpostService, InpostPoint } from './inpost.service';

describe('InpostService', () => {
  let service: InpostService;
  let httpMock: HttpTestingController;

  const point: InpostPoint = {
    name: 'KRA01',
    address: { line1: 'Street 1', line2: '' },
    address_details: { city: 'Krakow', street: 'Street', building_number: '1', post_code: '30-000' },
    location: { latitude: 50.0, longitude: 19.9 },
    opening_hours: '24/7',
    location_description: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(InpostService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('searchByLocation() sends the expected query params and unwraps items', () => {
    let result: InpostPoint[] | undefined;
    service.searchByLocation(50.0, 19.9, 5).subscribe(r => (result = r));

    const req = httpMock.expectOne(
      r => r.url === '/inpost-api/points'
        && r.params.get('type') === 'parcel_locker'
        && r.params.get('relative_point') === '50,19.9'
        && r.params.get('per_page') === '5'
        && r.params.get('status') === 'Operating'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ items: [point], total_pages: 1, count: 1 });
    expect(result).toEqual([point]);
  });

  it('searchByLocation() defaults limit to 10', () => {
    service.searchByLocation(1, 2).subscribe();
    const req = httpMock.expectOne(r => r.params.get('per_page') === '10');
    req.flush({ items: [], total_pages: 0, count: 0 });
  });

  it('searchByQuery() sends the name param', () => {
    service.searchByQuery('Krakow Central', 20).subscribe();
    const req = httpMock.expectOne(
      r => r.params.get('name') === 'Krakow Central' && r.params.get('per_page') === '20'
    );
    req.flush({ items: [], total_pages: 0, count: 0 });
  });

  it('searchByCity() sends the address[city] param', () => {
    let result: InpostPoint[] | undefined;
    service.searchByCity('Warsaw').subscribe(r => (result = r));
    const req = httpMock.expectOne(r => r.params.get('address[city]') === 'Warsaw');
    req.flush({ items: [point], total_pages: 1, count: 1 });
    expect(result).toEqual([point]);
  });

  it('propagates errors', () => {
    let error: unknown;
    service.searchByCity('Nowhere').subscribe({ error: (e) => (error = e) });
    httpMock.expectOne(r => r.url === '/inpost-api/points').flush('err', { status: 503, statusText: 'Unavailable' });
    expect((error as { status: number }).status).toBe(503);
  });
});
