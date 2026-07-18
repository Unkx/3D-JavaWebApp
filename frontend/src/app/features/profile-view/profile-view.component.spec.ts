import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfileViewComponent } from './profile-view.component';

describe('ProfileViewComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'user-1' }) } } }
      ]
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads and displays the public profile, ratings, and listings', () => {
    const fixture = TestBed.createComponent(ProfileViewComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/user-1/public-profile').flush({
      id: 'user-1', displayName: 'Jan K.', city: null, emailVerified: true,
      hasGoogleAuth: true, hasFacebookAuth: false, createdAt: '2026-01-01T00:00:00',
      lastLoginAt: '2026-07-18T08:00:00', hasAvatarData: false, avatarUrl: null,
      activeListingsCount: 2
    });
    httpMock.expectOne(req => req.url.startsWith('/api/users/user-1/ratings'))
      .flush({ summary: { averageStars: 4.5, count: 3 }, ratings: { content: [], page: 0, size: 20, totalElements: 3, totalPages: 1, last: true } });
    httpMock.expectOne(req => req.url.startsWith('/api/listings?userId=user-1'))
      .flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, last: true });

    expect(fixture.componentInstance.profile()?.displayName).toBe('Jan K.');
    expect(fixture.componentInstance.ratings()?.summary.averageStars).toBe(4.5);
  });

  it('shows a not-found state for an unknown user', () => {
    const fixture = TestBed.createComponent(ProfileViewComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/user-1/public-profile').flush(
      { message: 'Użytkownik nie istnieje' }, { status: 404, statusText: 'Not Found' }
    );

    expect(fixture.componentInstance.notFound()).toBe(true);
  });
});
