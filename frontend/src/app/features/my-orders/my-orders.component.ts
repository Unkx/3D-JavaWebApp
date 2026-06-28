import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  tablerInbox, tablerMail, tablerCurrencyZloty, tablerLock, tablerCircleCheck,
  tablerPackage, tablerMapPin, tablerFileText, tablerTruck
} from '@ng-icons/tabler-icons';
import { ListingService, Listing } from '../../services/listing.service';
import { OfferService, Offer, OrderTracking, Payment, Shipment } from '../../services/offer.service';
import { ConversationService } from '../../services/conversation.service';
import { PaczkomatPickerComponent } from '../../components/paczkomat-picker.component';

interface ListingWithOffers extends Listing {
  offersCount?: number;
}

@Component({
  selector: 'app-my-orders',
  imports: [RouterLink, SlicePipe, FormsModule, NgIcon, PaczkomatPickerComponent],
  providers: [provideIcons({
    tablerInbox, tablerMail, tablerCurrencyZloty, tablerLock, tablerCircleCheck,
    tablerPackage, tablerMapPin, tablerFileText, tablerTruck
  })],
  templateUrl: './my-orders.component.html',
  styleUrl: './my-orders.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyOrdersComponent implements OnInit {
  private listingService = inject(ListingService);
  private offerService = inject(OfferService);
  private conversationService = inject(ConversationService);
  private router = inject(Router);
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

  shipmentData = signal<Record<string, Shipment>>({});
  paymentData = signal<Record<string, Payment>>({});
  creatingShipmentId = signal<string | null>(null);
  advancingShipmentId = signal<string | null>(null);
  shipmentPaczkomat = signal('');

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
        data.filter(o => ['SELECTED', 'PRINTING', 'SHIPPED', 'DELIVERED'].includes(o.status ?? ''))
            .forEach(o => {
              this.loadTracking(o.id!);
              this.loadShipment(o.id!);
              this.loadPayment(o.id!);
            });
      },
      error: () => {}
    });
  }

  private loadShipment(offerId: string): void {
    this.offerService.getShipment(offerId).subscribe({
      next: s => this.shipmentData.update(d => ({ ...d, [offerId]: s })),
      error: () => {}
    });
  }

  private loadPayment(offerId: string): void {
    this.offerService.getPayment(offerId).subscribe({
      next: p => this.paymentData.update(d => ({ ...d, [offerId]: p })),
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

  openMessage(offer: Offer): void {
    const listingId = offer.listing?.id;
    if (!listingId) return;
    const otherUserId = offer.user?.id;
    this.conversationService.createOrGet(listingId, otherUserId).subscribe({
      next: conv => this.router.navigate(['/wiadomosci'], { queryParams: { conv: conv.id } }),
      error: () => {}
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

  createShipment(offerId: string): void {
    this.creatingShipmentId.set(offerId);
    this.offerService.createShipment(offerId, this.shipmentPaczkomat()).subscribe({
      next: s => {
        this.shipmentData.update(d => ({ ...d, [offerId]: s }));
        this.creatingShipmentId.set(null);
      },
      error: () => this.creatingShipmentId.set(null)
    });
  }

  advanceShipment(offerId: string): void {
    const shipment = this.shipmentData()[offerId];
    if (!shipment) return;
    this.advancingShipmentId.set(offerId);
    this.offerService.advanceShipment(shipment.id).subscribe({
      next: s => {
        this.shipmentData.update(d => ({ ...d, [offerId]: s }));
        this.advancingShipmentId.set(null);
        this.loadMyOffers();
      },
      error: () => this.advancingShipmentId.set(null)
    });
  }

  shipmentStatusLabel(status: string): string {
    const map: Record<string, string> = {
      LABEL_CREATED: 'Etykieta utworzona',
      DISPATCHED: 'Nadano',
      IN_TRANSIT: 'W drodze',
      READY_TO_PICKUP: 'Czeka w paczkomacie',
      DELIVERED: 'Odebrano'
    };
    return map[status] ?? status;
  }
}
