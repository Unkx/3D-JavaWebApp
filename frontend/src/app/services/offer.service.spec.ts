import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { OfferService, Offer } from './offer.service';

describe('OfferService', () => {
  let service: OfferService;
  let httpMock: HttpTestingController;

  const offer: Offer = {
    id: 'o1', price: 100, printingTimeHours: 2, filamentGrams: 50, printerModel: 'Ender 3',
    listing: { id: 'l1' }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(OfferService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getOffersForListing() GETs offers scoped to the listing', () => {
    service.getOffersForListing('l1').subscribe();
    const req = httpMock.expectOne('/api/offers/listing/l1');
    expect(req.request.method).toBe('GET');
    req.flush([offer]);
  });

  it('createOffer() POSTs a payload derived from the offer, using listing.id', () => {
    service.createOffer(offer).subscribe();
    const req = httpMock.expectOne('/api/offers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      listingId: 'l1',
      price: 100,
      printingTimeHours: 2,
      filamentGrams: 50,
      printerModel: 'Ender 3'
    });
    req.flush(offer);
  });

  it('selectOffer() PUTs the receiver paczkomat', () => {
    service.selectOffer('o1', 'KRA01').subscribe();
    const req = httpMock.expectOne('/api/offers/o1/select');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ receiverPaczkomat: 'KRA01' });
    req.flush(offer);
  });

  it('getMyOffers() GETs the current user offers', () => {
    service.getMyOffers().subscribe();
    const req = httpMock.expectOne('/api/offers/my');
    expect(req.request.method).toBe('GET');
    req.flush([offer]);
  });

  it('updateOfferStatus() PUTs the new status', () => {
    service.updateOfferStatus('o1', 'REJECTED').subscribe();
    const req = httpMock.expectOne('/api/offers/o1/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'REJECTED' });
    req.flush(offer);
  });

  it('updateTracking() PUTs carrier and tracking number', () => {
    service.updateTracking('o1', 'DHL', 'TRACK123').subscribe();
    const req = httpMock.expectOne('/api/offers/o1/tracking');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ carrierName: 'DHL', trackingNumber: 'TRACK123' });
    req.flush({});
  });

  it('getTracking() GETs the tracking info', () => {
    service.getTracking('o1').subscribe();
    const req = httpMock.expectOne('/api/offers/o1/tracking');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getPayment() GETs the payment info', () => {
    service.getPayment('o1').subscribe();
    const req = httpMock.expectOne('/api/offers/o1/payment');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getFeeBreakdown() builds the query string with price only', () => {
    service.getFeeBreakdown(150).subscribe();
    const req = httpMock.expectOne('/api/offers/fee-breakdown?price=150');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getFeeBreakdown() appends estimatorSize when provided', () => {
    service.getFeeBreakdown(150, 'large').subscribe();
    const req = httpMock.expectOne('/api/offers/fee-breakdown?price=150&estimatorSize=large');
    req.flush({});
  });

  it('getShipment() GETs the shipment for an offer', () => {
    service.getShipment('o1').subscribe();
    const req = httpMock.expectOne('/api/shipments/offer/o1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('createShipment() POSTs the sender paczkomat', () => {
    service.createShipment('o1', 'WAW01').subscribe();
    const req = httpMock.expectOne('/api/shipments/offer/o1');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ senderPaczkomat: 'WAW01' });
    req.flush({});
  });

  it('advanceShipment() PUTs an empty body', () => {
    service.advanceShipment('s1').subscribe();
    const req = httpMock.expectOne('/api/shipments/s1/advance');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({});
    req.flush({});
  });

  it('propagates HTTP errors', () => {
    let error: unknown;
    service.getMyOffers().subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/offers/my').flush('fail', { status: 500, statusText: 'Server Error' });
    expect((error as { status: number }).status).toBe(500);
  });
});
