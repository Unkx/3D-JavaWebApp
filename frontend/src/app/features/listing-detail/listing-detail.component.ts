import { Component, ChangeDetectionStrategy, signal, inject, OnInit, computed } from '@angular/core';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ListingService, Listing, StlFile } from '../../services/listing.service';
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
  private router = inject(Router);
  private listingService = inject(ListingService);
  private offerService = inject(OfferService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);

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
  deletingListing = signal(false);
  downloading = signal(false);
  currentUser = this.authService.currentUser;

  // Multiple STL files
  stlFiles = signal<StlFile[]>([]);
  selectedFileId = signal<string | null>(null);
  deletingFileId = signal<string | null>(null);

  selectedFile = computed(() =>
    this.stlFiles().find(f => f.id === this.selectedFileId()) ?? null
  );

  selectedFileUrl = computed(() => {
    const id = this.selectedFileId();
    const listing = this.listing();
    if (!id || !listing?.id) return null;
    return `/api/listings/${listing.id}/stl-files/${id}?v=${this.viewerVersion()}`;
  });

  zipDownloadUrl = computed(() => {
    const listing = this.listing();
    if (!listing?.id) return null;
    return `/api/listings/${listing.id}/download-zip`;
  });

  canUploadFile = computed(() => {
    const user = this.currentUser();
    const listing = this.listing();
    if (!user || !listing) return false;
    return user.userId === listing.user?.id || user.role === 'ADMIN';
  });

  // Owner or admin may delete the listing.
  canManage = this.canUploadFile;
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

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
    this.loadStlFiles(id);
  }

  private loadStlFiles(id: string): void {
    this.listingService.getStlFiles(id).subscribe({
      next: files => {
        this.stlFiles.set(files);
        // Keep current selection if still present, otherwise pick the first.
        const current = this.selectedFileId();
        if (!current || !files.some(f => f.id === current)) {
          this.selectedFileId.set(files.length > 0 ? files[0].id : null);
        }
      },
      error: () => this.stlFiles.set([])
    });
  }

  selectFile(fileId: string): void {
    if (this.selectedFileId() === fileId) return;
    this.selectedFileId.set(fileId);
    // Recreate the viewer so it re-fetches the newly selected model.
    this.viewerVisible.set(false);
    setTimeout(() => this.viewerVisible.set(true), 0);
  }

  deleteFile(fileId: string, event: Event): void {
    event.stopPropagation();
    if (!confirm('Usunąć ten plik?')) return;
    const listingId = this.listing()?.id;
    if (!listingId) return;

    this.deletingFileId.set(fileId);
    this.listingService.deleteStlFile(listingId, fileId).subscribe({
      next: () => {
        this.deletingFileId.set(null);
        if (this.selectedFileId() === fileId) {
          this.selectedFileId.set(null);
        }
        this.loadStlFiles(listingId);
      },
      error: () => this.deletingFileId.set(null)
    });
  }

  fileSizeLabel(bytes: number | null): string {
    if (!bytes) return '';
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
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
    // Reload the file list; viewer will show the (still or newly) selected file.
    this.viewerVersion.update(v => v + 1);
    this.loadStlFiles(current.id);
  }

  deleteListing(): void {
    const listing = this.listing();
    if (!listing?.id) return;
    const asAdmin = this.isAdmin() && this.currentUser()?.userId !== listing.user?.id;
    const msg = asAdmin
      ? 'Usunąć to zlecenie jako administrator? Tej operacji nie można cofnąć.'
      : 'Czy na pewno chcesz usunąć to zlecenie? Tej operacji nie można cofnąć.';
    if (!confirm(msg)) return;

    this.deletingListing.set(true);
    this.listingService.deleteListing(listing.id).subscribe({
      next: () => this.router.navigate(['/zlecenia']),
      error: () => {
        this.deletingListing.set(false);
        this.listingError.set('Nie udało się usunąć zlecenia.');
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

  downloadZip(): void {
    const url = this.zipDownloadUrl();
    if (!url || this.downloading()) return;
    this.downloading.set(true);
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const anchor = document.createElement('a');
        anchor.href = URL.createObjectURL(blob);
        anchor.download = (this.listing()?.title ?? 'listing') + '.zip';
        anchor.click();
        URL.revokeObjectURL(anchor.href);
        this.downloading.set(false);
      },
      error: () => this.downloading.set(false)
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
