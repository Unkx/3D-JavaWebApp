import { Component, Input, Output, EventEmitter, ViewChild, ElementRef, ChangeDetectionStrategy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ListingService } from '../services/listing.service';

@Component({
  selector: 'app-stl-file-upload',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="upload-container">
      <h3 class="upload-title">Przesłanie pliku STL</h3>

      @if (successMessage()) {
        <div class="alert alert--success" role="alert" aria-live="polite">
          ✅ {{ successMessage() }}
        </div>
      }
      @if (errorMessage()) {
        <div class="alert alert--error" role="alert" aria-live="polite">
          ⚠️ {{ errorMessage() }}
        </div>
      }

      <form (ngSubmit)="onSubmit()" class="upload-form">
        <label class="file-input-wrapper"
             (dragover)="onDragOver($event)"
             (dragleave)="onDragLeave($event)"
             (drop)="onDrop($event)"
             [class.dragover]="isDragging()">

          <input
            #fileInput
            type="file"
            accept=".stl"
            class="file-input"
            (change)="onFileSelected($event)"
            aria-label="Wybierz plik STL"
          />

          <span class="file-label">
            <span class="file-icon">📁</span>
            <span class="file-text">
              @if (selectedFileName()) {
                {{ selectedFileName() }}
              } @else {
                <strong>Kliknij aby wybrać</strong> lub przeciągnij plik STL
              }
            </span>
          </span>
        </label>

        @if (fileSizeWarning()) {
          <p class="warning-text">⚠️ {{ fileSizeWarning() }}</p>
        }

        <button
          type="button"
          class="btn btn--primary"
          (click)="onSubmit()"
          [disabled]="uploading()"
          [attr.aria-busy]="uploading()"
        >
          @if (uploading()) {
            <span class="btn-spinner" aria-hidden="true"></span>
            Przesyłanie...
          } @else {
            Prześlij plik
          }
        </button>
      </form>
    </div>
  `,
  styles: [`
    .upload-container {
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 12px;
      padding: 1.5rem;
      margin: 1.5rem 0;
    }

    .upload-title {
      font-size: 1.125rem;
      font-weight: 700;
      color: #111827;
      margin: 0 0 1rem;
    }

    .alert {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      padding: 0.875rem 1rem;
      border-radius: 8px;
      margin-bottom: 1rem;
      font-size: 0.9rem;
    }

    .alert--success {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #166534;
    }

    .alert--error {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #991b1b;
    }

    .upload-form {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .file-input-wrapper {
      position: relative;
      border: 2px dashed #d1d5db;
      border-radius: 8px;
      padding: 2rem;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s;
      background: #f9fafb;
    }

    .file-input-wrapper:hover {
      border-color: #2563eb;
      background: #eff6ff;
    }

    .file-input-wrapper.dragover {
      border-color: #2563eb;
      background: #dbeafe;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
    }

    .file-input {
      display: none;
    }

    .file-label {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.75rem;
      cursor: pointer;
      user-select: none;
    }

    .file-icon {
      font-size: 2.5rem;
    }

    .file-text {
      color: #6b7280;
      font-size: 0.95rem;
      line-height: 1.5;
    }

    .file-text strong {
      color: #2563eb;
      font-weight: 600;
    }

    .warning-text {
      font-size: 0.875rem;
      color: #d97706;
      margin: 0;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 1.25rem;
      border-radius: 8px;
      font-size: 0.9375rem;
      font-weight: 600;
      font-family: inherit;
      cursor: pointer;
      text-decoration: none;
      border: none;
      transition: background 0.15s;
      justify-content: center;
    }

    .btn--primary {
      background: #2563eb;
      color: #fff;
    }

    .btn--primary:hover:not(:disabled) {
      background: #1d4ed8;
    }

    .btn--primary:disabled {
      opacity: 0.65;
      cursor: not-allowed;
    }

    .btn-spinner {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255, 255, 255, 0.4);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StlFileUploadComponent {
  @Input() listingId!: string;
  @Output() uploaded = new EventEmitter<void>();
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private listingService = inject(ListingService);

  uploading = signal(false);
  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  selectedFile = signal<File | null>(null);
  selectedFileName = signal<string | null>(null);
  fileSizeWarning = signal<string | null>(null);
  isDragging = signal(false);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0] ?? null;
    this.applyFile(file);
  }

  private applyFile(file: File | null): void {
    this.errorMessage.set(null);
    this.fileSizeWarning.set(null);

    if (!file) {
      this.selectedFile.set(null);
      this.selectedFileName.set(null);
      return;
    }

    // Validate file extension
    if (!file.name.toLowerCase().endsWith('.stl')) {
      this.errorMessage.set('Proszę wybrać plik .stl');
      this.selectedFile.set(null);
      this.selectedFileName.set(null);
      return;
    }

    // Validate file size
    const maxSizeBytes = 50 * 1024 * 1024; // 50MB
    if (file.size > maxSizeBytes) {
      this.errorMessage.set('Plik jest zbyt duży (maksymalnie 50MB)');
      this.selectedFile.set(null);
      this.selectedFileName.set(null);
      return;
    }

    // File is valid
    this.selectedFile.set(file);
    this.selectedFileName.set(file.name);

    // Warn if file is large
    if (file.size > 10 * 1024 * 1024) {
      const sizeMB = Math.round(file.size / (1024 * 1024));
      this.fileSizeWarning.set(`Plik ma ${sizeMB}MB - przesyłanie może trwać chwilę`);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);

    const file = event.dataTransfer?.files?.[0] ?? null;
    this.applyFile(file);
  }

  onSubmit(): void {
    const file = this.selectedFile();
    if (!file) {
      this.errorMessage.set('Proszę najpierw wybrać plik .stl');
      return;
    }
    if (!this.listingId) {
      this.errorMessage.set('Błąd: brak identyfikatora zlecenia.');
      return;
    }

    this.uploading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.listingService.uploadStlFile(this.listingId, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.successMessage.set('Plik przesłany pomyślnie!');
        this.selectedFile.set(null);
        this.selectedFileName.set(null);
        this.fileSizeWarning.set(null);
        if (this.fileInput?.nativeElement) {
          this.fileInput.nativeElement.value = '';
        }
        this.uploaded.emit();
        setTimeout(() => this.successMessage.set(null), 5000);
      },
      error: (err) => {
        this.uploading.set(false);
        let msg: string;
        if (err?.status === 401 || err?.status === 403) {
          msg = 'Sesja wygasła lub brak uprawnień. Wyloguj się i zaloguj ponownie, a potem spróbuj jeszcze raz.';
        } else if (err?.status === 0) {
          msg = 'Brak połączenia z serwerem. Sprawdź, czy backend działa.';
        } else if (err?.error?.message) {
          msg = err.error.message;
        } else {
          msg = `Nie udało się przesłać plik (błąd ${err?.status ?? '?'}). Spróbuj ponownie.`;
        }
        this.errorMessage.set(msg);
      }
    });
  }
}
