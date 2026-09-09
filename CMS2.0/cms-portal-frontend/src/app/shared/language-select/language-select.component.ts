import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-language-select',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="lang-wrapper">
      <i class="pi pi-globe"></i>
      <select class="lang-select" (change)="changeLanguage($event)" [value]="translationService.currentLocale()" aria-label="Select language">
        @for (locale of translationService.locales(); track locale.code) {
          <option [value]="locale.code" [selected]="locale.code === translationService.currentLocale()">
            {{ locale.nativeName }}
          </option>
        }
      </select>
    </div>
  `,
  styles: [`
    .lang-wrapper {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 8px;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      background: #fff;
    }
    .lang-wrapper i {
      font-size: 14px;
      color: #6b7280;
    }
    .lang-select {
      border: none;
      background: transparent;
      font-size: 13px;
      color: #374151;
      cursor: pointer;
      outline: none;
      max-width: 110px;
    }
  `]
})
export class LanguageSelectComponent {
  protected translationService = inject(TranslationService);

  changeLanguage(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.translationService.setLocale(select.value);
  }
}
