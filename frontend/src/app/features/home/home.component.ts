import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RevealDirective } from '../../directives/reveal.directive';
import { IconComponent } from '../../components/icon.component';

@Component({
  selector: 'app-home',
  imports: [RouterLink, RevealDirective, IconComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HomeComponent {
  auth = inject(AuthService);

  readonly steps = [
    { icon: '📝', title: 'Opisz projekt', desc: 'Dodaj zlecenie: tytuł, opis, materiał i budżet.' },
    { icon: '🏷️', title: 'Otrzymaj oferty', desc: 'Drukarze przeglądają zlecenia i składają oferty z ceną.' },
    { icon: '✅', title: 'Wybierz wykonawcę', desc: 'Porównaj oferty i wybierz najlepszą.' },
    { icon: '📦', title: 'Odbierz wydruk', desc: 'Wykonawca drukuje i dostarcza gotowy element.' }
  ];

  readonly materials = [
    { name: 'PLA',   desc: 'Najtańszy, łatwy w druku, idealny do prototypów', price: 'od 0.15 zł/g' },
    { name: 'PETG',  desc: 'Wytrzymały i odporny na temperaturę', price: 'od 0.22 zł/g' },
    { name: 'ABS',   desc: 'Twardy, odporny na uderzenia', price: 'od 0.20 zł/g' },
    { name: 'RESIN', desc: 'Wysoka precyzja i gładka powierzchnia', price: 'od 0.45 zł/g' },
    { name: 'TPU',   desc: 'Elastyczny, gumopodobny filament', price: 'od 0.30 zł/g' },
    { name: 'ASA',   desc: 'Odporny na UV, do zastosowań zewnętrznych', price: 'od 0.25 zł/g' },
  ];
}
