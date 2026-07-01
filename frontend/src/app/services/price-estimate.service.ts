import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PriceEstimateRequest {
  description: string;
  material?: string;
  size?: string;
  quality?: string;
}

export interface PriceEstimateResponse {
  priceLow: number;
  priceHigh: number;
  reasoning: string;
  assumedWeightGrams: number;
  assumedPrintHours: number;
  warnings: string[];
  aiGenerated: boolean;
}

@Injectable({ providedIn: 'root' })
export class PriceEstimateService {
  private http = inject(HttpClient);

  getEstimate(request: PriceEstimateRequest): Observable<PriceEstimateResponse> {
    return this.http.post<PriceEstimateResponse>('/api/ai/price-estimate', request);
  }
}
