import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Listing {
  id?: string;
  title: string;
  description: string;
  requiredMaterial: string;
  maxBudget?: number;
  stlFileUrl?: string;
  stlFileName?: string;
  status?: string;
  createdAt?: string;
  user?: { id: string };
}

@Injectable({ providedIn: 'root' })
export class ListingService {
  private http = inject(HttpClient);
  private apiUrl = '/api/listings';

  getListings(): Observable<Listing[]> {
    return this.http.get<Listing[]>(this.apiUrl);
  }

  getListing(id: string): Observable<Listing> {
    return this.http.get<Listing>(`${this.apiUrl}/${id}`);
  }

  getMyListings(): Observable<Listing[]> {
    return this.http.get<Listing[]>(`${this.apiUrl}/my`);
  }

  createListing(listing: Omit<Listing, 'id' | 'status' | 'createdAt'>): Observable<Listing> {
    return this.http.post<Listing>(this.apiUrl, listing);
  }

  updateListing(id: string, listing: Listing): Observable<Listing> {
    return this.http.put<Listing>(`${this.apiUrl}/${id}`, listing);
  }

  deleteListing(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  uploadStlFile(id: string, file: File): Observable<Listing> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Listing>(`${this.apiUrl}/${id}/upload-stl`, formData, {
      reportProgress: true
    });
  }
}
