import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslationService, LocaleInfo } from '../../../services/translation.service';

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './language-switcher.component.html',
  styleUrls: ['./language-switcher.component.scss']
})
export class LanguageSwitcherComponent {

  protected translationService = inject(TranslationService);

  protected isOpen = false;

  get currentLocale(): string {
    return this.translationService.currentLocale();
  }

  get locales(): LocaleInfo[] {
    return this.translationService.locales();
  }

  get currentLocaleInfo(): LocaleInfo | undefined {
    return this.locales.find(l => l.code === this.currentLocale);
  }

  toggleDropdown(): void {
    this.isOpen = !this.isOpen;
  }

  selectLocale(locale: LocaleInfo): void {
    this.translationService.setLocale(locale.code);
    this.isOpen = false;
  }

  closeDropdown(): void {
    this.isOpen = false;
  }
}
