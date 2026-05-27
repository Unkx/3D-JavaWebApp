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
  userId?: string;
  status?: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class OfferService {
  private http = inject(HttpClient);
  private apiUrl = '/api/offers';

  getOffersForListing(listingId: string): Observable<Offer[]> {
    return this.http.get<Offer[]>(`${this.apiUrl}/listing/${listingId}`);
  }

  createOffer(offer: Offer): Observable<Offer> {
    return this.http.post<Offer>(this.apiUrl, offer);
  }

  selectOffer(offerId: string): Observable<Offer> {
    return this.http.put<Offer>(`${this.apiUrl}/${offerId}/select`, {});
  }
}
