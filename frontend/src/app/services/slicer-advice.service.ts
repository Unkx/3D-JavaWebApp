import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SlicerAdviceRequest {
  description: string;
  material?: string;
  size?: string;
  quality?: string;
}

export interface SlicerAdviceResponse {
  recommendedMaterial: string;
  materialReason: string;
  nozzleTemp: number;
  bedTemp: number;
  layerHeight: string;
  infillPercent: number;
  infillPattern: string;
  supportsNeeded: boolean;
  supportType: string;
  printSpeed: string;
  warnings: string[];
  tips: string[];
  aiGenerated: boolean;
}

@Injectable({ providedIn: 'root' })
export class SlicerAdviceService {
  private http = inject(HttpClient);

  getAdvice(request: SlicerAdviceRequest): Observable<SlicerAdviceResponse> {
    return this.http.post<SlicerAdviceResponse>('/api/ai/slicer-advice', request);
  }
}
