import {
  Component, ChangeDetectionStrategy, signal, inject, output, input,
  OnInit, OnDestroy, ElementRef, viewChild, afterNextRender
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { tablerCurrentLocation } from '@ng-icons/tabler-icons';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, catchError } from 'rxjs';
import { InpostService, InpostPoint } from '../services/inpost.service';
import * as L from 'leaflet';

const MARKER_ICON = L.icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const SELECTED_ICON = L.icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [30, 49],
  iconAnchor: [15, 49],
  popupAnchor: [1, -40],
  shadowSize: [41, 41],
  className: 'paczkomat-marker--selected'
});

@Component({
  selector: 'app-paczkomat-picker',
  imports: [FormsModule, NgIcon],
  providers: [provideIcons({ tablerCurrentLocation })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="paczkomat-picker">
      <div class="paczkomat-picker__search">
        <div class="paczkomat-picker__input-row">
          <input
            type="text"
            class="paczkomat-picker__input"
            placeholder="Szukaj po mieście lub nazwie paczkomatu..."
            [ngModel]="searchQuery()"
            (ngModelChange)="onSearchChange($event)"
            aria-label="Szukaj paczkomatu"
          />
          <button
            type="button"
            class="paczkomat-picker__locate-btn"
            (click)="useMyLocation()"
            [disabled]="locating()"
            title="Użyj mojej lokalizacji"
            aria-label="Użyj mojej lokalizacji"
          >
            @if (locating()) {
              <span class="paczkomat-picker__spinner" aria-hidden="true"></span>
            } @else {
              <ng-icon name="tablerCurrentLocation" aria-hidden="true" />
            }
          </button>
        </div>
        @if (locationError()) {
          <p class="paczkomat-picker__error" role="alert">{{ locationError() }}</p>
        }
      </div>

      <div class="paczkomat-picker__map-container" #mapContainer></div>

      @if (loading()) {
        <div class="paczkomat-picker__loading" aria-busy="true">
          <span class="paczkomat-picker__spinner" aria-hidden="true"></span>
          Szukanie paczkomatów...
        </div>
      }

      @if (points().length > 0) {
        <ul class="paczkomat-picker__list" aria-label="Lista paczkomatów">
          @for (point of points(); track point.name) {
            <li
              class="paczkomat-picker__item"
              [class.paczkomat-picker__item--selected]="selectedPoint()?.name === point.name"
              (click)="selectPoint(point)"
              (keyup.enter)="selectPoint(point)"
              tabindex="0"
              [attr.aria-selected]="selectedPoint()?.name === point.name"
              role="option"
            >
              <span class="paczkomat-picker__item-name">{{ point.name }}</span>
              <span class="paczkomat-picker__item-addr">
                {{ point.address_details.street }} {{ point.address_details.building_number }},
                {{ point.address_details.city }}
              </span>
              @if (point.location_description) {
                <span class="paczkomat-picker__item-desc">{{ point.location_description }}</span>
              }
            </li>
          }
        </ul>
      } @else if (!loading() && searchQuery()) {
        <p class="paczkomat-picker__empty">Nie znaleziono paczkomatów.</p>
      }
    </div>
  `,
  styles: [`
    .paczkomat-picker { display: flex; flex-direction: column; gap: 0.75rem; }

    .paczkomat-picker__search { display: flex; flex-direction: column; gap: 0.25rem; }

    .paczkomat-picker__input-row {
      display: flex; gap: 0.5rem; align-items: stretch;
    }

    .paczkomat-picker__input {
      flex: 1;
      padding: 0.6rem 0.75rem;
      border: 1px solid var(--color-border, #d0d5dd);
      border-radius: 8px;
      font-size: 0.9rem;
      background: var(--color-surface, #fff);
      color: var(--color-text, #1a1a2e);
      transition: border-color 0.15s;
    }
    .paczkomat-picker__input:focus {
      outline: none;
      border-color: var(--color-primary, #3b82f6);
      box-shadow: 0 0 0 3px rgba(59,130,246,0.15);
    }

    .paczkomat-picker__locate-btn {
      padding: 0.6rem 0.85rem;
      border: 1px solid var(--color-border, #d0d5dd);
      border-radius: 8px;
      background: var(--color-surface, #fff);
      cursor: pointer;
      font-size: 1.1rem;
      display: flex; align-items: center; justify-content: center;
      transition: background 0.15s, border-color 0.15s;
      min-width: 44px;
    }
    .paczkomat-picker__locate-btn:hover:not(:disabled) {
      background: var(--color-surface-alt, #f3f4f6);
      border-color: var(--color-primary, #3b82f6);
    }
    .paczkomat-picker__locate-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .paczkomat-picker__map-container {
      width: 100%;
      height: 280px;
      border-radius: 10px;
      border: 1px solid var(--color-border, #d0d5dd);
      overflow: hidden;
      background: #e5e7eb;
    }

    .paczkomat-picker__list {
      list-style: none;
      padding: 0;
      margin: 0;
      max-height: 220px;
      overflow-y: auto;
      border: 1px solid var(--color-border, #d0d5dd);
      border-radius: 8px;
    }

    .paczkomat-picker__item {
      padding: 0.6rem 0.75rem;
      cursor: pointer;
      display: flex; flex-direction: column; gap: 0.1rem;
      border-bottom: 1px solid var(--color-border, #eee);
      transition: background 0.12s;
    }
    .paczkomat-picker__item:last-child { border-bottom: none; }
    .paczkomat-picker__item:hover { background: var(--color-surface-alt, #f3f4f6); }
    .paczkomat-picker__item:focus-visible {
      outline: 2px solid var(--color-primary, #3b82f6);
      outline-offset: -2px;
    }
    .paczkomat-picker__item--selected {
      background: rgba(59,130,246,0.1);
    }

    .paczkomat-picker__item-name {
      font-weight: 600; font-size: 0.85rem;
      color: var(--color-primary, #3b82f6);
    }
    .paczkomat-picker__item-addr { font-size: 0.82rem; color: var(--color-text-muted, #6b7280); }
    .paczkomat-picker__item-desc { font-size: 0.75rem; color: var(--color-text-muted, #9ca3af); font-style: italic; }

    .paczkomat-picker__loading {
      display: flex; align-items: center; gap: 0.5rem;
      color: var(--color-text-muted, #6b7280); font-size: 0.85rem;
    }

    .paczkomat-picker__spinner {
      display: inline-block; width: 16px; height: 16px;
      border: 2px solid var(--color-border, #d0d5dd);
      border-top-color: var(--color-primary, #3b82f6);
      border-radius: 50%;
      animation: paczkomat-spin 0.6s linear infinite;
    }
    @keyframes paczkomat-spin { to { transform: rotate(360deg); } }

    .paczkomat-picker__error {
      color: var(--color-danger, #ef4444); font-size: 0.82rem; margin: 0;
    }
    .paczkomat-picker__empty {
      color: var(--color-text-muted, #6b7280); font-size: 0.85rem;
      text-align: center; padding: 0.75rem;
    }

    :global(.paczkomat-marker--selected) { filter: hue-rotate(120deg) brightness(1.2); }
  `]
})
export class PaczkomatPickerComponent implements OnInit, OnDestroy {
  initialPaczkomat = input<string>('');
  paczkomatSelected = output<string>();

  private inpostService = inject(InpostService);

  searchQuery = signal('');
  points = signal<InpostPoint[]>([]);
  selectedPoint = signal<InpostPoint | null>(null);
  loading = signal(false);
  locating = signal(false);
  locationError = signal<string | null>(null);

  private mapEl = viewChild<ElementRef<HTMLElement>>('mapContainer');
  private map: L.Map | null = null;
  private markers: L.Marker[] = [];
  private searchSubject = new Subject<string>();
  private destroyed = false;

  constructor() {
    afterNextRender(() => this.initMap());
  }

  ngOnInit(): void {
    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.length < 2) return of([]);
        this.loading.set(true);
        return this.inpostService.searchByQuery(q, 15).pipe(
          catchError(() => {
            return this.inpostService.searchByCity(q, 15).pipe(
              catchError(() => of([]))
            );
          })
        );
      })
    ).subscribe(pts => {
      this.points.set(pts);
      this.loading.set(false);
      this.updateMarkers(pts);
      if (pts.length > 0) this.fitMapToPoints(pts);
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.searchSubject.complete();
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  private initMap(): void {
    const el = this.mapEl()?.nativeElement;
    if (!el || this.destroyed) return;

    this.map = L.map(el, { zoomControl: true }).setView([52.23, 21.01], 6);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19
    }).addTo(this.map);

    this.useMyLocation();
  }

  onSearchChange(value: string): void {
    this.searchQuery.set(value);
    this.searchSubject.next(value);
  }

  useMyLocation(): void {
    if (!navigator.geolocation) {
      this.locationError.set('Geolokalizacja niedostępna w tej przeglądarce.');
      return;
    }
    this.locating.set(true);
    this.locationError.set(null);

    navigator.geolocation.getCurrentPosition(
      pos => {
        this.locating.set(false);
        const { latitude, longitude } = pos.coords;
        this.loading.set(true);
        this.inpostService.searchByLocation(latitude, longitude, 15).subscribe({
          next: pts => {
            this.points.set(pts);
            this.loading.set(false);
            this.updateMarkers(pts);
            if (this.map) {
              this.map.setView([latitude, longitude], 14);
            }
            if (pts.length > 0) this.fitMapToPoints(pts);
          },
          error: () => this.loading.set(false)
        });
      },
      err => {
        this.locating.set(false);
        switch (err.code) {
          case err.PERMISSION_DENIED:
            this.locationError.set('Odmówiono dostępu do lokalizacji. Wyszukaj ręcznie.');
            break;
          case err.POSITION_UNAVAILABLE:
            this.locationError.set('Lokalizacja niedostępna.');
            break;
          default:
            this.locationError.set('Nie udało się pobrać lokalizacji.');
        }
      },
      { enableHighAccuracy: false, timeout: 10000 }
    );
  }

  selectPoint(point: InpostPoint): void {
    this.selectedPoint.set(point);
    this.paczkomatSelected.emit(point.name);

    if (this.map) {
      this.map.setView([point.location.latitude, point.location.longitude], 16);
    }
    this.updateMarkerStyles();
  }

  private updateMarkers(pts: InpostPoint[]): void {
    if (!this.map) return;
    this.markers.forEach(m => m.remove());
    this.markers = [];

    for (const pt of pts) {
      const isSelected = this.selectedPoint()?.name === pt.name;
      const marker = L.marker(
        [pt.location.latitude, pt.location.longitude],
        { icon: isSelected ? SELECTED_ICON : MARKER_ICON }
      ).addTo(this.map);

      marker.bindPopup(
        `<strong>${pt.name}</strong><br>${pt.address_details.street} ${pt.address_details.building_number}<br>${pt.address_details.city}`
      );

      marker.on('click', () => this.selectPoint(pt));
      this.markers.push(marker);
    }
  }

  private updateMarkerStyles(): void {
    const pts = this.points();
    this.markers.forEach((marker, i) => {
      const isSelected = pts[i] && this.selectedPoint()?.name === pts[i].name;
      marker.setIcon(isSelected ? SELECTED_ICON : MARKER_ICON);
    });
  }

  private fitMapToPoints(pts: InpostPoint[]): void {
    if (!this.map || pts.length === 0) return;
    const bounds = L.latLngBounds(
      pts.map(p => [p.location.latitude, p.location.longitude] as L.LatLngTuple)
    );
    this.map.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 });
  }
}
