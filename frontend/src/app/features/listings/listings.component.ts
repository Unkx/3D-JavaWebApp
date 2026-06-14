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

  private static readonly PAGE_SIZE = 12;

  listings = signal<Listing[]>([]);
  loading = signal(true);          // initial load
  loadingMore = signal(false);     // "load more" in progress
  error = signal<string | null>(null);
  total = signal(0);
  hasMore = signal(false);
  private nextPage = 0;

  ngOnInit(): void {
    this.loadPage(true);
  }

  loadMore(): void {
    if (this.loadingMore() || !this.hasMore()) return;
    this.loadPage(false);
  }

  private loadPage(initial: boolean): void {
    if (initial) { this.loading.set(true); } else { this.loadingMore.set(true); }

    this.listingService.getListings(this.nextPage, ListingsComponent.PAGE_SIZE).subscribe({
      next: (res) => {
        this.listings.update(curr => initial ? res.content : [...curr, ...res.content]);
        this.total.set(res.totalElements);
        this.hasMore.set(!res.last);
        this.nextPage = res.page + 1;
        this.loading.set(false);
        this.loadingMore.set(false);
      },
      error: () => {
        this.error.set('Nie udało się załadować zleceń. Sprawdź czy backend jest uruchomiony.');
        this.loading.set(false);
        this.loadingMore.set(false);
      }
    });
  }

  sizeLabel(v: string | undefined): string {
    return ({ small: 'Mały', medium: 'Średni', large: 'Duży' } as Record<string, string>)[v ?? ''] ?? '';
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
