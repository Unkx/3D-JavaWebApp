import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
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
  imports: [RouterLink, DecimalPipe],
  templateUrl: './finance.component.html',
  styleUrl: './finance.component.css'
})
export class FinanceComponent implements OnInit {
  private finance = inject(FinanceService);

  summary = signal<FinanceSummary | null>(null);
  alerts = signal<OverdueAlert[]>([]);
  pipeline = signal<PipelineEntry[]>([]);
  costs = signal<RecurringCost[]>([]);
  settings = signal<CostSettings | null>(null);
  loadError = signal(false);

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
    this.finance.getSettings().subscribe({ next: s => this.settings.set(s), error: () => this.loadError.set(true) });
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
}
