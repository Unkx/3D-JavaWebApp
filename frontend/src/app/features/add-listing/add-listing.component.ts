import { Component, ChangeDetectionStrategy, signal, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ListingService } from '../../services/listing.service';
import { PriceEstimateService, PriceEstimateResponse } from '../../services/price-estimate.service';
import { IconComponent, IconName } from '../../components/icon.component';

@Component({
  selector: 'app-add-listing',
  imports: [ReactiveFormsModule, RouterLink, IconComponent],
  templateUrl: './add-listing.component.html',
  styleUrl: './add-listing.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AddListingComponent {
  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  private readonly priceEstimateService = inject(PriceEstimateService);
  private readonly router = inject(Router);

  readonly materials = ['PLA', 'PETG', 'ABS', 'ASA', 'RESIN', 'TPU', 'Inny'];
  private readonly allowedExtensions = new Set(['stl', 'obj', 'jpg', 'jpeg', 'png']);

  loading = signal(false);
  success = signal(false);
  serverError = signal<string | null>(null);
  selectedFiles = signal<File[]>([]);
  isDragging = signal(false);

  estimatorSize = signal<'small' | 'medium' | 'large'>('medium');
  estimatorQuality = signal<'fast' | 'normal' | 'ultra'>('normal');

  form = this.fb.group({
    title: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    requiredMaterial: ['PLA', [Validators.required]],
    maxBudget: [null as number | null, [Validators.min(1)]],
    stlFileUrl: ['', [Validators.pattern('^https?://.+')]]
  });

  get titleCtrl() { return this.form.get('title')!; }
  get descriptionCtrl() { return this.form.get('description')!; }
  get materialCtrl() { return this.form.get('requiredMaterial')!; }
  get budgetCtrl() { return this.form.get('maxBudget')!; }
  get stlUrlCtrl() { return this.form.get('stlFileUrl')!; }

  priceEstimate = signal<PriceEstimateResponse | null>(null);
  priceEstimateLoading = signal(false);
  priceEstimateError = signal<string | null>(null);

  estimatePrice(): void {
    const description = this.descriptionCtrl.value?.trim();
    if (!description) return;
    this.priceEstimateLoading.set(true);
    this.priceEstimateError.set(null);
    this.priceEstimateService.getEstimate({
      description,
      material: this.materialCtrl.value ?? undefined,
      size: this.estimatorSize(),
      quality: this.estimatorQuality()
    }).subscribe({
      next: (resp) => {
        this.priceEstimate.set(resp);
        this.priceEstimateLoading.set(false);
      },
      error: () => {
        this.priceEstimateError.set('Nie udało się uzyskać wyceny. Spróbuj ponownie.');
        this.priceEstimateLoading.set(false);
      }
    });
  }

  // --- file upload ---

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) this.addFiles(Array.from(input.files));
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    if (!(event.currentTarget as Element).contains(event.relatedTarget as Node)) {
      this.isDragging.set(false);
    }
  }

  onDropFiles(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
    const files = event.dataTransfer?.files;
    if (files) this.addFiles(Array.from(files));
  }

  private addFiles(incoming: File[]): void {
    const valid = incoming.filter(f => {
      const ext = f.name.split('.').pop()?.toLowerCase() ?? '';
      return this.allowedExtensions.has(ext);
    });
    this.selectedFiles.update(prev => {
      const existingNames = new Set(prev.map(f => f.name));
      return [...prev, ...valid.filter(f => !existingNames.has(f.name))];
    });
  }

  removeFile(file: File): void {
    this.selectedFiles.update(prev => prev.filter(f => f !== file));
  }

  fileIcon(file: File): IconName {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (['jpg', 'jpeg', 'png'].includes(ext)) return 'image';
    if (ext === 'obj') return 'package';
    return 'file';
  }

  formatSize(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  // --- submit ---

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.serverError.set(null);

    const value = this.form.getRawValue();
    this.listingService.createListing({
      title: value.title!,
      description: value.description!,
      requiredMaterial: value.requiredMaterial!,
      ...(value.maxBudget ? { maxBudget: value.maxBudget } : {}),
      ...(value.stlFileUrl ? { stlFileUrl: value.stlFileUrl } : {}),
      estimatorSize: this.estimatorSize(),
      estimatorQuality: this.estimatorQuality()
    }).subscribe({
      next: (listing) => {
        const files = this.selectedFiles();
        if (files.length > 0 && listing.id) {
          this.listingService.uploadStlFiles(listing.id, files).subscribe({
            next: () => this.finishSuccess(),
            error: () => this.finishSuccess()   // listing was created — still navigate
          });
        } else {
          this.finishSuccess();
        }
      },
      error: () => {
        this.loading.set(false);
        this.serverError.set('Nie udało się dodać zlecenia. Sprawdź połączenie i spróbuj ponownie.');
      }
    });
  }

  private finishSuccess(): void {
    this.loading.set(false);
    this.success.set(true);
    setTimeout(() => this.router.navigate(['/zlecenia']), 2000);
  }
}
