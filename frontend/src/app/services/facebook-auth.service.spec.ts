import { TestBed } from '@angular/core/testing';
import { FacebookAuthService } from './facebook-auth.service';

describe('FacebookAuthService', () => {
  let service: FacebookAuthService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    service = TestBed.inject(FacebookAuthService);
    document.querySelectorAll('script[src*="connect.facebook.net"]').forEach(s => s.remove());
  });

  afterEach(() => {
    delete (window as any).FB;
    delete (window as any).fbAsyncInit;
  });

  it('resolves the access token when the user completes Facebook login', async () => {
    (window as any).FB = {
      init: vi.fn(),
      login: (cb: (r: unknown) => void) => cb({ authResponse: { accessToken: 'fb-token-123' } })
    };

    const loginPromise = service.login();
    (window as any).fbAsyncInit();

    await expect(loginPromise).resolves.toBe('fb-token-123');
  });

  it('resolves null when the user cancels the Facebook popup', async () => {
    (window as any).FB = {
      init: vi.fn(),
      login: (cb: (r: unknown) => void) => cb({})
    };

    const loginPromise = service.login();
    (window as any).fbAsyncInit();

    await expect(loginPromise).resolves.toBeNull();
  });

  it('only appends the Facebook SDK script once across repeated logins', async () => {
    (window as any).FB = {
      init: vi.fn(),
      login: (cb: (r: unknown) => void) => cb({ authResponse: { accessToken: 't1' } })
    };

    const first = service.login();
    (window as any).fbAsyncInit();
    await first;

    await service.login();

    expect(document.querySelectorAll('script[src*="connect.facebook.net"]').length).toBe(1);
  });
});
