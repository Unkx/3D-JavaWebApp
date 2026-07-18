import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RatingService, UserRatings } from '../../services/rating.service';
import { UserService, UpdatePrivacyPayload, UserPublicProfile } from '../../services/user.service';
import { IconComponent, IconName } from '../../components/icon.component';

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
  showCity: boolean;
  showRealName: boolean;
}

@Component({
  selector: 'app-user-panel',
  imports: [RouterLink, FormsModule, DecimalPipe, IconComponent],
  templateUrl: './user-panel.component.html',
  styleUrl: './user-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserPanelComponent implements OnInit {
  private http          = inject(HttpClient);
  private router        = inject(Router);
  private ratingService = inject(RatingService);
  private userService = inject(UserService);
  auth = inject(AuthService);

  profile = signal<UserProfile | null>(null);
  loading = signal(true);
  error   = signal<string | null>(null);

  ratings = signal<UserRatings | null>(null);
  ratingsLoading = signal(false);

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

  // --- Avatar ---
  publicSelf = signal<UserPublicProfile | null>(null);
  avatarUploading = signal(false);
  avatarError = signal<string | null>(null);
  avatarLoadFailed = signal(false);

  // --- Privacy ---
  editingPrivacy = signal(false);
  savingPrivacy  = signal(false);
  privacyError   = signal<string | null>(null);
  editShowCity      = signal(false);
  editShowRealName  = signal(true);

  // --- Redeem ---
  redeemCode    = signal('');
  redeeming     = signal(false);
  redeemError   = signal<string | null>(null);
  redeemSuccess = signal(false);

  ngOnInit(): void { this.loadProfile(); }

  private loadProfile(): void {
    this.loading.set(true);
    this.http.get<UserProfile>('/api/users/me').subscribe({
      next: p => {
        this.profile.set(p);
        this.loading.set(false);
        this.loadRatings(p.id);
        this.userService.getPublicProfile(p.id).subscribe({ next: ps => this.publicSelf.set(ps), error: () => {} });
      },
      error: () => { this.error.set('Nie udało się załadować profilu.'); this.loading.set(false); }
    });
  }

  private loadRatings(userId: string): void {
    this.ratingsLoading.set(true);
    this.ratingService.getUserRatings(userId).subscribe({
      next: r => { this.ratings.set(r); this.ratingsLoading.set(false); },
      error: () => this.ratingsLoading.set(false)
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

  // --- Avatar ---
  uploadAvatar(file: File): void {
    this.avatarUploading.set(true);
    this.avatarError.set(null);
    this.userService.uploadAvatar(file).subscribe({
      next: p => { this.publicSelf.set(p); this.avatarUploading.set(false); },
      error: (err: HttpErrorResponse) => {
        this.avatarUploading.set(false);
        this.avatarError.set(err.error?.message ?? 'Nie udało się przesłać awatara.');
      }
    });
  }
  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadAvatar(file);
    input.value = '';
  }
  deleteAvatar(): void {
    this.avatarUploading.set(true);
    this.avatarError.set(null);
    this.userService.deleteAvatar().subscribe({
      next: p => { this.publicSelf.set(p); this.avatarUploading.set(false); },
      error: (err: HttpErrorResponse) => {
        this.avatarUploading.set(false);
        this.avatarError.set(err.error?.message ?? 'Nie udało się usunąć awatara.');
      }
    });
  }
  importGoogleAvatar(): void {
    this.avatarUploading.set(true);
    this.userService.importGoogleAvatar().subscribe({
      next: p => { this.publicSelf.set(p); this.avatarUploading.set(false); },
      error: (err: HttpErrorResponse) => {
        this.avatarUploading.set(false);
        this.avatarError.set(err.error?.message ?? 'Nie udało się zaimportować zdjęcia z Google.');
      }
    });
  }
  avatarUrl(id: string): string {
    return this.userService.avatarUrl(id);
  }
  onAvatarError(): void {
    this.avatarLoadFailed.set(true);
  }

  // --- Privacy ---
  startEditPrivacy(): void {
    const p = this.profile()!;
    this.editShowCity.set(p.showCity);
    this.editShowRealName.set(p.showRealName);
    this.privacyError.set(null);
    this.editingPrivacy.set(true);
  }
  cancelEditPrivacy(): void { this.editingPrivacy.set(false); }
  savePrivacy(): void {
    this.savingPrivacy.set(true);
    this.privacyError.set(null);
    const payload: UpdatePrivacyPayload = {
      showCity: this.editShowCity(),
      showRealName: this.editShowRealName()
    };
    this.userService.updatePrivacy(payload).subscribe({
      next: p => { this.publicSelf.set(p); this.savingPrivacy.set(false); this.editingPrivacy.set(false); },
      error: (err: HttpErrorResponse) => {
        this.savingPrivacy.set(false);
        this.privacyError.set(err.error?.message ?? 'Nie udało się zapisać ustawień prywatności.');
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
    return role === 'ADMIN' ? 'Administrator' : 'Użytkownik';
  }
  roleIcon(role: string): IconName {
    return role === 'ADMIN' ? 'key' : 'user';
  }
}
