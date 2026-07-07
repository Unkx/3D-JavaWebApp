import { TestBed } from '@angular/core/testing';
import { GoogleAuthService } from './google-auth.service';

describe('GoogleAuthService', () => {
  let service: GoogleAuthService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    service = TestBed.inject(GoogleAuthService);
    document.querySelectorAll('script[src*="accounts.google.com/gsi/client"]').forEach(s => s.remove());
    document.querySelectorAll('#test-google-container').forEach(el => el.remove());
  });

  afterEach(() => {
    delete (window as any).google;
  });

  function simulateScriptLoad(): void {
    const script = document.querySelector('script[src*="accounts.google.com/gsi/client"]') as HTMLScriptElement;
    (script.onload as () => void)();
  }

  it('initializes with the client ID and forwards the credential to the token callback', async () => {
    const initialize = vi.fn();
    const renderButton = vi.fn();
    (window as any).google = { accounts: { id: { initialize, renderButton } } };

    const container = document.createElement('div');
    container.id = 'test-google-container';
    document.body.appendChild(container);

    const onToken = vi.fn();
    const renderPromise = service.renderButton('test-google-container', onToken);
    simulateScriptLoad();
    await renderPromise;

    expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ client_id: expect.any(String) }));
    expect(renderButton).toHaveBeenCalledWith(container, expect.objectContaining({ theme: 'outline' }));

    const registeredCallback = initialize.mock.calls[0][0].callback;
    registeredCallback({ credential: 'google-id-token-123' });
    expect(onToken).toHaveBeenCalledWith('google-id-token-123');
  });

  it('does nothing if the container element is missing', async () => {
    const initialize = vi.fn();
    const renderButton = vi.fn();
    (window as any).google = { accounts: { id: { initialize, renderButton } } };

    const renderPromise = service.renderButton('does-not-exist', vi.fn());
    simulateScriptLoad();
    await renderPromise;

    expect(renderButton).not.toHaveBeenCalled();
  });

  it('only appends the GIS script once across repeated renderButton calls', async () => {
    const initialize = vi.fn();
    const renderButton = vi.fn();
    (window as any).google = { accounts: { id: { initialize, renderButton } } };

    const container = document.createElement('div');
    container.id = 'test-google-container';
    document.body.appendChild(container);

    const first = service.renderButton('test-google-container', vi.fn());
    simulateScriptLoad();
    await first;

    await service.renderButton('test-google-container', vi.fn());

    expect(document.querySelectorAll('script[src*="accounts.google.com/gsi/client"]').length).toBe(1);
  });
});
