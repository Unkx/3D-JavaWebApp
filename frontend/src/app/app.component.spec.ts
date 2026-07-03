import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';
import { ConversationService } from './services/conversation.service';

@Component({ template: '<p>home</p>' })
class BlankHomeComponent {}

@Component({ template: '<p>missing</p>' })
class BlankFullscreenComponent {}

describe('AppComponent', () => {
  let authStub: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    isAdmin: ReturnType<typeof vi.fn>;
    currentUser: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let conversationStub: { getUnreadCount: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authStub = {
      isLoggedIn: vi.fn().mockReturnValue(false),
      isAdmin: vi.fn().mockReturnValue(false),
      currentUser: vi.fn().mockReturnValue(null),
      logout: vi.fn()
    };
    conversationStub = { getUnreadCount: vi.fn().mockReturnValue(of({ count: 0 })) };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authStub },
        { provide: ConversationService, useValue: conversationStub },
        provideRouter([
          { path: '', component: BlankHomeComponent },
          { path: 'missing', data: { fullscreen: true }, component: BlankFullscreenComponent }
        ])
      ]
    });
  });

  it('shows the navbar and sidebar on a normal route', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('header.navbar')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('aside.sidebar')).not.toBeNull();
  });

  it('hides the navbar and sidebar on a route flagged data.fullscreen', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/missing');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('header.navbar')).toBeNull();
    expect(fixture.nativeElement.querySelector('aside.sidebar')).toBeNull();
  });
});
