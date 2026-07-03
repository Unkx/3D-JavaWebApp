import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotFoundComponent } from './not-found.component';

describe('NotFoundComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])]
    });
  });

  it('renders the failed-print headline and subtext', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.not-found__headline')?.textContent).toContain('Ten wydruk się nie udał.');
    expect(el.querySelector('.not-found__eyebrow')?.textContent).toContain('404');
  });

  it('links "Strona główna" to the home route', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/"]');
    expect(link?.textContent).toContain('Strona główna');
  });

  it('links "Przeglądaj zlecenia" to the listings route', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/zlecenia"]');
    expect(link?.textContent).toContain('Przeglądaj zlecenia');
  });

  it('offers a mailto report link to the admin address', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('.not-found__report');
    expect(link?.getAttribute('href')).toBe('mailto:admin@druk3d.pl?subject=Zg%C5%82oszenie%20problemu%20-%20404');
  });
});
