import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ListingService, Listing } from '../../services/listing.service';

interface ListingWithOffers extends Listing {
  offersCount?: number;
}

@Component({
  selector: 'app-my-orders',
  imports: [RouterLink, SlicePipe],
  templateUrl: './my-orders.component.html',
  styleUrl: './my-orders.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyOrdersComponent implements OnInit {
  private listingService = inject(ListingService);
  private http           = inject(HttpClient);

  listings     = signal<ListingWithOffers[]>([]);
  loading      = signal(true);
  error        = signal<string | null>(null);
  deletingId   = signal<string | null>(null);
  closingId    = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.listingService.getMyListings().subscribe({
      next:  data => { this.listings.set(data); this.loading.set(false); },
      error: ()   => { this.error.set('Nie udało się załadować zleceń.'); this.loading.set(false); }
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
      next:  () => { this.listings.update(list => list.filter(l => l.id !== id)); this.deletingId.set(null); },
      error: () => this.deletingId.set(null)
    });
  }

  statusLabel(status: string | undefined): string {
    const map: Record<string, string> = { OPEN: 'Otwarte', CLOSED: 'Zamknięte', AWARDED: 'Przyznane' };
    return map[status ?? ''] ?? (status ?? '');
  }
}
