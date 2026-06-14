import { Component, ChangeDetectionStrategy, signal, inject, OnInit, computed } from '@angular/core';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ListingService, Listing, StlFile, UpdateListingPayload } from '../../services/listing.service';
import { OfferService, Offer } from '../../services/offer.service';
import { AuthService } from '../../services/auth.service';
import { StlViewerComponent } from '../../components/stl-viewer.component';
import { StlFileUploadComponent } from '../../components/stl-file-upload.component';

type EstimatorSize    = 'small' | 'medium' | 'large';
type EstimatorQuality = 'fast'  | 'normal' | 'ultra';

@Component({
  selector: 'app-listing-detail',
  imports: [RouterLink, ReactiveFormsModule, SlicePipe, StlViewerComponent, StlFileUploadComponent],
  templateUrl: './listing-detail.component.html',
  styleUrl: './listing-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListingDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly listingService = inject(ListingService);
  private readonly offerService = inject(OfferService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
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
  private draggedId: string | null = null;

  // Inline edit
  editMode = signal(false);
  editSaving = signal(false);
  editError = signal<string | null>(null);
  editSize = signal<EstimatorSize>('medium');
  editQuality = signal<EstimatorQuality>('normal');

  readonly editMaterials = ['PLA', 'PETG', 'ABS', 'ASA', 'RESIN', 'TPU', 'Inny'];

  editForm = this.fb.group({
    description: ['', [Validators.maxLength(2000)]],
    requiredMaterial: ['PLA', [Validators.required]],
    maxBudget: [null as number | null, [Validators.min(1)]]
  });

  // Cost estimator widget (on the detail page — for visitors)
  estimatorSize = signal<EstimatorSize>('medium');
  estimatorQuality = signal<EstimatorQuality>('normal');

  printEstimate = computed(() => {
    const material = (this.listing()?.requiredMaterial ?? '').toUpperCase();
    const grams = { small: 50, medium: 150, large: 350 }[this.estimatorSize()];
    const hours = { small: 1.5, medium: 4, large: 10 }[this.estimatorSize()];
    const qualityTimeScale = { fast: 0.7, normal: 1, ultra: 1.4 }[this.estimatorQuality()];
    const qualityFilamentScale = { fast: 1, normal: 1, ultra: 1.1 }[this.estimatorQuality()];
    const pricePerGram: Record<string, number> = {
      PLA: 0.05, PETG: 0.07, ABS: 0.06, ASA: 0.08, TPU: 0.12, RESIN: 0.15
    };
    const pgram = pricePerGram[material] ?? 0.06;
    const base = grams * qualityFilamentScale * pgram + hours * qualityTimeScale * 5;
    return { low: Math.max(1, Math.round(base * 0.85)), high: Math.round(base * 1.3) };
  });

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

  canManage = this.canUploadFile;
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');
  isOwner = computed(() => this.currentUser()?.userId === this.listing()?.user?.id);

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
        if (this.selectedFileId() === fileId) this.selectedFileId.set(null);
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
        if (data.estimatorSize)    this.estimatorSize.set(data.estimatorSize as EstimatorSize);
        if (data.estimatorQuality) this.estimatorQuality.set(data.estimatorQuality as EstimatorQuality);
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

  // --- Inline edit ---

  openEdit(): void {
    const l = this.listing();
    if (!l) return;
    this.editForm.patchValue({
      description: l.description ?? '',
      requiredMaterial: l.requiredMaterial ?? 'PLA',
      maxBudget: l.maxBudget ?? null
    });
    this.editSize.set((l.estimatorSize as EstimatorSize) ?? 'medium');
    this.editQuality.set((l.estimatorQuality as EstimatorQuality) ?? 'normal');
    this.editError.set(null);
    this.editMode.set(true);
  }

  cancelEdit(): void {
    this.editMode.set(false);
    this.editError.set(null);
  }

  saveEdit(): void {
    if (this.editForm.invalid) { this.editForm.markAllAsTouched(); return; }
    const id = this.listing()?.id;
    if (!id) return;
    this.editSaving.set(true);
    this.editError.set(null);
    const v = this.editForm.getRawValue();
    const payload: UpdateListingPayload = {
      description: v.description ?? '',
      requiredMaterial: v.requiredMaterial!,
      maxBudget: v.maxBudget ?? null,
      estimatorSize: this.editSize(),
      estimatorQuality: this.editQuality()
    };
    this.listingService.patchListing(id, payload).subscribe({
      next: (updated) => {
        this.listing.set(updated);
        this.editSaving.set(false);
        this.editMode.set(false);
      },
      error: () => {
        this.editSaving.set(false);
        this.editError.set('Nie udało się zapisać zmian. Spróbuj ponownie.');
      }
    });
  }

  // --- Drag reorder ---

  onDragStart(fileId: string): void { this.draggedId = fileId; }

  onDragOver(event: DragEvent): void { event.preventDefault(); }

  onDrop(event: DragEvent, targetId: string): void {
    event.preventDefault();
    const sourceId = this.draggedId;
    this.draggedId = null;
    if (!sourceId || sourceId === targetId) return;
    const files = [...this.stlFiles()];
    const fromIdx = files.findIndex(f => f.id === sourceId);
    const toIdx   = files.findIndex(f => f.id === targetId);
    if (fromIdx === -1 || toIdx === -1) return;
    const [moved] = files.splice(fromIdx, 1);
    files.splice(toIdx, 0, moved);
    this.stlFiles.set(files);
    const listingId = this.listing()?.id;
    if (listingId) this.listingService.reorderStlFiles(listingId, files.map(f => f.id)).subscribe();
  }

  onDragEnd(): void { this.draggedId = null; }

  // --- Download ---

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

  // --- Offer helpers ---

  private loadOffers(id: string): void {
    this.offerService.getOffersForListing(id).subscribe({
      next: (data) => { this.offers.set(data); this.offersLoading.set(false); },
      error: () => this.offersLoading.set(false)
    });
  }

  submitOffer(): void {
    if (this.offerForm.invalid) { this.offerForm.markAllAsTouched(); return; }
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

  estimateCost(offer: Offer): { low: number; high: number } {
    const base = offer.filamentGrams * 0.06 + offer.printingTimeHours * 5;
    return { low: Math.max(1, Math.round(base * 0.8)), high: Math.round(base * 1.35) };
  }

  priceVsEstimate(offer: Offer): 'cheap' | 'fair' | 'expensive' {
    const { low, high } = this.estimateCost(offer);
    if (offer.price < low) return 'cheap';
    if (offer.price > high) return 'expensive';
    return 'fair';
  }

  sizeLabel(v: string | undefined): string {
    return ({ small: 'Mały', medium: 'Średni', large: 'Duży' } as Record<string, string>)[v ?? ''] ?? v ?? '';
  }

  qualityLabel(v: string | undefined): string {
    return ({ fast: 'Szybki', normal: 'Normal', ultra: 'Ultra' } as Record<string, string>)[v ?? ''] ?? v ?? '';
  }

  statusLabel(status: string | undefined): string {
    const map: Record<string, string> = {
      OPEN: 'Otwarte', CLOSED: 'Zamknięte', AWARDED: 'Przyznane',
      PENDING: 'Oczekuje', SELECTED: 'Wybrana', REJECTED: 'Odrzucona', PAID: 'Opłacona'
    };
    return map[status ?? ''] ?? status ?? '';
  }
}
