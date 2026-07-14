import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AdminPanelComponent } from './admin-panel.component';
import { AuthService } from '../../services/auth.service';

describe('AdminPanelComponent', () => {
  let httpMock: HttpTestingController;
  let authStub: {
    listAdminCodes: ReturnType<typeof vi.fn>;
    generateAdminCode: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authStub = {
      listAdminCodes: vi.fn(),
      generateAdminCode: vi.fn(),
      logout: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub }
      ]
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('suspendUser() calls the suspend endpoint and updates the row', () => {
    const fixture = TestBed.createComponent(AdminPanelComponent);
    const component = fixture.componentInstance;
    component.users.set([
      { id: 'u1', email: 'a@test.local', role: 'USER', firstName: null, lastName: null, createdAt: '2026-01-01', suspended: false }
    ]);

    component.suspendUser('u1');
    const req = httpMock.expectOne('/api/admin/users/u1/suspend');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'u1', email: 'a@test.local', role: 'USER', firstName: null, lastName: null, createdAt: '2026-01-01', suspended: true });

    expect(component.users()[0].suspended).toBe(true);
  });

  it('unsuspendUser() calls the unsuspend endpoint and updates the row', () => {
    const fixture = TestBed.createComponent(AdminPanelComponent);
    const component = fixture.componentInstance;
    component.users.set([
      { id: 'u1', email: 'a@test.local', role: 'USER', firstName: null, lastName: null, createdAt: '2026-01-01', suspended: true }
    ]);

    component.unsuspendUser('u1');
    const req = httpMock.expectOne('/api/admin/users/u1/unsuspend');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'u1', email: 'a@test.local', role: 'USER', firstName: null, lastName: null, createdAt: '2026-01-01', suspended: false });

    expect(component.users()[0].suspended).toBe(false);
  });

  it('hideListing() calls the hide endpoint and updates the row', () => {
    const fixture = TestBed.createComponent(AdminPanelComponent);
    const component = fixture.componentInstance;
    component.listings.set([
      { id: 'l1', title: 'Test', status: 'OPEN', createdAt: '2026-01-01', ownerEmail: 'a@test.local', ownerFirstName: null, ownerLastName: null, maxBudget: null, moderationStatus: 'VISIBLE' }
    ]);

    component.hideListing('l1');
    const req = httpMock.expectOne('/api/admin/listings/l1/hide');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'l1', title: 'Test', status: 'OPEN', createdAt: '2026-01-01', ownerEmail: 'a@test.local', ownerFirstName: null, ownerLastName: null, maxBudget: null, moderationStatus: 'HIDDEN' });

    expect(component.listings()[0].moderationStatus).toBe('HIDDEN');
  });

  it('unhideListing() calls the unhide endpoint and updates the row', () => {
    const fixture = TestBed.createComponent(AdminPanelComponent);
    const component = fixture.componentInstance;
    component.listings.set([
      { id: 'l1', title: 'Test', status: 'OPEN', createdAt: '2026-01-01', ownerEmail: 'a@test.local', ownerFirstName: null, ownerLastName: null, maxBudget: null, moderationStatus: 'HIDDEN' }
    ]);

    component.unhideListing('l1');
    const req = httpMock.expectOne('/api/admin/listings/l1/unhide');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'l1', title: 'Test', status: 'OPEN', createdAt: '2026-01-01', ownerEmail: 'a@test.local', ownerFirstName: null, ownerLastName: null, maxBudget: null, moderationStatus: 'VISIBLE' });

    expect(component.listings()[0].moderationStatus).toBe('VISIBLE');
  });

  it('loads traffic summary on init and exposes it as a signal', () => {
    authStub.listAdminCodes.mockReturnValue(of([]));

    const fixture = TestBed.createComponent(AdminPanelComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me').flush({
      id: 'u1', email: 'admin@test.local', role: 'ADMIN', createdAt: '2026-01-01',
      listingsCount: 0, offersCount: 0, firstName: null, lastName: null,
      phone: null, gender: null, bio: null, dateOfBirth: null,
      street: null, houseNumber: null, city: null, postalCode: null
    });
    httpMock.expectOne('/api/admin/listings').flush([]);
    httpMock.expectOne('/api/admin/users').flush([]);

    const trafficReq = httpMock.expectOne('/api/admin/traffic');
    trafficReq.flush({
      pageViewsByDay: [{ date: '2026-07-14', count: 5 }],
      topPaths: [{ path: '/', count: 3 }],
      apiStats: { totalRequests: 10, errorCount: 1, avgDurationMs: 42.5 }
    });
    httpMock.expectOne('/api/admin/revenue').flush({
      byDay: [],
      totalPlatformFee: 0,
      totalVolume: 0,
      paidCount: 0,
      pendingCount: 0
    });
    httpMock.expectOne('/api/admin/audit-log').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true
    });

    expect(fixture.componentInstance.traffic()?.apiStats.totalRequests).toBe(10);
  });

  it('loads revenue summary on init', () => {
    authStub.listAdminCodes.mockReturnValue(of([]));

    const fixture = TestBed.createComponent(AdminPanelComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me').flush({
      id: 'u1', email: 'admin@test.local', role: 'ADMIN', createdAt: '2026-01-01',
      listingsCount: 0, offersCount: 0, firstName: null, lastName: null,
      phone: null, gender: null, bio: null, dateOfBirth: null,
      street: null, houseNumber: null, city: null, postalCode: null
    });
    httpMock.expectOne('/api/admin/listings').flush([]);
    httpMock.expectOne('/api/admin/users').flush([]);
    httpMock.expectOne('/api/admin/traffic').flush({
      pageViewsByDay: [],
      topPaths: [],
      apiStats: { totalRequests: 0, errorCount: 0, avgDurationMs: 0 }
    });

    const revenueReq = httpMock.expectOne(req => req.url === '/api/admin/revenue');
    revenueReq.flush({
      byDay: [{ date: '2026-07-14', platformFee: 15, totalVolume: 150 }],
      totalPlatformFee: 15,
      totalVolume: 150,
      paidCount: 2,
      pendingCount: 1
    });
    httpMock.expectOne('/api/admin/audit-log').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true
    });

    expect(fixture.componentInstance.revenue()?.totalPlatformFee).toBe(15);
  });

  it('loads the audit log on init', () => {
    authStub.listAdminCodes.mockReturnValue(of([]));

    const fixture = TestBed.createComponent(AdminPanelComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me').flush({
      id: 'u1', email: 'admin@test.local', role: 'ADMIN', createdAt: '2026-01-01',
      listingsCount: 0, offersCount: 0, firstName: null, lastName: null,
      phone: null, gender: null, bio: null, dateOfBirth: null,
      street: null, houseNumber: null, city: null, postalCode: null
    });
    httpMock.expectOne('/api/admin/listings').flush([]);
    httpMock.expectOne('/api/admin/users').flush([]);
    httpMock.expectOne('/api/admin/traffic').flush({
      pageViewsByDay: [],
      topPaths: [],
      apiStats: { totalRequests: 0, errorCount: 0, avgDurationMs: 0 }
    });
    httpMock.expectOne('/api/admin/revenue').flush({
      byDay: [],
      totalPlatformFee: 0,
      totalVolume: 0,
      paidCount: 0,
      pendingCount: 0
    });

    const auditReq = httpMock.expectOne(req => req.url === '/api/admin/audit-log');
    auditReq.flush({
      content: [{ id: 'a1', adminEmail: 'admin@test.local', actionType: 'HIDE_LISTING', targetType: 'Listing', targetId: 'l1', details: null, createdAt: '2026-07-14T10:00:00' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, last: true
    });

    expect(fixture.componentInstance.auditLog().length).toBe(1);
  });
});
