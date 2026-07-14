import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
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
});
