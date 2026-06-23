import { Directive, ElementRef, inject, OnDestroy, signal } from '@angular/core';
import { afterNextRender } from '@angular/core';

@Directive({
  selector: '[appReveal]',
  host: {
    '[class.scroll-reveal]': '!isRevealed()',
    '[class.scroll-revealed]': 'isRevealed()',
  }
})
export class RevealDirective implements OnDestroy {
  isRevealed = signal(false);
  private observer: IntersectionObserver | null = null;

  constructor() {
    const el = inject(ElementRef);

    afterNextRender(() => {
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        this.isRevealed.set(true);
        return;
      }

      this.observer = new IntersectionObserver(
        ([entry]) => {
          if (entry.isIntersecting) {
            this.isRevealed.set(true);
            this.observer?.disconnect();
          }
        },
        { threshold: 0.1 }
      );
      this.observer.observe(el.nativeElement);
    });
  }

  ngOnDestroy() {
    this.observer?.disconnect();
  }
}
