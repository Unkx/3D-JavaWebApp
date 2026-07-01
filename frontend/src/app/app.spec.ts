import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';
import { ConversationService } from './services/conversation.service';

describe('AppComponent', () => {
  let authStub: Pick<AuthService, 'isLoggedIn' | 'isAdmin' | 'currentUser' | 'logout'>;
  let themeStub: Pick<ThemeService, 'isDark' | 'toggle'>;
  let conversationStub: Pick<ConversationService, 'getUnreadCount'>;

  beforeEach(async () => {
    authStub = {
      isLoggedIn: (() => false) as AuthService['isLoggedIn'],
      isAdmin: (() => false) as AuthService['isAdmin'],
      currentUser: (() => null) as AuthService['currentUser'],
      logout: vi.fn()
    };
    themeStub = { isDark: (() => false) as ThemeService['isDark'], toggle: vi.fn() };
    conversationStub = { getUnreadCount: () => of({ count: 0 }) };

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: ThemeService, useValue: themeStub },
        { provide: ConversationService, useValue: conversationStub }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the brand title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.navbar__title')?.textContent).toContain('Druk3D');
  });

  it('toggleMenu()/closeMenu() flip the menuOpen signal', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.menuOpen()).toBe(false);
    app.toggleMenu();
    expect(app.menuOpen()).toBe(true);
    app.closeMenu();
    expect(app.menuOpen()).toBe(false);
  });
});
