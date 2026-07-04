import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RevealDirective } from '../../directives/reveal.directive';

@Component({
  selector: 'app-terms-of-use',
  imports: [RevealDirective],
  templateUrl: './terms-of-use.component.html',
  styleUrl: './terms-of-use.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TermsOfUseComponent {}
