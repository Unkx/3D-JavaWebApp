import { Component, Input, Output, EventEmitter, ViewChild, ElementRef, ChangeDetectionStrategy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ListingService } from '../services/listing.service';

@Component({
  selector: 'app-stl-file-upload',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="upload-container">
      <h3 class="upload-title">Przesłanie plików (STL / zdjęcia)</h3>

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
            accept=".stl,.png,.jpg,.jpeg,.gif,.webp"
            multiple
            class="file-input"
            (change)="onFileSelected($event)"
            aria-label="Wybierz pliki STL lub zdjęcia"
          />

          <span class="file-label">
            <span class="file-icon">📁</span>
            <span class="file-text">
              <strong>Kliknij aby wybrać</strong> lub przeciągnij pliki
              <small>Modele .stl oraz zdjęcia (.png, .jpg, .gif, .webp) — kilka naraz</small>
            </span>
          </span>
        </label>

        @if (selectedFiles().length > 0) {
          <ul class="selected-list">
            @for (f of selectedFiles(); track f.name + f.size) {
              <li class="selected-item">
                <span class="selected-item__name">📄 {{ f.name }}</span>
                <span class="selected-item__size">{{ formatSize(f.size) }}</span>
                <button type="button" class="selected-item__remove" (click)="removeFile(f)" [disabled]="uploading()" aria-label="Usuń z listy">✕</button>
              </li>
            }
          </ul>
        }

        <button
          type="button"
          class="btn btn--primary"
          (click)="onSubmit()"
          [disabled]="uploading() || selectedFiles().length === 0"
          [attr.aria-busy]="uploading()"
        >
          @if (uploading()) {
            <span class="btn-spinner" aria-hidden="true"></span>
            Przesyłanie...
          } @else {
            Prześlij {{ selectedFiles().length || '' }} {{ selectedFiles().length === 1 ? 'plik' : 'pliki' }}
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

    .file-text small {
      display: block;
      margin-top: 0.25rem;
      font-size: 0.75rem;
      color: #9ca3af;
    }

    .warning-text {
      font-size: 0.875rem;
      color: #d97706;
      margin: 0;
    }

    .selected-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }

    .selected-item {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.45rem 0.7rem;
      background: #f9fafb;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
    }

    .selected-item__name {
      font-size: 0.875rem;
      color: #111827;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .selected-item__size {
      font-size: 0.75rem;
      color: #9ca3af;
      margin-left: auto;
      white-space: nowrap;
    }

    .selected-item__remove {
      border: none;
      background: transparent;
      color: #9ca3af;
      cursor: pointer;
      font-size: 1rem;
      line-height: 1;
      padding: 0.1rem 0.3rem;
      border-radius: 4px;
    }

    .selected-item__remove:hover:not(:disabled) {
      background: #fee2e2;
      color: #dc2626;
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

  private static readonly MAX_SIZE = 50 * 1024 * 1024; // 50MB
  private static readonly ALLOWED = ['.stl', '.png', '.jpg', '.jpeg', '.gif', '.webp'];

  uploading = signal(false);
  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  selectedFiles = signal<File[]>([]);
  isDragging = signal(false);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(input?.files);
  }

  private addFiles(fileList: FileList | null | undefined): void {
    this.errorMessage.set(null);
    if (!fileList || fileList.length === 0) return;

    const accepted: File[] = [...this.selectedFiles()];
    const rejected: string[] = [];

    for (const file of Array.from(fileList)) {
      const name = file.name.toLowerCase();
      if (!StlFileUploadComponent.ALLOWED.some(ext => name.endsWith(ext))) {
        rejected.push(`${file.name} (nieobsługiwany typ)`);
        continue;
      }
      if (file.size > StlFileUploadComponent.MAX_SIZE) {
        rejected.push(`${file.name} (> 50MB)`);
        continue;
      }
      // Skip duplicates already in the list
      if (accepted.some(f => f.name === file.name && f.size === file.size)) {
        continue;
      }
      accepted.push(file);
    }

    this.selectedFiles.set(accepted);
    if (rejected.length > 0) {
      this.errorMessage.set('Pominięto: ' + rejected.join(', '));
    }
  }

  removeFile(file: File): void {
    this.selectedFiles.update(list => list.filter(f => f !== file));
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
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
    this.addFiles(event.dataTransfer?.files);
  }

  onSubmit(): void {
    const files = this.selectedFiles();
    if (files.length === 0) {
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

    this.listingService.uploadStlFiles(this.listingId, files).subscribe({
      next: (saved) => {
        this.uploading.set(false);
        const n = saved?.length ?? files.length;
        this.successMessage.set(`Przesłano ${n} ${n === 1 ? 'plik' : 'pliki'}!`);
        this.selectedFiles.set([]);
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
          msg = `Nie udało się przesłać plików (błąd ${err?.status ?? '?'}). Spróbuj ponownie.`;
        }
        this.errorMessage.set(msg);
      }
    });
  }
}
