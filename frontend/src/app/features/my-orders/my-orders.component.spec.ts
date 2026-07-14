import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MyOrdersComponent } from './my-orders.component';
import { ListingService } from '../../services/listing.service';
import { OfferService } from '../../services/offer.service';
import { ConversationService } from '../../services/conversation.service';
import { AuthService } from '../../services/auth.service';

describe('MyOrdersComponent', () => {
  let httpMock: HttpTestingController;
  let listingStub: { getMyListings: ReturnType<typeof vi.fn> };
  let offerStub: { getMyOffers: ReturnType<typeof vi.fn>; getOffersForListing: ReturnType<typeof vi.fn> };
  let conversationStub: { createOrGet: ReturnType<typeof vi.fn> };
  let authStub: { currentUser: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    listingStub = { getMyListings: vi.fn() };
    offerStub = { getMyOffers: vi.fn(), getOffersForListing: vi.fn() };
    conversationStub = { createOrGet: vi.fn() };
    authStub = { currentUser: vi.fn().mockReturnValue({ userId: 'u1' }) };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ListingService, useValue: listingStub },
        { provide: OfferService, useValue: offerStub },
        { provide: ConversationService, useValue: conversationStub },
        { provide: AuthService, useValue: authStub },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } }
      ]
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('rateOrder() submits stars and comment, then marks the offer as rated', () => {
    const fixture = TestBed.createComponent(MyOrdersComponent);
    const component = fixture.componentInstance;
    component.myOffers.set([
      { id: 'o1', price: 100, printingTimeHours: 2, filamentGrams: 50, printerModel: 'Ender 3', status: 'DELIVERED', listing: { id: 'l1' } }
    ]);
    component.ratingStars.set(5);
    component.ratingComment.set('Great work!');

    component.submitRating('o1');
    const req = httpMock.expectOne('/api/offers/o1/ratings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ stars: 5, comment: 'Great work!' });
    req.flush({ id: 'r1', offerId: 'o1', stars: 5, comment: 'Great work!', raterId: 'u1', ratedUserId: 'u2', moderationStatus: 'VISIBLE', createdAt: '2026-07-14T00:00:00' });

    expect(component.ratedOfferIds().has('o1')).toBe(true);
  });
});
