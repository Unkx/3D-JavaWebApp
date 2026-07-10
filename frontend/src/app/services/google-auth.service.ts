import { Injectable } from '@angular/core';

// Replace with the real OAuth Client ID from console.cloud.google.com — it is public by design
// (every Google Identity Services integration embeds it client-side).
const GOOGLE_CLIENT_ID = '93361708845-vsatnhot5fn9h171uuaqeriborbh0lph.apps.googleusercontent.com';

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleAccountsId {
  initialize(config: { client_id: string; callback: (response: GoogleCredentialResponse) => void }): void;
  renderButton(container: HTMLElement, options: { theme: string; size: string; type: string; width: number }): void;
}

declare const google: { accounts: { id: GoogleAccountsId } };

@Injectable({ providedIn: 'root' })
export class GoogleAuthService {
  private sdkReady: Promise<void> | null = null;
  private tokenCallback: ((idToken: string) => void) | null = null;

  renderButton(containerId: string, onToken: (idToken: string) => void): Promise<void> {
    this.tokenCallback = onToken;
    return this.loadSdk().then(() => {
      const container = document.getElementById(containerId);
      if (!container) return;
      // Google's button takes a fixed pixel width (no percentage support), so measure the
      // container to match the full-width Facebook button rendered right below it.
      google.accounts.id.renderButton(container, { theme: 'outline', size: 'large', type: 'standard', width: container.clientWidth });
    });
  }

  private loadSdk(): Promise<void> {
    if (this.sdkReady) return this.sdkReady;

    this.sdkReady = new Promise((resolve) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.onload = () => {
        google.accounts.id.initialize({
          client_id: GOOGLE_CLIENT_ID,
          callback: (response) => this.tokenCallback?.(response.credential)
        });
        resolve();
      };
      document.body.appendChild(script);
    });

    return this.sdkReady;
  }
}
