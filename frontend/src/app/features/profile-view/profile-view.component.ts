import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { UserService, UserPublicProfile } from '../../services/user.service';
import { RatingService, UserRatings } from '../../services/rating.service';
import { ListingService, Listing, PageResponse } from '../../services/listing.service';
import { IconComponent } from '../../components/icon.component';

type ProfileTab = 'listings' | 'reviews';

@Component({
  selector: 'app-profile-view',
  imports: [RouterLink, DecimalPipe, IconComponent],
  templateUrl: './profile-view.component.html',
  styleUrl: './profile-view.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProfileViewComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private userService = inject(UserService);
  private ratingService = inject(RatingService);
  private listingService = inject(ListingService);

  userId = this.route.snapshot.paramMap.get('id')!;

  profile = signal<UserPublicProfile | null>(null);
  loading = signal(true);
  notFound = signal(false);

  ratings = signal<UserRatings | null>(null);
  ratingsLoading = signal(false);

  listings = signal<PageResponse<Listing> | null>(null);
  listingsLoading = signal(false);

  activeTab = signal<ProfileTab>('listings');

  ngOnInit(): void {
    this.loadProfile();
  }

  private loadProfile(): void {
    this.loading.set(true);
    this.userService.getPublicProfile(this.userId).subscribe({
      next: p => {
        this.profile.set(p);
        this.loading.set(false);
        this.loadRatings();
        this.loadListings();
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.notFound.set(err.status === 404);
      }
    });
  }

  private loadRatings(): void {
    this.ratingsLoading.set(true);
    this.ratingService.getUserRatings(this.userId).subscribe({
      next: r => { this.ratings.set(r); this.ratingsLoading.set(false); },
      error: () => this.ratingsLoading.set(false)
    });
  }

  private loadListings(): void {
    this.listingsLoading.set(true);
    this.listingService.getListingsByUser(this.userId).subscribe({
      next: l => { this.listings.set(l); this.listingsLoading.set(false); },
      error: () => this.listingsLoading.set(false)
    });
  }

  setTab(tab: ProfileTab): void {
    this.activeTab.set(tab);
  }

  avatarUrl(p: UserPublicProfile): string | null {
    if (p.hasAvatarData) return this.userService.avatarUrl(p.id);
    return p.avatarUrl;
  }

  avatarChar(p: UserPublicProfile): string {
    return p.displayName.charAt(0).toUpperCase();
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long', day: 'numeric' });
  }

  formatLastLogin(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long', day: 'numeric' });
  }
}
