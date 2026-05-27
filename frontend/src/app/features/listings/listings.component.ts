import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { ListingService, Listing } from '../../services/listing.service';

@Component({
  selector: 'app-listings',
  imports: [RouterLink, SlicePipe],
  templateUrl: './listings.component.html',
  styleUrl: './listings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListingsComponent implements OnInit {
  private listingService = inject(ListingService);

  listings = signal<Listing[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.listingService.getListings().subscribe({
      next: (data) => {
        this.listings.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Nie udało się załadować zleceń. Sprawdź czy backend jest uruchomiony.');
        this.loading.set(false);
      }
    });
  }

  statusLabel(status: string | undefined): string {
    const map: Record<string, string> = {
      OPEN: 'Otwarte',
      CLOSED: 'Zamknięte',
      AWARDED: 'Przyznane'
    };
    return map[status ?? ''] ?? status ?? '';
  }
}
