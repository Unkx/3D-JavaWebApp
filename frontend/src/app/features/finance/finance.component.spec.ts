import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { FinanceComponent } from './finance.component';
import { FinanceSummary, OverdueAlert, PipelineEntry, RecurringCost, CostSettings } from '../../services/finance.service';

const summary: FinanceSummary = {
  totalReleased: 1234.5,
  totalHeld: 200,
  monthProfit: 300.25,
  monthCosts: 50,
  months: Array.from({ length: 12 }, (_, i) => ({
    month: `2026-${String(i + 1).padStart(2, '0')}`,
    inflow: 100 + i,
    pending: 10,
    costs: 20 + i,
    net: 80
  }))
};

const alerts: OverdueAlert[] = [
  { offerId: 'o1', listingId: 'l1', listingTitle: 'Model smoka', buyerName: 'Jan Kowalski', price: 99.99, daysOverdue: 5 }
];

const pipeline: PipelineEntry[] = [
  { status: 'PENDING', count: 3, value: 100 },
  { status: 'SELECTED', count: 2, value: 200 },
  { status: 'PAID', count: 1, value: 50 },
  { status: 'PRINTING', count: 1, value: 60 },
  { status: 'SHIPPED', count: 0, value: 0 },
  { status: 'DELIVERED', count: 4, value: 400 },
  { status: 'REJECTED', count: 2, value: 0 }
];

const costs: RecurringCost[] = [];
const settings: CostSettings = { filamentPricePerKg: 90, costPerPrintHour: 2 };

describe('FinanceComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushAll(): void {
    httpMock.expectOne('/api/finance/summary').flush(summary);
    httpMock.expectOne('/api/finance/alerts').flush(alerts);
    httpMock.expectOne('/api/finance/pipeline').flush(pipeline);
    httpMock.expectOne('/api/finance/costs').flush(costs);
    httpMock.expectOne('/api/finance/settings').flush(settings);
  }

  it('renders KPI values and 12 chart columns', () => {
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    const kpiValues = compiled.querySelectorAll('.kpi__value');
    expect(kpiValues.length).toBe(4);
    expect(kpiValues[0].textContent).toContain('1,234.50');

    expect(compiled.querySelectorAll('.chart__col').length).toBe(12);
  });

  it('renders an alert row with a link to the listing', () => {
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    const link = compiled.querySelector<HTMLAnchorElement>('.alert-row__title');
    expect(link?.textContent).toContain('Model smoka');
    expect(link?.getAttribute('href')).toBe('/zlecenia/l1');
  });

  it('shows the empty state when there are no alerts', () => {
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/finance/summary').flush(summary);
    httpMock.expectOne('/api/finance/alerts').flush([]);
    httpMock.expectOne('/api/finance/pipeline').flush(pipeline);
    httpMock.expectOne('/api/finance/costs').flush(costs);
    httpMock.expectOne('/api/finance/settings').flush(settings);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('Brak zaległości');
  });

  it('renders pipeline statuses with Polish labels', () => {
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('Oczekujące');
    expect(compiled.textContent).toContain('Wybrane');
    expect(compiled.textContent).toContain('Opłacone');
    expect(compiled.textContent).toContain('W druku');
    expect(compiled.textContent).toContain('Dostarczone');
    expect(compiled.textContent).toContain('Odrzucone: 2');
  });

  it('shows the error state when a request fails', () => {
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/finance/summary').flush('error', { status: 500, statusText: 'Server Error' });
    httpMock.expectOne('/api/finance/alerts').flush([]);
    httpMock.expectOne('/api/finance/pipeline').flush([]);
    httpMock.expectOne('/api/finance/costs').flush([]);
    httpMock.expectOne('/api/finance/settings').flush(settings);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('Nie udało się załadować danych finansowych.');
  });
});
