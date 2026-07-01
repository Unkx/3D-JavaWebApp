import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RevealDirective } from './reveal.directive';

@Component({
  selector: 'app-reveal-host',
  imports: [RevealDirective],
  template: `<div appReveal>content</div>`
})
class RevealHostComponent {}

describe('RevealDirective', () => {
  let observedCallback: ((entries: Partial<IntersectionObserverEntry>[]) => void) | null;
  let disconnectSpy: ReturnType<typeof vi.fn<() => void>>;
  let observeSpy: ReturnType<typeof vi.fn<(target: Element) => void>>;
  let matchMediaMatches = false;

  beforeEach(() => {
    observedCallback = null;
    disconnectSpy = vi.fn();
    observeSpy = vi.fn();

    class FakeIntersectionObserver {
      constructor(cb: (entries: Partial<IntersectionObserverEntry>[]) => void) {
        observedCallback = cb;
      }
      observe(target: Element) { observeSpy(target); }
      disconnect() { disconnectSpy(); }
    }
    (globalThis as unknown as { IntersectionObserver: unknown }).IntersectionObserver = FakeIntersectionObserver;

    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: matchMediaMatches,
        media: query,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    TestBed.configureTestingModule({ imports: [RevealHostComponent] });
  });

  it('is not revealed initially and observes the host element', async () => {
    matchMediaMatches = false;
    const fixture = TestBed.createComponent(RevealHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const div = fixture.nativeElement.querySelector('div') as HTMLElement;
    expect(div.classList.contains('scroll-reveal')).toBe(true);
    expect(div.classList.contains('scroll-revealed')).toBe(false);
    expect(observeSpy).toHaveBeenCalledWith(div);
  });

  it('reveals the element and disconnects the observer once it intersects', async () => {
    matchMediaMatches = false;
    const fixture = TestBed.createComponent(RevealHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    observedCallback!([{ isIntersecting: true }]);
    fixture.detectChanges();

    const div = fixture.nativeElement.querySelector('div') as HTMLElement;
    expect(div.classList.contains('scroll-revealed')).toBe(true);
    expect(div.classList.contains('scroll-reveal')).toBe(false);
    expect(disconnectSpy).toHaveBeenCalled();
  });

  it('does not reveal when the intersection entry is not intersecting', async () => {
    matchMediaMatches = false;
    const fixture = TestBed.createComponent(RevealHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    observedCallback!([{ isIntersecting: false }]);
    fixture.detectChanges();

    const div = fixture.nativeElement.querySelector('div') as HTMLElement;
    expect(div.classList.contains('scroll-revealed')).toBe(false);
    expect(disconnectSpy).not.toHaveBeenCalled();
  });

  it('reveals immediately without observing when reduced motion is preferred', async () => {
    matchMediaMatches = true;
    const fixture = TestBed.createComponent(RevealHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const div = fixture.nativeElement.querySelector('div') as HTMLElement;
    expect(div.classList.contains('scroll-revealed')).toBe(true);
    expect(observeSpy).not.toHaveBeenCalled();
  });
});
