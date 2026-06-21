import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Offer {
  id?: string;
  price: number;
  printingTimeHours: number;
  filamentGrams: number;
  printerModel: string;
  listing?: { id: string };
  user?: { id: string; email?: string };
  status?: string;
  createdAt?: string;
}

export interface OrderTracking {
  id: string;
  carrierName: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class OfferService {
  private http = inject(HttpClient);
  private apiUrl = '/api/offers';

  getOffersForListing(listingId: string): Observable<Offer[]> {
    return this.http.get<Offer[]>(`${this.apiUrl}/listing/${listingId}`);
  }

  createOffer(offer: Offer): Observable<Offer> {
    const payload = {
      listingId: offer.listing?.id,
      price: offer.price,
      printingTimeHours: offer.printingTimeHours,
      filamentGrams: offer.filamentGrams,
      printerModel: offer.printerModel
    };
    return this.http.post<Offer>(this.apiUrl, payload);
  }

  selectOffer(offerId: string): Observable<Offer> {
    return this.http.put<Offer>(`${this.apiUrl}/${offerId}/select`, {});
  }

  getMyOffers(): Observable<Offer[]> {
    return this.http.get<Offer[]>(`${this.apiUrl}/my`);
  }

  updateOfferStatus(offerId: string, status: string): Observable<Offer> {
    return this.http.put<Offer>(`${this.apiUrl}/${offerId}/status`, { status });
  }

  updateTracking(offerId: string, carrierName: string, trackingNumber: string): Observable<OrderTracking> {
    return this.http.put<OrderTracking>(`${this.apiUrl}/${offerId}/tracking`, { carrierName, trackingNumber });
  }

  getTracking(offerId: string): Observable<OrderTracking> {
    return this.http.get<OrderTracking>(`${this.apiUrl}/${offerId}/tracking`);
  }
}
