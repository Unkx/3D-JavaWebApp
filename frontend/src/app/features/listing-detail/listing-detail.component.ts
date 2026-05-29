import { Component, ChangeDetectionStrategy, signal, inject, OnInit, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { ListingService, Listing } from '../../services/listing.service';
import { OfferService, Offer } from '../../services/offer.service';
import { AuthService } from '../../services/auth.service';
import { StlViewerComponent } from '../../components/stl-viewer.component';
import { StlFileUploadComponent } from '../../components/stl-file-upload.component';

@Component({
  selector: 'app-listing-detail',
  imports: [RouterLink, ReactiveFormsModule, SlicePipe, StlViewerComponent, StlFileUploadComponent],
  templateUrl: './listing-detail.component.html',
  styleUrl: './listing-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListingDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private listingService = inject(ListingService);
  private offerService = inject(OfferService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  listing = signal<Listing | null>(null);
  offers = signal<Offer[]>([]);
  listingLoading = signal(true);
  offersLoading = signal(true);
  listingError = signal<string | null>(null);
  submitSuccess = signal(false);
  submitError = signal<string | null>(null);
  submitting = signal(false);
  viewerVersion = signal(0);
  viewerVisible = signal(true);
  currentUser = this.authService.currentUser;

  canUploadFile = computed(() => {
    const user = this.currentUser();
    const listing = this.listing();
    if (!user || !listing) return false;
    return user.userId === listing.user?.id || user.role === 'ADMIN';
  });

  offerForm = this.fb.group({
    price: [null as number | null, [Validators.required, Validators.min(1)]],
    printingTimeHours: [null as number | null, [Validators.required, Validators.min(0.1)]],
    filamentGrams: [null as number | null, [Validators.required, Validators.min(1)]],
    printerModel: ['', [Validators.required, Validators.minLength(2)]]
  });

  get priceCtrl() { return this.offerForm.get('price')!; }
  get timeCtrl() { return this.offerForm.get('printingTimeHours')!; }
  get filamentCtrl() { return this.offerForm.get('filamentGrams')!; }
  get printerCtrl() { return this.offerForm.get('printerModel')!; }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadListing(id);
    this.loadOffers(id);
  }

  private loadListing(id: string): void {
    this.listingService.getListing(id).subscribe({
      next: (data) => {
        this.listing.set(data);
        this.listingLoading.set(false);
      },
      error: () => {
        this.listingError.set('Nie znaleziono zlecenia.');
        this.listingLoading.set(false);
      }
    });
  }

  onFileUploaded(): void {
    const current = this.listing();
    if (!current?.id) return;
    // Reload the listing so stlFileName populates, then destroy + recreate the
    // viewer with a cache-busted URL so it re-fetches the newly uploaded model.
    this.listingService.getListing(current.id).subscribe({
      next: (data) => {
        this.listing.set(data);
        this.viewerVersion.update(v => v + 1);
        this.viewerVisible.set(false);
        setTimeout(() => this.viewerVisible.set(true), 0);
      }
    });
  }

  private loadOffers(id: string): void {
    this.offerService.getOffersForListing(id).subscribe({
      next: (data) => {
        this.offers.set(data);
        this.offersLoading.set(false);
      },
      error: () => this.offersLoading.set(false)
    });
  }

  submitOffer(): void {
    if (this.offerForm.invalid) {
      this.offerForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const value = this.offerForm.getRawValue();
    const offer: Offer = {
      price: value.price!,
      printingTimeHours: value.printingTimeHours!,
      filamentGrams: value.filamentGrams!,
      printerModel: value.printerModel!,
      listing: { id: this.listing()!.id! }
    };

    this.offerService.createOffer(offer).subscribe({
      next: (newOffer) => {
        this.offers.update(list => [...list, newOffer]);
        this.offerForm.reset();
        this.submitting.set(false);
        this.submitSuccess.set(true);
        setTimeout(() => this.submitSuccess.set(false), 4000);
      },
      error: () => {
        this.submitting.set(false);
        this.submitError.set('Nie udało się złożyć oferty. Spróbuj ponownie.');
      }
    });
  }

  statusLabel(status: string | undefined): string {
    const map: Record<string, string> = {
      OPEN: 'Otwarte',
      CLOSED: 'Zamknięte',
      AWARDED: 'Przyznane',
      PENDING: 'Oczekuje',
      SELECTED: 'Wybrana',
      REJECTED: 'Odrzucona',
      PAID: 'Opłacona'
    };
    return map[status ?? ''] ?? status ?? '';
  }
}
