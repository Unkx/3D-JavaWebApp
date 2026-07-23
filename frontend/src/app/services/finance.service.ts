import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface MonthBucket { month: string; inflow: number; pending: number; costs: number; net: number; }
export interface FinanceSummary { totalReleased: number; totalHeld: number; monthProfit: number; monthCosts: number; months: MonthBucket[]; }
export interface PipelineEntry { status: string; count: number; value: number; }
export interface OverdueAlert { offerId: string; listingId: string; listingTitle: string; buyerName: string; price: number; daysOverdue: number; }
export interface RecurringCost { id: string; name: string; monthlyAmount: number; startDate: string; endDate: string | null; }
export interface RecurringCostRequest { name: string; monthlyAmount: number; startDate: string | null; endDate: string | null; }
export interface CostSettings { filamentPricePerKg: number; costPerPrintHour: number; }

@Injectable({ providedIn: 'root' })
export class FinanceService {
  private http = inject(HttpClient);
  private base = '/api/finance';

  getSummary(): Observable<FinanceSummary> { return this.http.get<FinanceSummary>(`${this.base}/summary`); }
  getPipeline(): Observable<PipelineEntry[]> { return this.http.get<PipelineEntry[]>(`${this.base}/pipeline`); }
  getAlerts(): Observable<OverdueAlert[]> { return this.http.get<OverdueAlert[]>(`${this.base}/alerts`); }
  getCosts(): Observable<RecurringCost[]> { return this.http.get<RecurringCost[]>(`${this.base}/costs`); }
  createCost(req: RecurringCostRequest): Observable<RecurringCost> { return this.http.post<RecurringCost>(`${this.base}/costs`, req); }
  updateCost(id: string, req: RecurringCostRequest): Observable<RecurringCost> { return this.http.put<RecurringCost>(`${this.base}/costs/${id}`, req); }
  deleteCost(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/costs/${id}`); }
  getSettings(): Observable<CostSettings> { return this.http.get<CostSettings>(`${this.base}/settings`); }
  updateSettings(req: CostSettings): Observable<CostSettings> { return this.http.put<CostSettings>(`${this.base}/settings`, req); }
}
