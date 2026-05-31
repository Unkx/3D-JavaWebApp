import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { AuthService, AdminCode } from '../../services/auth.service';

interface UserProfile {
  id: string;
  email: string;
  role: string;
  createdAt: string;
  listingsCount: number;
  offersCount: number;
}

@Component({
  selector: 'app-user-panel',
  imports: [RouterLink, FormsModule],
  templateUrl: './user-panel.component.html',
  styleUrl: './user-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserPanelComponent implements OnInit {
  private http = inject(HttpClient);
  auth         = inject(AuthService);

  profile = signal<UserProfile | null>(null);
  loading = signal(true);
  error   = signal<string | null>(null);

  // Redeem (regular user becoming admin)
  redeemCode    = signal('');
  redeeming     = signal(false);
  redeemError   = signal<string | null>(null);
  redeemSuccess = signal(false);

  // Generate / list (admin)
  codes          = signal<AdminCode[]>([]);
  codesLoading   = signal(false);
  generating     = signal(false);
  copiedCode     = signal<string | null>(null);

  ngOnInit(): void {
    this.loadProfile();
  }

  private loadProfile(): void {
    this.loading.set(true);
    this.http.get<UserProfile>('/api/users/me').subscribe({
      next: p => {
        this.profile.set(p);
        this.loading.set(false);
        if (p.role === 'ADMIN') {
          this.loadCodes();
        }
      },
      error: () => { this.error.set('Nie udało się załadować profilu.'); this.loading.set(false); }
    });
  }

  isAdmin(): boolean {
    return this.profile()?.role === 'ADMIN';
  }

  // --- Redeem a code (user -> admin) ---
  redeem(): void {
    const code = this.redeemCode().trim();
    if (!code) { this.redeemError.set('Wpisz kod administratora.'); return; }

    this.redeeming.set(true);
    this.redeemError.set(null);
    this.redeemSuccess.set(false);

    this.auth.redeemAdminCode(code).subscribe({
      next: () => {
        this.redeeming.set(false);
        this.redeemSuccess.set(true);
        this.redeemCode.set('');
        // Refresh profile (role is now ADMIN) and load admin codes
        this.loadProfile();
      },
      error: (err: HttpErrorResponse) => {
        this.redeeming.set(false);
        this.redeemError.set(err.error?.message ?? 'Nie udało się zrealizować kodu.');
      }
    });
  }

  // --- Admin: generate + list codes ---
  private loadCodes(): void {
    this.codesLoading.set(true);
    this.auth.listAdminCodes().subscribe({
      next: list => { this.codes.set(list); this.codesLoading.set(false); },
      error: () => this.codesLoading.set(false)
    });
  }

  generate(): void {
    this.generating.set(true);
    this.auth.generateAdminCode().subscribe({
      next: created => {
        this.generating.set(false);
        this.codes.update(list => [created, ...list]);
      },
      error: () => this.generating.set(false)
    });
  }

  copy(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copiedCode.set(code);
      setTimeout(() => this.copiedCode.set(null), 1500);
    });
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long', day: 'numeric' });
  }

  roleLabel(role: string): string {
    return role === 'ADMIN' ? '🔑 Administrator' : '👤 Użytkownik';
  }
}
