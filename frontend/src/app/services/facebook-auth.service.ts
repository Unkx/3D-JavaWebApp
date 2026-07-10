import { Injectable } from '@angular/core';

// Replace with the real App ID from developers.facebook.com — it is public by design
// (every Facebook JS SDK integration embeds it client-side).
const FACEBOOK_APP_ID = '1018372287267595';

declare global {
  interface Window {
    fbAsyncInit?: () => void;
  }
}

interface FacebookLoginResponse {
  authResponse?: { accessToken: string };
}

interface FacebookSdk {
  init(params: { appId: string; version: string; xfbml: boolean }): void;
  login(callback: (response: FacebookLoginResponse) => void, options: { scope: string }): void;
}

declare const FB: FacebookSdk;

@Injectable({ providedIn: 'root' })
export class FacebookAuthService {
  private sdkReady: Promise<void> | null = null;

  login(): Promise<string | null> {
    return this.loadSdk().then(() => this.doLogin());
  }

  private loadSdk(): Promise<void> {
    if (this.sdkReady) return this.sdkReady;

    this.sdkReady = new Promise((resolve) => {
      window.fbAsyncInit = () => {
        FB.init({ appId: FACEBOOK_APP_ID, version: 'v21.0', xfbml: false });
        resolve();
      };
      const script = document.createElement('script');
      script.src = 'https://connect.facebook.net/pl_PL/sdk.js';
      script.async = true;
      document.body.appendChild(script);
    });

    return this.sdkReady;
  }

  private doLogin(): Promise<string | null> {
    return new Promise((resolve) => {
      FB.login((response) => {
        resolve(response.authResponse ? response.authResponse.accessToken : null);
      }, { scope: 'email' });
    });
  }
}
