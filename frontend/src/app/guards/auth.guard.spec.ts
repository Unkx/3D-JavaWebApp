import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authStub: { isLoggedIn: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn>; createUrlTree: ReturnType<typeof vi.fn>; url: string };

  beforeEach(() => {
    authStub = { isLoggedIn: vi.fn() };
    router = {
      navigate: vi.fn(),
      createUrlTree: vi.fn((commands: unknown[], extras?: unknown) => ({ commands, extras }) as unknown as UrlTree),
      url: '/zlecenia/1'
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authStub },
        { provide: Router, useValue: router }
      ]
    });
  });

  function run() {
    return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
  }

  it('allows navigation when the user is logged in', () => {
    authStub.isLoggedIn.mockReturnValue(true);
    expect(run()).toBe(true);
  });

  it('redirects to the login page with a returnUrl when logged out', () => {
    authStub.isLoggedIn.mockReturnValue(false);
    const result = run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/logowanie'], { queryParams: { returnUrl: '/zlecenia/1' } });
    expect(result).not.toBe(true);
  });
});
