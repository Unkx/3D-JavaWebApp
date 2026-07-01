import { TestBed } from '@angular/core/testing';
import { HttpRequest } from '@angular/common/http';
import { of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let authStub: { getToken: ReturnType<typeof vi.fn> };
  let next: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    authStub = { getToken: vi.fn() };
    next = vi.fn((req: HttpRequest<unknown>) => of(req));
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authStub }]
    });
  });

  function run(req: HttpRequest<unknown>) {
    return TestBed.runInInjectionContext(() => authInterceptor(req, next as never));
  }

  it('adds an Authorization header when a token is present', () => {
    authStub.getToken.mockReturnValue('tok-123');
    const req = new HttpRequest('GET', '/api/listings');

    run(req);

    expect(next).toHaveBeenCalledTimes(1);
    const forwarded = next.mock.calls[0][0] as HttpRequest<unknown>;
    expect(forwarded.headers.get('Authorization')).toBe('Bearer tok-123');
  });

  it('passes the request through unmodified when there is no token', () => {
    authStub.getToken.mockReturnValue(null);
    const req = new HttpRequest('GET', '/api/listings');

    run(req);

    expect(next).toHaveBeenCalledTimes(1);
    const forwarded = next.mock.calls[0][0] as HttpRequest<unknown>;
    expect(forwarded).toBe(req);
    expect(forwarded.headers.has('Authorization')).toBe(false);
  });
});
