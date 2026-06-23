import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Offer {
  id?: string;
  price: number;
  printingTimeHours: number;
  filamentGrams: number;
  printerModel: string;
  listing?: { id: string; title?: string };
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

export interface Payment {
  id: string;
  contractorPrice: number;
  platformFeePercent: number;
  platformFee: number;
  shippingPrice: number;
  parcelSize: string;
  totalPrice: number;
  receiverPaczkomat: string;
  status: string;
  paidAt: string | null;
  releasedAt: string | null;
}

export interface Shipment {
  id: string;
  trackingNumber: string;
  labelUrl: string;
  senderPaczkomat: string;
  receiverPaczkomat: string;
  parcelSize: string;
  status: string;
  createdAt: string;
}

export interface FeeBreakdown {
  contractorPrice: number;
  platformFeePercent: number;
  platformFee: number;
  shippingPrice: number;
  parcelSize: string;
  totalPrice: number;
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

  selectOffer(offerId: string, receiverPaczkomat: string): Observable<Offer> {
    return this.http.put<Offer>(`${this.apiUrl}/${offerId}/select`, { receiverPaczkomat });
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

  getPayment(offerId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.apiUrl}/${offerId}/payment`);
  }

  getFeeBreakdown(price: number, estimatorSize?: string): Observable<FeeBreakdown> {
    let url = `${this.apiUrl}/fee-breakdown?price=${price}`;
    if (estimatorSize) url += `&estimatorSize=${estimatorSize}`;
    return this.http.get<FeeBreakdown>(url);
  }

  getShipment(offerId: string): Observable<Shipment> {
    return this.http.get<Shipment>(`/api/shipments/offer/${offerId}`);
  }

  createShipment(offerId: string, senderPaczkomat: string): Observable<Shipment> {
    return this.http.post<Shipment>(`/api/shipments/offer/${offerId}`, { senderPaczkomat });
  }

  advanceShipment(shipmentId: string): Observable<Shipment> {
    return this.http.put<Shipment>(`/api/shipments/${shipmentId}/advance`, {});
  }
}
