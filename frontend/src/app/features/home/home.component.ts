import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RevealDirective } from '../../directives/reveal.directive';

@Component({
  selector: 'app-home',
  imports: [RouterLink, RevealDirective],
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
    { name: 'PLA',   desc: 'Najtańszy, łatwy w druku, idealny do prototypów' },
    { name: 'PETG',  desc: 'Wytrzymały i odporny na temperaturę' },
    { name: 'ABS',   desc: 'Twardy, odporny na uderzenia' },
    { name: 'RESIN', desc: 'Wysoka precyzja i gładka powierzchnia' },
    { name: 'TPU',   desc: 'Elastyczny, gumopodobny filament' },
    { name: 'ASA',   desc: 'Odporny na UV, do zastosowań zewnętrznych' },
  ];
}
