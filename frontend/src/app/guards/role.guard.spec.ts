import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { adminGuard, userOnlyGuard } from './role.guard';
import { AuthService } from '../services/auth.service';

describe('role guards', () => {
  let authStub: { isLoggedIn: ReturnType<typeof vi.fn>; isAdmin: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authStub = { isLoggedIn: vi.fn(), isAdmin: vi.fn() };
    router = {
      createUrlTree: vi.fn((commands: unknown[]) => ({ commands }) as unknown as UrlTree)
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authStub },
        { provide: Router, useValue: router }
      ]
    });
  });

  function runAdmin() {
    return TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
  }

  function runUserOnly() {
    return TestBed.runInInjectionContext(() => userOnlyGuard({} as never, {} as never));
  }

  describe('adminGuard', () => {
    it('redirects to /logowanie when logged out', () => {
      authStub.isLoggedIn.mockReturnValue(false);
      runAdmin();
      expect(router.createUrlTree).toHaveBeenCalledWith(['/logowanie']);
    });

    it('allows navigation for an admin', () => {
      authStub.isLoggedIn.mockReturnValue(true);
      authStub.isAdmin.mockReturnValue(true);
      expect(runAdmin()).toBe(true);
    });

    it('redirects a logged-in non-admin to /profil', () => {
      authStub.isLoggedIn.mockReturnValue(true);
      authStub.isAdmin.mockReturnValue(false);
      runAdmin();
      expect(router.createUrlTree).toHaveBeenCalledWith(['/profil']);
    });
  });

  describe('userOnlyGuard', () => {
    it('redirects to /logowanie when logged out', () => {
      authStub.isLoggedIn.mockReturnValue(false);
      runUserOnly();
      expect(router.createUrlTree).toHaveBeenCalledWith(['/logowanie']);
    });

    it('allows navigation for a non-admin user', () => {
      authStub.isLoggedIn.mockReturnValue(true);
      authStub.isAdmin.mockReturnValue(false);
      expect(runUserOnly()).toBe(true);
    });

    it('redirects an admin to /admin', () => {
      authStub.isLoggedIn.mockReturnValue(true);
      authStub.isAdmin.mockReturnValue(true);
      runUserOnly();
      expect(router.createUrlTree).toHaveBeenCalledWith(['/admin']);
    });
  });
});
