import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-language-select',
  standalone: true,
  imports: [FormsModule, SelectModule],
  template: `
    <p-select
      [options]="translationService.locales()"
      [ngModel]="translationService.currentLocale()"
      (ngModelChange)="translationService.setLocale($event)"
      optionLabel="nativeName"
      optionValue="code"
      [fluid]="false"
      styleClass="lang-dropdown"
      [dt]="{
        border: { color: 'transparent', hoverColor: 'transparent', focusColor: 'transparent', activeColor: 'transparent' },
        shadow: 'none',
        focusRing: { shadow: 'none' }
      }">
      <ng-template pTemplate="selectedItem" let-selected>
        <div class="lang-selected">
          <i class="pi pi-globe"></i>
          <span>{{ selected?.nativeName }}</span>
        </div>
      </ng-template>
      <ng-template pTemplate="item" let-locale>
        <span>{{ locale.nativeName }}</span>
      </ng-template>
    </p-select>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
    }
    .lang-selected {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.8125rem;
      color: var(--p-text-color);
    }
    .lang-selected i {
      font-size: 0.875rem;
      color: var(--p-text-muted-color);
    }
  `]
})
export class LanguageSelectComponent {
  protected translationService = inject(TranslationService);
}
