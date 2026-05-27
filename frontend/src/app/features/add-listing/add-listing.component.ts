import { Component, ChangeDetectionStrategy, signal, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ListingService } from '../../services/listing.service';

@Component({
  selector: 'app-add-listing',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './add-listing.component.html',
  styleUrl: './add-listing.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AddListingComponent {
  private fb = inject(FormBuilder);
  private listingService = inject(ListingService);
  private router = inject(Router);

  readonly materials = ['PLA', 'PETG', 'ABS', 'ASA', 'RESIN', 'TPU', 'Inny'];

  loading = signal(false);
  success = signal(false);
  serverError = signal<string | null>(null);

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
      ...(value.stlFileUrl ? { stlFileUrl: value.stlFileUrl } : {})
    }).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigate(['/zlecenia']), 2000);
      },
      error: () => {
        this.loading.set(false);
        this.serverError.set('Nie udało się dodać zlecenia. Sprawdź połączenie i spróbuj ponownie.');
      }
    });
  }
}
