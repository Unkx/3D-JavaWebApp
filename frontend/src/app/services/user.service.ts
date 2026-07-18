import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface User {
  id?: string;
  email: string;
  password?: string;
  role?: string;
  stripeCustomerId?: string;
  createdAt?: string;
}

export interface UserPublicProfile {
  id: string;
  displayName: string;
  city: string | null;
  emailVerified: boolean;
  hasGoogleAuth: boolean;
  hasFacebookAuth: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  hasAvatarData: boolean;
  avatarUrl: string | null;
  activeListingsCount: number;
}

export interface UpdatePrivacyPayload {
  showCity: boolean;
  showRealName: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private apiUrl = '/api/users';

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  getUser(id: string): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  createUser(user: User): Observable<User> {
    return this.http.post<User>(this.apiUrl, user);
  }

  updateUser(id: string, user: User): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, user);
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // --- Public profile viewer ---

  getPublicProfile(id: string): Observable<UserPublicProfile> {
    return this.http.get<UserPublicProfile>(`${this.apiUrl}/${id}/public-profile`);
  }

  avatarUrl(id: string): string {
    return `${this.apiUrl}/${id}/avatar`;
  }

  uploadAvatar(file: File): Observable<UserPublicProfile> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<UserPublicProfile>(`${this.apiUrl}/me/avatar`, formData);
  }

  deleteAvatar(): Observable<UserPublicProfile> {
    return this.http.delete<UserPublicProfile>(`${this.apiUrl}/me/avatar`);
  }

  importGoogleAvatar(): Observable<UserPublicProfile> {
    return this.http.post<UserPublicProfile>(`${this.apiUrl}/me/avatar/import-google`, {});
  }

  updatePrivacy(payload: UpdatePrivacyPayload): Observable<UserPublicProfile> {
    return this.http.put<UserPublicProfile>(`${this.apiUrl}/me/privacy`, payload);
  }
}
