import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Rating {
  id: string;
  offerId: string;
  raterId: string;
  raterDisplayName: string;
  ratedUserId: string;
  stars: number;
  comment: string | null;
  moderationStatus: string;
  createdAt: string;
}

export interface RatingSummary {
  averageStars: number | null;
  count: number;
}

export interface UserRatings {
  summary: RatingSummary;
  ratings: { content: Rating[]; page: number; size: number; totalElements: number; totalPages: number; last: boolean };
}

@Injectable({ providedIn: 'root' })
export class RatingService {
  private http = inject(HttpClient);

  createRating(offerId: string, stars: number, comment?: string): Observable<Rating> {
    return this.http.post<Rating>(`/api/offers/${offerId}/ratings`, { stars, comment });
  }

  getOfferRatings(offerId: string): Observable<Rating[]> {
    return this.http.get<Rating[]>(`/api/offers/${offerId}/ratings`);
  }

  getUserRatings(userId: string, page = 0, size = 20): Observable<UserRatings> {
    return this.http.get<UserRatings>(`/api/users/${userId}/ratings?page=${page}&size=${size}`);
  }
}
