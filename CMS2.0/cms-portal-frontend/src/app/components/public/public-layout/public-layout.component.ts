import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, Router } from '@angular/router';
import { PublicAuthService } from '../../../services/public-auth.service';
import { TranslationService } from '../../../services/translation.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, TranslatePipe],
  templateUrl: './public-layout.component.html',
  styleUrl: './public-layout.component.scss'
})
export class PublicLayoutComponent {
  private router = inject(Router);
  authService = inject(PublicAuthService);
  translationService = inject(TranslationService);

  mobileMenuOpen = false;
  fontSize = 16;
  highContrast = false;

  logout() {
    this.authService.logout();
    this.router.navigate(['/public']);
  }

  toggleContrast() {
    this.highContrast = !this.highContrast;
    document.body.classList.toggle('high-contrast', this.highContrast);
  }

  changeLanguage(event: Event) {
    const select = event.target as HTMLSelectElement;
    this.translationService.setLocale(select.value);
  }

  increaseFontSize() {
    this.fontSize = Math.min(this.fontSize + 2, 24);
    document.body.style.zoom = `${(this.fontSize / 16) * 100}%`;
  }

  decreaseFontSize() {
    this.fontSize = Math.max(this.fontSize - 2, 12);
    document.body.style.zoom = `${(this.fontSize / 16) * 100}%`;
  }

  resetFontSize() {
    this.fontSize = 16;
    document.body.style.zoom = '100%';
  }
}
