import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { AuthService, AdminCode } from '../../services/auth.service';

interface UserProfile {
  id: string;
  email: string;
  role: string;
  createdAt: string;
  listingsCount: number;
  offersCount: number;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  gender: string | null;
  bio: string | null;
  dateOfBirth: string | null;
  street: string | null;
  houseNumber: string | null;
  city: string | null;
  postalCode: string | null;
}

interface UserSummary {
  id: string;
  email: string;
  role: string;
  firstName: string | null;
  lastName: string | null;
  createdAt: string;
  suspended: boolean;
}

interface AdminListing {
  id: string;
  title: string;
  status: string;
  createdAt: string;
  ownerEmail: string;
  ownerFirstName: string | null;
  ownerLastName: string | null;
  maxBudget: number | null;
  moderationStatus: string;
}

interface DailyCount { date: string; count: number; }
interface PathCount { path: string; count: number; }
interface ApiStats { totalRequests: number; errorCount: number; avgDurationMs: number; }
interface TrafficSummary {
  pageViewsByDay: DailyCount[];
  topPaths: PathCount[];
  apiStats: ApiStats;
}

@Component({
  selector: 'app-admin-panel',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './admin-panel.component.html',
  styleUrl: './admin-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminPanelComponent implements OnInit {
  private http = inject(HttpClient);
  auth         = inject(AuthService);

  profile      = signal<UserProfile | null>(null);
  loading      = signal(true);
  error        = signal<string | null>(null);

  // --- Profile details ---
  editingDetails  = signal(false);
  savingDetails   = signal(false);
  detailsError    = signal<string | null>(null);
  editFirstName   = signal('');
  editLastName    = signal('');
  editBio         = signal('');
  editDateOfBirth = signal('');
  editGender      = signal('');

  // --- Account ---
  editingAccount = signal(false);
  savingAccount  = signal(false);
  accountError   = signal<string | null>(null);
  editPhone      = signal('');

  // --- Shipping ---
  editingShipping  = signal(false);
  savingShipping   = signal(false);
  shippingError    = signal<string | null>(null);
  editStreet       = signal('');
  editHouseNumber  = signal('');
  editCity         = signal('');
  editPostalCode   = signal('');

  // --- Listings ---
  listings         = signal<AdminListing[]>([]);
  listingsLoading  = signal(false);
  confirmDeleteId  = signal<string | null>(null);
  deleting         = signal(false);
  moderatingListingId = signal<string | null>(null);

  // --- Users ---
  users        = signal<UserSummary[]>([]);
  usersLoading = signal(false);
  suspendingUserId = signal<string | null>(null);

  // --- Admin codes ---
  codes        = signal<AdminCode[]>([]);
  codesLoading = signal(false);
  generating   = signal(false);
  copiedCode   = signal<string | null>(null);

  // --- Traffic ---
  traffic        = signal<TrafficSummary | null>(null);
  trafficLoading = signal(false);

  ngOnInit(): void {
    this.loadProfile();
    this.loadListings();
    this.loadUsers();
    this.loadCodes();
    this.loadTraffic();
  }

  private loadProfile(): void {
    this.http.get<UserProfile>('/api/users/me').subscribe({
      next: p => { this.profile.set(p); this.loading.set(false); },
      error: () => { this.error.set('Nie udało się załadować profilu.'); this.loading.set(false); }
    });
  }

  private loadListings(): void {
    this.listingsLoading.set(true);
    this.http.get<AdminListing[]>('/api/admin/listings').subscribe({
      next: list => { this.listings.set(list); this.listingsLoading.set(false); },
      error: () => this.listingsLoading.set(false)
    });
  }

  deleteListing(id: string): void {
    if (this.confirmDeleteId() !== id) { this.confirmDeleteId.set(id); return; }
    this.deleting.set(true);
    this.http.delete(`/api/listings/${id}`).subscribe({
      next: () => {
        this.listings.update(list => list.filter(l => l.id !== id));
        this.confirmDeleteId.set(null);
        this.deleting.set(false);
      },
      error: () => this.deleting.set(false)
    });
  }

  cancelDelete(): void { this.confirmDeleteId.set(null); }

  hideListing(id: string): void {
    this.moderatingListingId.set(id);
    this.http.put<AdminListing>(`/api/admin/listings/${id}/hide`, {}).subscribe({
      next: updated => {
        this.listings.update(list => list.map(l => l.id === id ? updated : l));
        this.moderatingListingId.set(null);
      },
      error: () => this.moderatingListingId.set(null)
    });
  }

  unhideListing(id: string): void {
    this.moderatingListingId.set(id);
    this.http.put<AdminListing>(`/api/admin/listings/${id}/unhide`, {}).subscribe({
      next: updated => {
        this.listings.update(list => list.map(l => l.id === id ? updated : l));
        this.moderatingListingId.set(null);
      },
      error: () => this.moderatingListingId.set(null)
    });
  }

  private loadUsers(): void {
    this.usersLoading.set(true);
    this.http.get<UserSummary[]>('/api/admin/users').subscribe({
      next: list => { this.users.set(list); this.usersLoading.set(false); },
      error: () => this.usersLoading.set(false)
    });
  }

  suspendUser(id: string): void {
    this.suspendingUserId.set(id);
    this.http.put<UserSummary>(`/api/admin/users/${id}/suspend`, {}).subscribe({
      next: updated => {
        this.users.update(list => list.map(u => u.id === id ? updated : u));
        this.suspendingUserId.set(null);
      },
      error: () => this.suspendingUserId.set(null)
    });
  }

  unsuspendUser(id: string): void {
    this.suspendingUserId.set(id);
    this.http.put<UserSummary>(`/api/admin/users/${id}/unsuspend`, {}).subscribe({
      next: updated => {
        this.users.update(list => list.map(u => u.id === id ? updated : u));
        this.suspendingUserId.set(null);
      },
      error: () => this.suspendingUserId.set(null)
    });
  }

  private loadCodes(): void {
    this.codesLoading.set(true);
    this.auth.listAdminCodes().subscribe({
      next: list => { this.codes.set(list); this.codesLoading.set(false); },
      error: () => this.codesLoading.set(false)
    });
  }

  private loadTraffic(): void {
    this.trafficLoading.set(true);
    this.http.get<TrafficSummary>('/api/admin/traffic').subscribe({
      next: t => { this.traffic.set(t); this.trafficLoading.set(false); },
      error: () => this.trafficLoading.set(false)
    });
  }

  // --- Profile details ---
  startEditDetails(): void {
    const p = this.profile()!;
    this.editFirstName.set(p.firstName ?? '');
    this.editLastName.set(p.lastName ?? '');
    this.editBio.set(p.bio ?? '');
    this.editDateOfBirth.set(p.dateOfBirth ?? '');
    this.editGender.set(p.gender ?? '');
    this.detailsError.set(null);
    this.editingDetails.set(true);
  }
  cancelEditDetails(): void { this.editingDetails.set(false); }
  saveDetails(): void {
    const p = this.profile()!;
    this.savingDetails.set(true);
    this.detailsError.set(null);
    this.http.put<UserProfile>('/api/users/me', {
      firstName: this.editFirstName().trim() || null,
      lastName:  this.editLastName().trim()  || null,
      phone:     p.phone,
      gender:    this.editGender()           || null,
      bio:       this.editBio().trim()       || null,
      dateOfBirth: this.editDateOfBirth()    || null,
    }).subscribe({
      next: p => { this.profile.set(p); this.savingDetails.set(false); this.editingDetails.set(false); },
      error: (err: HttpErrorResponse) => { this.savingDetails.set(false); this.detailsError.set(err.error?.message ?? 'Nie udało się zapisać.'); }
    });
  }

  // --- Account ---
  startEditAccount(): void { this.editPhone.set(this.profile()?.phone ?? ''); this.accountError.set(null); this.editingAccount.set(true); }
  cancelEditAccount(): void { this.editingAccount.set(false); }
  saveAccount(): void {
    const p = this.profile()!;
    this.savingAccount.set(true);
    this.accountError.set(null);
    this.http.put<UserProfile>('/api/users/me', {
      firstName: p.firstName, lastName: p.lastName,
      phone: this.editPhone().trim() || null,
      gender: p.gender, bio: p.bio, dateOfBirth: p.dateOfBirth,
    }).subscribe({
      next: p => { this.profile.set(p); this.savingAccount.set(false); this.editingAccount.set(false); },
      error: (err: HttpErrorResponse) => { this.savingAccount.set(false); this.accountError.set(err.error?.message ?? 'Nie udało się zapisać.'); }
    });
  }

  // --- Shipping ---
  startEditShipping(): void {
    const p = this.profile()!;
    this.editStreet.set(p.street ?? ''); this.editHouseNumber.set(p.houseNumber ?? '');
    this.editCity.set(p.city ?? ''); this.editPostalCode.set(p.postalCode ?? '');
    this.shippingError.set(null); this.editingShipping.set(true);
  }
  cancelEditShipping(): void { this.editingShipping.set(false); }
  saveShipping(): void {
    this.savingShipping.set(true);
    this.shippingError.set(null);
    this.http.put<UserProfile>('/api/users/me/shipping', {
      street: this.editStreet().trim() || null,
      houseNumber: this.editHouseNumber().trim() || null,
      city: this.editCity().trim() || null,
      postalCode: this.editPostalCode().trim() || null,
    }).subscribe({
      next: p => { this.profile.set(p); this.savingShipping.set(false); this.editingShipping.set(false); },
      error: (err: HttpErrorResponse) => { this.savingShipping.set(false); this.shippingError.set(err.error?.message ?? 'Nie udało się zapisać.'); }
    });
  }

  // --- Admin codes ---
  generate(): void {
    this.generating.set(true);
    this.auth.generateAdminCode().subscribe({
      next: c => { this.generating.set(false); this.codes.update(list => [c, ...list]); },
      error: () => this.generating.set(false)
    });
  }
  copy(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copiedCode.set(code);
      setTimeout(() => this.copiedCode.set(null), 1500);
    });
  }

  // --- Helpers ---
  displayName(p: UserProfile): string {
    const full = [p.firstName, p.lastName].filter(Boolean).join(' ');
    return full || p.email;
  }
  avatarChar(p: UserProfile): string {
    return (p.firstName?.[0] ?? p.email[0]).toUpperCase();
  }
  userDisplayName(u: UserSummary): string {
    const full = [u.firstName, u.lastName].filter(Boolean).join(' ');
    return full || '—';
  }
  genderLabel(gender: string | null): string {
    const map: Record<string, string> = {
      MALE: 'Mężczyzna', FEMALE: 'Kobieta',
      OTHER: 'Inne', PREFER_NOT_TO_SAY: 'Wolę nie podawać',
    };
    return gender ? (map[gender] ?? gender) : '—';
  }
  statusLabel(status: string): string {
    const map: Record<string, string> = { OPEN: 'Otwarte', CLOSED: 'Zamknięte', IN_PROGRESS: 'W toku' };
    return map[status] ?? status;
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long', day: 'numeric' });
  }
  formatDob(dob: string | null): string {
    if (!dob) return '—';
    const [y, m, d] = dob.split('-');
    return `${d}.${m}.${y}`;
  }
  totalPageViews(): number {
    return this.traffic()?.pageViewsByDay.reduce((sum, d) => sum + d.count, 0) ?? 0;
  }
}
