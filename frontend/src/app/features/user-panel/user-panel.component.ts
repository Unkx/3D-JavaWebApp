import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';

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
  imports: [RouterLink],
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

  ngOnInit(): void {
    this.http.get<UserProfile>('/api/users/me').subscribe({
      next:  p  => { this.profile.set(p); this.loading.set(false); },
      error: () => { this.error.set('Nie udało się załadować profilu.'); this.loading.set(false); }
    });
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('pl-PL', { year: 'numeric', month: 'long', day: 'numeric' });
  }

  roleLabel(role: string): string {
    return role === 'ADMIN' ? '🔑 Administrator' : '👤 Użytkownik';
  }
}
