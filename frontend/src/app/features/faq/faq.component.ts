import { Component, ChangeDetectionStrategy, signal } from '@angular/core';
import { RevealDirective } from '../../directives/reveal.directive';

interface FaqItem {
  q: string;
  a: string;
}

interface FaqCategory {
  title: string;
  items: FaqItem[];
}

@Component({
  selector: 'app-faq',
  imports: [RevealDirective],
  templateUrl: './faq.component.html',
  styleUrl: './faq.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FaqComponent {
  readonly openKey = signal<string | null>(null);

  readonly categories: FaqCategory[] = [
    {
      title: 'Zlecenia',
      items: [
        {
          q: 'Jak dodać zlecenie druku 3D?',
          a: 'Zaloguj się, kliknij „Dodaj zlecenie" i uzupełnij tytuł, opis, materiał oraz budżet. Możesz dołączyć plik modelu (np. STL), żeby drukarze mogli dokładnie wycenić usługę.'
        },
        {
          q: 'Ile kosztuje wydruk?',
          a: 'Cena zależy od materiału, rozmiaru, jakości druku i czasu realizacji. Zamiast sztywnego cennika, otrzymujesz oferty od kilku drukarzy i wybierasz tę, która najbardziej Ci odpowiada.'
        },
        {
          q: 'Jak długo czeka się na ofertę?',
          a: 'Większość zleceń otrzymuje pierwsze oferty w ciągu kilku godzin. Średni czas realizacji całego zamówienia (od zgłoszenia do odbioru) to około 48 godzin.'
        }
      ]
    },
    {
      title: 'Płatności',
      items: [
        {
          q: 'Jak wygląda proces płatności?',
          a: 'Płatność następuje dopiero po zaakceptowaniu oferty wykonawcy, bezpośrednio w serwisie. Środki są zabezpieczone do momentu potwierdzenia odbioru wydruku.'
        },
        {
          q: 'Co jeśli wydruk mi się nie spodoba?',
          a: 'Skontaktuj się z wykonawcą przez wiadomości w serwisie, aby ustalić poprawki lub zwrot. Jeśli nie dojdziecie do porozumienia, zgłoś sprawę do administratora.'
        },
        {
          q: 'Czy mogę anulować zlecenie?',
          a: 'Tak, dopóki nie zaakceptowałeś żadnej oferty. Po akceptacji anulowanie wymaga zgody wykonawcy.'
        }
      ]
    },
    {
      title: 'Dla drukarzy',
      items: [
        {
          q: 'Jak zostać drukarzem na Druk3D?',
          a: 'Załóż konto, uzupełnij profil i zacznij przeglądać dostępne zlecenia w zakładce „Zlecenia". Rejestracja jest bezpłatna i nie wymaga weryfikacji sprzętu.'
        },
        {
          q: 'Jak i kiedy otrzymuję wypłatę?',
          a: 'Wypłata jest zwalniana po potwierdzeniu odbioru wydruku przez zleceniodawcę. Środki trafiają na Twoje konto w serwisie i możesz je wypłacić w dowolnym momencie.'
        },
        {
          q: 'Czy mogę odrzucić zlecenie?',
          a: 'Tak. Składanie ofert jest dobrowolne — przeglądasz dostępne zlecenia i odpowiadasz tylko na te, które Cię interesują.'
        }
      ]
    }
  ];

  toggle(key: string): void {
    this.openKey.update(current => (current === key ? null : key));
  }

  isOpen(key: string): boolean {
    return this.openKey() === key;
  }
}
