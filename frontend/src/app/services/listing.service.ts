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
  estimatorSize?: string;
  estimatorQuality?: string;
  previewImageUrl?: string;
  hasAttachments?: boolean;
}

export interface UpdateListingPayload {
  description: string;
  requiredMaterial: string;
  maxBudget?: number | null;
  estimatorSize?: string;
  estimatorQuality?: string;
}

export interface StlFile {
  id: string;
  fileName: string;
  fileSize: number | null;
  contentType?: string;
  kind: 'stl' | 'obj' | 'image';
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class ListingService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/listings';

  getListings(page = 0, size = 12, search = ''): Observable<PageResponse<Listing>> {
    const q = search.trim();
    const params = q ? `page=${page}&size=${size}&search=${encodeURIComponent(q)}` : `page=${page}&size=${size}`;
    return this.http.get<PageResponse<Listing>>(`${this.apiUrl}?${params}`);
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

  // --- Multiple STL files ---

  getStlFiles(listingId: string): Observable<StlFile[]> {
    return this.http.get<StlFile[]>(`${this.apiUrl}/${listingId}/stl-files`);
  }

  uploadStlFiles(listingId: string, files: File[]): Observable<StlFile[]> {
    const formData = new FormData();
    for (const file of files) {
      formData.append('files', file);
    }
    return this.http.post<StlFile[]>(`${this.apiUrl}/${listingId}/stl-files`, formData, {
      reportProgress: true
    });
  }

  deleteStlFile(listingId: string, fileId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${listingId}/stl-files/${fileId}`);
  }

  reorderStlFiles(listingId: string, orderedIds: string[]): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${listingId}/stl-files/reorder`, orderedIds);
  }

  patchListing(id: string, payload: UpdateListingPayload): Observable<Listing> {
    return this.http.patch<Listing>(`${this.apiUrl}/${id}`, payload);
  }
}
