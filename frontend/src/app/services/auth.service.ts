import { inject, Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

export interface AuthUser {
  token: string;
  email: string;
  role: string;
  userId: string;
}

export interface RegisterPayload { email: string; password: string; }
export interface LoginPayload    { email: string; password: string; }

const TOKEN_KEY = 'auth_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http   = inject(HttpClient);
  private router = inject(Router);

  private _user = signal<AuthUser | null>(this.loadFromStorage());

  readonly currentUser = this._user.asReadonly();
  readonly isLoggedIn  = computed(() => this._user() !== null);
  readonly isAdmin     = computed(() => this._user()?.role === 'ADMIN');

  register(payload: RegisterPayload): Observable<AuthUser> {
    return this.http.post<AuthUser>('/api/auth/register', payload).pipe(
      tap(user => this.persist(user))
    );
  }

  login(payload: LoginPayload): Observable<AuthUser> {
    return this.http.post<AuthUser>('/api/auth/login', payload).pipe(
      tap(user => this.persist(user))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this._user.set(null);
    this.router.navigate(['/']);
  }

  getToken(): string | null {
    return this._user()?.token ?? null;
  }

  private persist(user: AuthUser): void {
    localStorage.setItem(TOKEN_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  private loadFromStorage(): AuthUser | null {
    try {
      const raw = localStorage.getItem(TOKEN_KEY);
      return raw ? (JSON.parse(raw) as AuthUser) : null;
    } catch {
      return null;
    }
  }
}
