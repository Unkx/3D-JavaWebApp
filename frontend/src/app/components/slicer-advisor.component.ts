import { Component, ChangeDetectionStrategy, signal, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { tablerBrain } from '@ng-icons/tabler-icons';
import { SlicerAdviceService, SlicerAdviceResponse } from '../services/slicer-advice.service';

@Component({
  selector: 'app-slicer-advisor',
  imports: [FormsModule, NgIcon],
  providers: [provideIcons({ tablerBrain })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="advisor" aria-label="Asystent ustawień druku">
      <h2 class="advisor__title">
        <ng-icon name="tablerBrain" aria-hidden="true" />
        Asystent ustawień druku
      </h2>
      <p class="advisor__subtitle">
        Opisz co chcesz wydrukować — dostaniesz sugerowane ustawienia slicera, materiał i ostrzeżenia.
      </p>

      <div class="advisor__form">
        <textarea
          class="advisor__input"
          [(ngModel)]="description"
          placeholder="np. Obudowa na Raspberry Pi do zamontowania na ścianie, musi wytrzymać temperaturę do 40°C..."
          rows="3"
          maxlength="2000"
          aria-label="Opis wydruku"
        ></textarea>

        <div class="advisor__options">
          <div class="advisor__option">
            <label class="advisor__option-label" for="adv-material">Materiał (opcjonalnie)</label>
            <select id="adv-material" class="advisor__select" [(ngModel)]="material">
              <option value="">Automatyczny dobór</option>
              <option value="PLA">PLA</option>
              <option value="PETG">PETG</option>
              <option value="ABS">ABS</option>
              <option value="ASA">ASA</option>
              <option value="TPU">TPU</option>
              <option value="RESIN">Żywica (RESIN)</option>
            </select>
          </div>
          <div class="advisor__option">
            <label class="advisor__option-label" for="adv-size">Wielkość</label>
            <select id="adv-size" class="advisor__select" [(ngModel)]="size">
              <option value="small">Mały</option>
              <option value="medium">Średni</option>
              <option value="large">Duży</option>
            </select>
          </div>
          <div class="advisor__option">
            <label class="advisor__option-label" for="adv-quality">Jakość</label>
            <select id="adv-quality" class="advisor__select" [(ngModel)]="quality">
              <option value="fast">Szybki</option>
              <option value="normal">Normal</option>
              <option value="ultra">Ultra</option>
            </select>
          </div>
        </div>

        <button
          class="advisor__btn"
          (click)="getAdvice()"
          [disabled]="loading() || !description.trim()"
        >
          @if (loading()) {
            <span class="advisor__spinner" aria-hidden="true"></span>
            Analizowanie...
          } @else {
            Poradź mi ustawienia
          }
        </button>
      </div>

      @if (error()) {
        <div class="advisor__error" role="alert">{{ error() }}</div>
      }

      @if (advice()) {
        @let a = advice()!;
        <div class="advisor__result">
          @if (a.aiGenerated) {
            <span class="advisor__badge advisor__badge--ai">AI</span>
          } @else {
            <span class="advisor__badge">Reguły</span>
          }

          <div class="advisor__section">
            <h3 class="advisor__section-title">Materiał</h3>
            <div class="advisor__card">
              <span class="advisor__material">{{ a.recommendedMaterial }}</span>
              <span class="advisor__reason">{{ a.materialReason }}</span>
            </div>
          </div>

          <div class="advisor__section">
            <h3 class="advisor__section-title">Ustawienia slicera</h3>
            <div class="advisor__grid">
              @if (a.nozzleTemp > 0) {
                <div class="advisor__param">
                  <span class="advisor__param-label">Temp. dyszy</span>
                  <span class="advisor__param-value">{{ a.nozzleTemp }}°C</span>
                </div>
              }
              @if (a.bedTemp > 0) {
                <div class="advisor__param">
                  <span class="advisor__param-label">Temp. stołu</span>
                  <span class="advisor__param-value">{{ a.bedTemp }}°C</span>
                </div>
              }
              <div class="advisor__param">
                <span class="advisor__param-label">Warstwa</span>
                <span class="advisor__param-value">{{ a.layerHeight }}</span>
              </div>
              <div class="advisor__param">
                <span class="advisor__param-label">Wypełnienie</span>
                <span class="advisor__param-value">{{ a.infillPercent }}% ({{ a.infillPattern }})</span>
              </div>
              <div class="advisor__param">
                <span class="advisor__param-label">Prędkość</span>
                <span class="advisor__param-value">{{ a.printSpeed }}</span>
              </div>
              <div class="advisor__param">
                <span class="advisor__param-label">Supporty</span>
                <span class="advisor__param-value">
                  {{ a.supportsNeeded ? 'Tak (' + a.supportType + ')' : 'Nie' }}
                </span>
              </div>
            </div>
          </div>

          @if (a.warnings.length > 0) {
            <div class="advisor__section">
              <h3 class="advisor__section-title advisor__section-title--warn">Ostrzeżenia</h3>
              <ul class="advisor__list advisor__list--warn">
                @for (w of a.warnings; track w) {
                  <li>{{ w }}</li>
                }
              </ul>
            </div>
          }

          @if (a.tips.length > 0) {
            <div class="advisor__section">
              <h3 class="advisor__section-title">Wskazówki</h3>
              <ul class="advisor__list">
                @for (t of a.tips; track t) {
                  <li>{{ t }}</li>
                }
              </ul>
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    .advisor {
      background: var(--color-surface, #fff);
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 12px;
      padding: 1.5rem;
    }

    .advisor__title {
      font-size: 1.15rem;
      font-weight: 700;
      margin: 0 0 0.25rem;
      color: var(--color-text, #1a1a2e);
    }

    .advisor__subtitle {
      font-size: 0.85rem;
      color: var(--color-text-muted, #6b7280);
      margin: 0 0 1rem;
    }

    .advisor__form { display: flex; flex-direction: column; gap: 0.75rem; }

    .advisor__input {
      width: 100%;
      padding: 0.65rem 0.75rem;
      border: 1px solid var(--color-border, #d0d5dd);
      border-radius: 8px;
      font-size: 0.9rem;
      font-family: inherit;
      resize: vertical;
      background: var(--color-surface, #fff);
      color: var(--color-text, #1a1a2e);
      box-sizing: border-box;
    }
    .advisor__input:focus {
      outline: none;
      border-color: var(--color-primary, #3b82f6);
      box-shadow: 0 0 0 3px rgba(59,130,246,0.12);
    }

    .advisor__options {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 0.5rem;
    }

    .advisor__option { display: flex; flex-direction: column; gap: 0.2rem; }
    .advisor__option-label { font-size: 0.78rem; color: var(--color-text-muted, #6b7280); }

    .advisor__select {
      padding: 0.45rem 0.5rem;
      border: 1px solid var(--color-border, #d0d5dd);
      border-radius: 6px;
      font-size: 0.85rem;
      background: var(--color-surface, #fff);
      color: var(--color-text, #1a1a2e);
    }

    .advisor__btn {
      align-self: flex-start;
      padding: 0.6rem 1.2rem;
      border: none;
      border-radius: 8px;
      background: var(--color-primary, #3b82f6);
      color: #fff;
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      display: flex; align-items: center; gap: 0.5rem;
      transition: opacity 0.15s;
    }
    .advisor__btn:hover:not(:disabled) { opacity: 0.9; }
    .advisor__btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .advisor__spinner {
      display: inline-block; width: 16px; height: 16px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: advisor-spin 0.6s linear infinite;
    }
    @keyframes advisor-spin { to { transform: rotate(360deg); } }

    .advisor__error {
      margin-top: 0.75rem;
      padding: 0.6rem 0.75rem;
      border-radius: 8px;
      background: rgba(239,68,68,0.08);
      color: var(--color-danger, #ef4444);
      font-size: 0.85rem;
    }

    .advisor__result {
      margin-top: 1rem;
      display: flex; flex-direction: column; gap: 1rem;
      position: relative;
    }

    .advisor__badge {
      align-self: flex-end;
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.15rem 0.5rem;
      border-radius: 4px;
      background: var(--color-surface-alt, #f3f4f6);
      color: var(--color-text-muted, #6b7280);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .advisor__badge--ai {
      background: rgba(59,130,246,0.1);
      color: var(--color-primary, #3b82f6);
    }

    .advisor__section-title {
      font-size: 0.82rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--color-text-muted, #6b7280);
      margin: 0 0 0.5rem;
    }
    .advisor__section-title--warn { color: var(--color-danger, #ef4444); }

    .advisor__card {
      display: flex; flex-direction: column; gap: 0.25rem;
      padding: 0.65rem 0.75rem;
      border-radius: 8px;
      background: var(--color-surface-alt, #f9fafb);
    }

    .advisor__material {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--color-primary, #3b82f6);
    }

    .advisor__reason {
      font-size: 0.82rem;
      color: var(--color-text-muted, #6b7280);
    }

    .advisor__grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: 0.5rem;
    }

    .advisor__param {
      display: flex; flex-direction: column; gap: 0.1rem;
      padding: 0.5rem 0.65rem;
      border-radius: 8px;
      background: var(--color-surface-alt, #f9fafb);
    }
    .advisor__param-label {
      font-size: 0.72rem;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      color: var(--color-text-muted, #9ca3af);
    }
    .advisor__param-value {
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--color-text, #1a1a2e);
    }

    .advisor__list {
      margin: 0;
      padding: 0 0 0 1.2rem;
      font-size: 0.85rem;
      color: var(--color-text, #1a1a2e);
      display: flex; flex-direction: column; gap: 0.3rem;
    }
    .advisor__list--warn { color: var(--color-danger, #ef4444); }
  `]
})
export class SlicerAdvisorComponent {
  contextMaterial = input<string>('');
  contextSize = input<string>('');
  contextQuality = input<string>('');

  private slicerService = inject(SlicerAdviceService);

  description = '';
  material = '';
  size = 'medium';
  quality = 'normal';
  loading = signal(false);
  error = signal<string | null>(null);
  advice = signal<SlicerAdviceResponse | null>(null);

  getAdvice(): void {
    if (!this.description.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.advice.set(null);

    this.slicerService.getAdvice({
      description: this.description,
      material: this.material || this.contextMaterial() || undefined,
      size: this.size || this.contextSize() || undefined,
      quality: this.quality || this.contextQuality() || undefined
    }).subscribe({
      next: resp => {
        this.advice.set(resp);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Nie udało się uzyskać porady. Spróbuj ponownie.');
        this.loading.set(false);
      }
    });
  }
}
