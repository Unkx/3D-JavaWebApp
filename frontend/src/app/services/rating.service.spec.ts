import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RatingService } from './rating.service';

describe('RatingService', () => {
  let service: RatingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RatingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createRating() POSTs stars and comment to the offer ratings endpoint', () => {
    service.createRating('offer-1', 5, 'Great!').subscribe();
    const req = httpMock.expectOne('/api/offers/offer-1/ratings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ stars: 5, comment: 'Great!' });
    req.flush({});
  });

  it('createRating() omits comment when not provided', () => {
    service.createRating('offer-1', 4).subscribe();
    const req = httpMock.expectOne('/api/offers/offer-1/ratings');
    expect(req.request.body).toEqual({ stars: 4, comment: undefined });
    req.flush({});
  });

  it('getOfferRatings() GETs ratings scoped to the offer', () => {
    service.getOfferRatings('offer-1').subscribe();
    const req = httpMock.expectOne('/api/offers/offer-1/ratings');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getUserRatings() GETs the paged summary for a user', () => {
    service.getUserRatings('user-1').subscribe();
    const req = httpMock.expectOne('/api/users/user-1/ratings?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush({ summary: { averageStars: null, count: 0 }, ratings: { content: [] } });
  });
});
