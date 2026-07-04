import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RevealDirective } from '../../directives/reveal.directive';

@Component({
  selector: 'app-privacy-policy',
  imports: [RevealDirective],
  templateUrl: './privacy-policy.component.html',
  styleUrl: './privacy-policy.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PrivacyPolicyComponent {}
