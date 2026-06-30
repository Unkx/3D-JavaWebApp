import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

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

@Component({
  selector: 'app-user-panel',
  imports: [RouterLink, FormsModule],
  templateUrl: './user-panel.component.html',
  styleUrl: './user-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserPanelComponent implements OnInit {
  private http   = inject(HttpClient);
  private router = inject(Router);
  auth           = inject(AuthService);

  profile = signal<UserProfile | null>(null);
  loading = signal(true);
  error   = signal<string | null>(null);

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
  editingShipping = signal(false);
  savingShipping  = signal(false);
  shippingError   = signal<string | null>(null);
  editStreet      = signal('');
  editHouseNumber = signal('');
  editCity        = signal('');
  editPostalCode  = signal('');

  // --- Redeem ---
  redeemCode    = signal('');
  redeeming     = signal(false);
  redeemError   = signal<string | null>(null);
  redeemSuccess = signal(false);

  ngOnInit(): void { this.loadProfile(); }

  private loadProfile(): void {
    this.loading.set(true);
    this.http.get<UserProfile>('/api/users/me').subscribe({
      next: p => { this.profile.set(p); this.loading.set(false); },
      error: () => { this.error.set('Nie udało się załadować profilu.'); this.loading.set(false); }
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
      firstName:   this.editFirstName().trim()   || null,
      lastName:    this.editLastName().trim()    || null,
      phone:       p.phone,
      gender:      this.editGender()             || null,
      bio:         this.editBio().trim()         || null,
      dateOfBirth: this.editDateOfBirth()        || null,
    }).subscribe({
      next: p => { this.profile.set(p); this.savingDetails.set(false); this.editingDetails.set(false); },
      error: (err: HttpErrorResponse) => {
        this.savingDetails.set(false);
        this.detailsError.set(err.error?.message ?? 'Nie udało się zapisać zmian.');
      }
    });
  }

  // --- Account ---
  startEditAccount(): void {
    this.editPhone.set(this.profile()?.phone ?? '');
    this.accountError.set(null);
    this.editingAccount.set(true);
  }
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
      error: (err: HttpErrorResponse) => {
        this.savingAccount.set(false);
        this.accountError.set(err.error?.message ?? 'Nie udało się zapisać zmian.');
      }
    });
  }

  // --- Shipping ---
  startEditShipping(): void {
    const p = this.profile()!;
    this.editStreet.set(p.street ?? '');
    this.editHouseNumber.set(p.houseNumber ?? '');
    this.editCity.set(p.city ?? '');
    this.editPostalCode.set(p.postalCode ?? '');
    this.shippingError.set(null);
    this.editingShipping.set(true);
  }
  cancelEditShipping(): void { this.editingShipping.set(false); }
  saveShipping(): void {
    this.savingShipping.set(true);
    this.shippingError.set(null);
    this.http.put<UserProfile>('/api/users/me/shipping', {
      street:      this.editStreet().trim()      || null,
      houseNumber: this.editHouseNumber().trim() || null,
      city:        this.editCity().trim()        || null,
      postalCode:  this.editPostalCode().trim()  || null,
    }).subscribe({
      next: p => { this.profile.set(p); this.savingShipping.set(false); this.editingShipping.set(false); },
      error: (err: HttpErrorResponse) => {
        this.savingShipping.set(false);
        this.shippingError.set(err.error?.message ?? 'Nie udało się zapisać adresu.');
      }
    });
  }

  // --- Redeem ---
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
        this.router.navigate(['/admin']);
      },
      error: (err: HttpErrorResponse) => {
        this.redeeming.set(false);
        this.redeemError.set(err.error?.message ?? 'Nie udało się zrealizować kodu.');
      }
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
  genderLabel(gender: string | null): string {
    const map: Record<string, string> = {
      MALE: 'Mężczyzna', FEMALE: 'Kobieta',
      OTHER: 'Inne', PREFER_NOT_TO_SAY: 'Wolę nie podawać',
    };
    return gender ? (map[gender] ?? gender) : '—';
  }
  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long', day: 'numeric' });
  }
  formatDob(dob: string | null): string {
    if (!dob) return '—';
    const [y, m, d] = dob.split('-');
    return `${d}.${m}.${y}`;
  }
  roleLabel(role: string): string {
    return role === 'ADMIN' ? '🔑 Administrator' : '👤 Użytkownik';
  }
}
