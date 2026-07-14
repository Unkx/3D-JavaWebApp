import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserPanelComponent } from './user-panel.component';
import { AuthService } from '../../services/auth.service';

describe('UserPanelComponent', () => {
  let httpMock: HttpTestingController;
  let authStub: { redeemAdminCode: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authStub = { redeemAdminCode: vi.fn(), logout: vi.fn() };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } }
      ]
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the user\'s own ratings on init', () => {
    const fixture = TestBed.createComponent(UserPanelComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me').flush({ id: 'u1', email: 'a@test.local', role: 'USER', createdAt: '2026-01-01', listingsCount: 0, offersCount: 0, firstName: null, lastName: null, phone: null, gender: null, bio: null, dateOfBirth: null, street: null, houseNumber: null, city: null, postalCode: null });

    const req = httpMock.expectOne(req => req.url.startsWith('/api/users/u1/ratings'));
    req.flush({ summary: { averageStars: 4.5, count: 2 }, ratings: { content: [], page: 0, size: 20, totalElements: 2, totalPages: 1, last: true } });

    expect(fixture.componentInstance.ratings()?.summary.averageStars).toBe(4.5);
  });
});
