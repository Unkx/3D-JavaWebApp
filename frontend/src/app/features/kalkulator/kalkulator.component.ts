import { Component, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RevealDirective } from '../../directives/reveal.directive';

type PriceMode = 'markup' | 'price' | 'hand';

@Component({
  selector: 'app-kalkulator',
  imports: [RevealDirective, DecimalPipe],
  templateUrl: './kalkulator.component.html',
  styleUrl: './kalkulator.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class KalkulatorComponent {
  // ---- Filament ----
  readonly filamentPricePerKg = signal(80);
  readonly filamentGrams = signal(120);
  readonly wastePercent = signal(5);

  // ---- Druk: czas, prąd, maszyna ----
  readonly printHours = signal(6);
  readonly printerWatts = signal(150);
  readonly electricityPrice = signal(1.10);
  readonly machineAmortPerHour = signal(2.00);

  // ---- Twój czas i pozostałe koszty ----
  readonly laborHours = signal(0.5);
  readonly extraMaterials = signal(3);

  // ---- Jak ustalić cenę? (edytujesz jedno z trzech pól) ----
  readonly lastEdited = signal<PriceMode>('markup');
  readonly markupPercent = signal(60);
  readonly netPriceInput = signal<number | null>(null);
  readonly targetHandInput = signal<number | null>(null);

  // ---- Koszty ----
  readonly effectiveGrams = computed(() => this.filamentGrams() * (1 + this.wastePercent() / 100));
  readonly filamentCost = computed(() => (this.filamentPricePerKg() / 1000) * this.effectiveGrams());
  readonly energyKwh = computed(() => (this.printerWatts() / 1000) * this.printHours());
  readonly energyCost = computed(() => this.energyKwh() * this.electricityPrice());
  readonly machineCost = computed(() => this.machineAmortPerHour() * this.printHours());
  readonly totalCost = computed(() =>
    this.filamentCost() + this.energyCost() + this.machineCost() + this.extraMaterials()
  );

  // ---- Cena, wg ostatnio edytowanego pola ----
  readonly priceNet = computed(() => {
    const cost = this.totalCost();
    switch (this.lastEdited()) {
      case 'price':
        return Math.max(0, this.netPriceInput() ?? cost);
      case 'hand':
        return Math.max(0, cost + (this.targetHandInput() ?? 0));
      default:
        return Math.max(0, cost * (1 + this.markupPercent() / 100));
    }
  });

  readonly takeHome = computed(() => this.priceNet() - this.totalCost());
  readonly markupPctDisplay = computed(() => {
    const cost = this.totalCost();
    return cost > 0 ? (this.priceNet() / cost - 1) * 100 : 0;
  });
  readonly hourlyRate = computed(() => {
    const h = this.laborHours();
    return h > 0 ? this.takeHome() / h : NaN;
  });

  // ---- Wartości wyświetlane w trzech polach cenowych (aktywne pole = surowy input) ----
  readonly markupDisplay = computed(() =>
    this.lastEdited() === 'markup' ? this.markupPercent() : this.markupPctDisplay()
  );
  readonly netPriceDisplay = computed(() =>
    this.lastEdited() === 'price' ? (this.netPriceInput() ?? this.priceNet()) : this.priceNet()
  );
  readonly targetHandDisplay = computed(() =>
    this.lastEdited() === 'hand' ? (this.targetHandInput() ?? this.takeHome()) : this.takeHome()
  );

  parseNumFromEvent(event: Event): number {
    return this.parseNum((event.target as HTMLInputElement).value);
  }

  onMarkupInput(event: Event): void {
    this.markupPercent.set(this.parseNumFromEvent(event));
    this.lastEdited.set('markup');
  }

  onNetPriceInput(event: Event): void {
    this.netPriceInput.set(this.parseNumFromEvent(event));
    this.lastEdited.set('price');
  }

  onTargetHandInput(event: Event): void {
    this.targetHandInput.set(this.parseNumFromEvent(event));
    this.lastEdited.set('hand');
  }

  formatPln(n: number): string {
    if (!isFinite(n)) return '-';
    return n.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' zł';
  }

  formatRate(n: number): string {
    if (!isFinite(n)) return '- ustaw czas pracy';
    return n.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' zł/h';
  }

  formatNumber(n: number, minFrac: number, maxFrac: number): string {
    if (!isFinite(n)) return '-';
    return n.toLocaleString('pl-PL', { minimumFractionDigits: minFrac, maximumFractionDigits: maxFrac });
  }

  private parseNum(value: string): number {
    const v = parseFloat(value);
    return isNaN(v) ? 0 : v;
  }
}
