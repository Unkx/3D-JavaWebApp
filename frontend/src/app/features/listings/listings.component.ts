import { Component, ChangeDetectionStrategy, signal, inject, OnInit, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, takeUntil } from 'rxjs/operators';
import { ListingService, Listing } from '../../services/listing.service';

@Component({
  selector: 'app-listings',
  imports: [RouterLink, SlicePipe],
  templateUrl: './listings.component.html',
  styleUrl: './listings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListingsComponent implements OnInit, OnDestroy {
  private listingService = inject(ListingService);
  private readonly destroy$ = new Subject<void>();
  private readonly search$ = new Subject<string>();

  private static readonly PAGE_SIZE = 12;

  listings = signal<Listing[]>([]);
  loading = signal(true);
  loadingMore = signal(false);
  error = signal<string | null>(null);
  total = signal(0);
  hasMore = signal(false);
  searchQuery = signal('');
  private nextPage = 0;

  ngOnInit(): void {
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.nextPage = 0;
      this.loadPage(true);
    });
    this.loadPage(true);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearch(value: string): void {
    this.searchQuery.set(value);
    this.search$.next(value);
  }

  loadMore(): void {
    if (this.loadingMore() || !this.hasMore()) return;
    this.loadPage(false);
  }

  private loadPage(initial: boolean): void {
    if (initial) { this.loading.set(true); } else { this.loadingMore.set(true); }

    this.listingService.getListings(this.nextPage, ListingsComponent.PAGE_SIZE, this.searchQuery()).subscribe({
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
