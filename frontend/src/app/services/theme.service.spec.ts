import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

function setMatchMedia(matches: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    setMatchMedia(false);
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it('uses the stored "dark" preference when present', () => {
    localStorage.setItem('druk3d-theme', 'dark');
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('uses the stored "light" preference when present', () => {
    localStorage.setItem('druk3d-theme', 'light');
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('falls back to prefers-color-scheme when nothing is stored', () => {
    setMatchMedia(true);
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
  });

  it('falls back to false preference when matchMedia reports light and nothing stored', () => {
    setMatchMedia(false);
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);
  });

  it('defaults to dark when matchMedia is unavailable and nothing stored', () => {
    Object.defineProperty(window, 'matchMedia', { writable: true, configurable: true, value: undefined });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
  });

  it('toggle() flips the signal, updates the DOM attribute, and persists the choice', () => {
    localStorage.setItem('druk3d-theme', 'light');
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);

    service.toggle();

    expect(service.isDark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('druk3d-theme')).toBe('dark');

    service.toggle();

    expect(service.isDark()).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem('druk3d-theme')).toBe('light');
  });
});
