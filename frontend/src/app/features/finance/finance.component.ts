import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  FinanceService, FinanceSummary, MonthBucket, OverdueAlert, PipelineEntry, RecurringCost, CostSettings
} from '../../services/finance.service';



const PIPELINE_LABELS: Record<string, string> = {
  PENDING: 'Oczekujące',
  SELECTED: 'Wybrane',
  PAID: 'Opłacone',
  PRINTING: 'W druku',
  SHIPPED: 'Wysłane',
  DELIVERED: 'Dostarczone',
  REJECTED: 'Odrzucone'
};

const POLISH_MONTHS = ['Sty', 'Lut', 'Mar', 'Kwi', 'Maj', 'Cze', 'Lip', 'Sie', 'Wrz', 'Paź', 'Lis', 'Gru'];

@Component({
  selector: 'app-finance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DecimalPipe, ReactiveFormsModule],
  templateUrl: './finance.component.html',
  styleUrl: './finance.component.css'
})
export class FinanceComponent implements OnInit {
  private finance = inject(FinanceService);
  private fb = inject(FormBuilder);

  summary = signal<FinanceSummary | null>(null);
  alerts = signal<OverdueAlert[]>([]);
  pipeline = signal<PipelineEntry[]>([]);
  costs = signal<RecurringCost[]>([]);
  settings = signal<CostSettings | null>(null);
  loadError = signal(false);

  costForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    monthlyAmount: [0, [Validators.required, Validators.min(0.01)]],
    startDate: [''],
    endDate: ['']
  });
  settingsForm = this.fb.nonNullable.group({
    filamentPricePerKg: [0, [Validators.required, Validators.min(0.01)]],
    costPerPrintHour: [0, [Validators.required, Validators.min(0.01)]]
  });

  editingCostId = signal<string | null>(null);
  costSaving = signal(false);
  settingsSaved = signal(false);
  settingsSaving = signal(false);
  confirmDeleteId = signal<string | null>(null);
  deletingCostId = signal<string | null>(null);
  costFormError = signal(false);

  months = computed<MonthBucket[]>(() => this.summary()?.months ?? []);

  maxMonthValue = computed(() => {
    const values = this.months().flatMap(m => [Math.abs(m.inflow), Math.abs(m.costs)]);
    return Math.max(1, ...values);
  });

  pipelineBars = computed(() => this.pipeline().filter(p => p.status !== 'REJECTED').slice(0, 6));
  rejectedCount = computed(() => this.pipeline().find(p => p.status === 'REJECTED')?.count ?? 0);
  maxPipelineCount = computed(() => Math.max(1, ...this.pipelineBars().map(p => p.count)));

  ngOnInit(): void {
    this.finance.getSummary().subscribe({
      next: s => this.summary.set(s),
      error: () => this.loadError.set(true)
    });
    this.finance.getAlerts().subscribe({ next: a => this.alerts.set(a), error: () => this.loadError.set(true) });
    this.finance.getPipeline().subscribe({ next: p => this.pipeline.set(p), error: () => this.loadError.set(true) });
    this.finance.getCosts().subscribe({ next: c => this.costs.set(c), error: () => this.loadError.set(true) });
    this.finance.getSettings().subscribe({
      next: s => {
        this.settings.set(s);
        this.settingsForm.patchValue({
          filamentPricePerKg: s.filamentPricePerKg,
          costPerPrintHour: s.costPerPrintHour
        });
      },
      error: () => this.loadError.set(true)
    });
  }

  inflowHeight(m: MonthBucket): number {
    return (Math.abs(m.inflow) / this.maxMonthValue()) * 100;
  }

  costsHeight(m: MonthBucket): number {
    return (Math.abs(m.costs) / this.maxMonthValue()) * 100;
  }

  monthLabelFull(month: string): string {
    const [year, monthNum] = month.split('-');
    const idx = Number(monthNum) - 1;
    const name = POLISH_MONTHS[idx] ?? monthNum;
    return `${name} ${year}`;
  }

  monthLabelShort(month: string): string {
    const [, monthNum] = month.split('-');
    return monthNum ?? month;
  }

  pipelineLabel(status: string): string {
    return PIPELINE_LABELS[status] ?? status;
  }

  pipelineWidth(entry: PipelineEntry): number {
    const pct = (entry.count / this.maxPipelineCount()) * 100;
    return entry.count === 0 ? 4 : Math.max(pct, 4);
  }

  private refreshCosts(): void {
    this.finance.getCosts().subscribe({ next: c => this.costs.set(c), error: () => this.loadError.set(true) });
  }

  private refreshSummary(): void {
    this.finance.getSummary().subscribe({ next: s => this.summary.set(s), error: () => this.loadError.set(true) });
  }

  submitCost(): void {
    if (this.costSaving()) { return; }
    this.costFormError.set(false);
    if (this.costForm.invalid) {
      this.costForm.markAllAsTouched();
      this.costFormError.set(true);
      return;
    }

    const raw = this.costForm.getRawValue();
    const payload = {
      name: raw.name,
      monthlyAmount: raw.monthlyAmount,
      startDate: raw.startDate || null,
      endDate: raw.endDate || null
    };

    this.costSaving.set(true);
    const editingId = this.editingCostId();
    const request = editingId
      ? this.finance.updateCost(editingId, payload)
      : this.finance.createCost(payload);

    request.subscribe({
      next: () => {
        this.costSaving.set(false);
        this.cancelEdit();
        this.refreshCosts();
      },
      error: () => this.costSaving.set(false)
    });
  }

  startEdit(cost: RecurringCost): void {
    this.editingCostId.set(cost.id);
    this.confirmDeleteId.set(null);
    this.costFormError.set(false);
    this.costForm.patchValue({
      name: cost.name,
      monthlyAmount: cost.monthlyAmount,
      startDate: cost.startDate ?? '',
      endDate: cost.endDate ?? ''
    });
  }

  cancelEdit(): void {
    this.editingCostId.set(null);
    this.costFormError.set(false);
    this.costForm.reset({ name: '', monthlyAmount: 0, startDate: '', endDate: '' });
  }

  requestDelete(id: string): void {
    this.confirmDeleteId.set(id);
  }

  cancelDeleteCost(): void {
    this.confirmDeleteId.set(null);
  }

  confirmDelete(): void {
    const id = this.confirmDeleteId();
    if (!id) { return; }

    this.deletingCostId.set(id);
    this.finance.deleteCost(id).subscribe({
      next: () => {
        this.deletingCostId.set(null);
        this.confirmDeleteId.set(null);
        this.refreshCosts();
      },
      error: () => this.deletingCostId.set(null)
    });
  }

  saveSettings(): void {
    if (this.settingsSaving()) { return; }
    this.settingsSaved.set(false);
    if (this.settingsForm.invalid) {
      this.settingsForm.markAllAsTouched();
      return;
    }

    const payload = this.settingsForm.getRawValue();
    this.settingsSaving.set(true);
    this.finance.updateSettings(payload).subscribe({
      next: s => {
        this.settings.set(s);
        this.settingsSaving.set(false);
        this.settingsSaved.set(true);
        this.refreshSummary();
      },
      error: () => this.settingsSaving.set(false)
    });
  }
}
