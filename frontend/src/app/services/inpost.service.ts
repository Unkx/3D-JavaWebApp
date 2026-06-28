import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface InpostPoint {
  name: string;
  address: {
    line1: string;
    line2: string;
  };
  address_details: {
    city: string;
    street: string;
    building_number: string;
    post_code: string;
  };
  location: {
    latitude: number;
    longitude: number;
  };
  opening_hours: string;
  location_description: string | null;
}

interface InpostResponse {
  items: InpostPoint[];
  total_pages: number;
  count: number;
}

@Injectable({ providedIn: 'root' })
export class InpostService {
  private http = inject(HttpClient);
  private baseUrl = '/inpost-api/points';

  searchByLocation(lat: number, lng: number, limit = 10): Observable<InpostPoint[]> {
    return this.http.get<InpostResponse>(this.baseUrl, {
      params: {
        type: 'parcel_locker',
        relative_point: `${lat},${lng}`,
        per_page: limit.toString(),
        status: 'Operating'
      }
    }).pipe(map(r => r.items));
  }

  searchByQuery(query: string, limit = 10): Observable<InpostPoint[]> {
    return this.http.get<InpostResponse>(this.baseUrl, {
      params: {
        type: 'parcel_locker',
        name: query,
        per_page: limit.toString(),
        status: 'Operating'
      }
    }).pipe(map(r => r.items));
  }

  searchByCity(city: string, limit = 10): Observable<InpostPoint[]> {
    return this.http.get<InpostResponse>(this.baseUrl, {
      params: {
        type: 'parcel_locker',
        'address[city]': city,
        per_page: limit.toString(),
        status: 'Operating'
      }
    }).pipe(map(r => r.items));
  }
}
