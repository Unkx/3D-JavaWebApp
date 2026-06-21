import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ListingService, Listing } from '../../services/listing.service';
import { OfferService, Offer, OrderTracking } from '../../services/offer.service';
import { AuthService } from '../../services/auth.service';

interface ListingWithOffers extends Listing {
  offersCount?: number;
}

@Component({
  selector: 'app-my-orders',
  imports: [RouterLink, SlicePipe, FormsModule],
  templateUrl: './my-orders.component.html',
  styleUrl: './my-orders.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyOrdersComponent implements OnInit {
  private listingService = inject(ListingService);
  private offerService = inject(OfferService);
  private authService = inject(AuthService);
  private http = inject(HttpClient);

  listings = signal<ListingWithOffers[]>([]);
  myOffers = signal<Offer[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  deletingId = signal<string | null>(null);
  closingId = signal<string | null>(null);

  // Order tracking
  activeTab = signal<'listings' | 'offers'>('listings');
  updatingStatusId = signal<string | null>(null);
  trackingData = signal<Record<string, OrderTracking>>({});
  shippingFormId = signal<string | null>(null);
  carrierName = signal('');
  trackingNumber = signal('');
  sendingTracking = signal(false);

  readonly carriers = ['DPD', 'InPost', 'Poczta Polska', 'Kurier', 'Inne'];

  ngOnInit(): void {
    this.load();
    this.loadMyOffers();
  }

  private load(): void {
    this.loading.set(true);
    this.listingService.getMyListings().subscribe({
      next: data => { this.listings.set(data); this.loading.set(false); },
      error: () => { this.error.set('Nie udało się załadować zleceń.'); this.loading.set(false); }
    });
  }

  private loadMyOffers(): void {
    this.offerService.getMyOffers().subscribe({
      next: data => {
        this.myOffers.set(data);
        data.filter(o => ['PRINTING', 'SHIPPED', 'DELIVERED'].includes(o.status ?? ''))
            .forEach(o => this.loadTracking(o.id!));
      },
      error: () => {}
    });
  }

  private loadTracking(offerId: string): void {
    this.offerService.getTracking(offerId).subscribe({
      next: t => this.trackingData.update(d => ({ ...d, [offerId]: t })),
      error: () => {}
    });
  }

  updateStatus(offerId: string, status: string): void {
    this.updatingStatusId.set(offerId);
    this.offerService.updateOfferStatus(offerId, status).subscribe({
      next: updated => {
        this.myOffers.update(list => list.map(o => o.id === offerId ? { ...o, status: updated.status } : o));
        this.updatingStatusId.set(null);
        if (status === 'PRINTING') this.loadTracking(offerId);
      },
      error: () => this.updatingStatusId.set(null)
    });
  }

  openShippingForm(offerId: string): void {
    this.shippingFormId.set(offerId);
    this.carrierName.set('DPD');
    this.trackingNumber.set('');
  }

  submitTracking(offerId: string): void {
    const carrier = this.carrierName().trim();
    const number = this.trackingNumber().trim();
    if (!carrier || !number) return;
    this.sendingTracking.set(true);
    this.offerService.updateTracking(offerId, carrier, number).subscribe({
      next: t => {
        this.trackingData.update(d => ({ ...d, [offerId]: t }));
        this.myOffers.update(list => list.map(o => o.id === offerId ? { ...o, status: 'SHIPPED' } : o));
        this.shippingFormId.set(null);
        this.sendingTracking.set(false);
      },
      error: () => this.sendingTracking.set(false)
    });
  }

  confirmDelivery(offerId: string): void {
    this.updatingStatusId.set(offerId);
    this.offerService.updateOfferStatus(offerId, 'DELIVERED').subscribe({
      next: updated => {
        this.myOffers.update(list => list.map(o => o.id === offerId ? { ...o, status: updated.status } : o));
        this.updatingStatusId.set(null);
        this.loadTracking(offerId);
      },
      error: () => this.updatingStatusId.set(null)
    });
  }

  closeListing(id: string): void {
    this.closingId.set(id);
    this.http.put<Listing>(`/api/listings/${id}/close`, {}).subscribe({
      next: updated => {
        this.listings.update(list => list.map(l => l.id === id ? { ...l, status: updated.status } : l));
        this.closingId.set(null);
      },
      error: () => this.closingId.set(null)
    });
  }

  deleteListing(id: string): void {
    if (!confirm('Czy na pewno chcesz usunąć to zlecenie?')) return;
    this.deletingId.set(id);
    this.http.delete(`/api/listings/${id}`).subscribe({
      next: () => { this.listings.update(list => list.filter(l => l.id !== id)); this.deletingId.set(null); },
      error: () => this.deletingId.set(null)
    });
  }

  statusLabel(status: string | undefined): string {
    const map: Record<string, string> = {
      OPEN: 'Otwarte', CLOSED: 'Zamknięte', AWARDED: 'Przyznane',
      PENDING: 'Oczekuje', SELECTED: 'Wybrana', REJECTED: 'Odrzucona', PAID: 'Opłacona',
      PRINTING: 'Drukowanie', SHIPPED: 'Wysłano', DELIVERED: 'Dostarczono'
    };
    return map[status ?? ''] ?? (status ?? '');
  }

  offerStatusStep(status: string | undefined): number {
    const steps: Record<string, number> = { SELECTED: 0, PRINTING: 1, SHIPPED: 2, DELIVERED: 3 };
    return steps[status ?? ''] ?? -1;
  }
}
