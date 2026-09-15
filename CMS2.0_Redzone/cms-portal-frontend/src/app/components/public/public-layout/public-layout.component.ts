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
  userMenuOpen = false;
  activeTheme = 'blue';
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

  toggleAccessibilityPanel() {
    document.body.classList.toggle('accessibility-enhanced');
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

  getMaskedPhone(): string {
    const phone = this.authService.userIdentifier() || '';
    if (phone.length >= 4) {
      return '*'.repeat(phone.length - 4) + phone.slice(-4);
    }
    return phone;
  }

  getUserInitials(): string {
    const name = this.getUserName();
    if (name && name.length >= 2) {
      const parts = name.split(' ');
      return parts.length > 1
        ? (parts[0][0] + parts[1][0]).toUpperCase()
        : name.slice(0, 2).toUpperCase();
    }
    const phone = this.authService.userIdentifier() || '';
    return phone.slice(-2).toUpperCase();
  }

  getUserName(): string {
    return (this.authService as any).userName?.() || 'User';
  }

  getUserEmail(): string {
    return (this.authService as any).userEmail?.() || '';
  }

  getLastSignedIn(): string {
    const now = new Date();
    const dd = String(now.getDate()).padStart(2, '0');
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const yy = String(now.getFullYear()).slice(-2);
    const hh = String(now.getHours()).padStart(2, '0');
    const min = String(now.getMinutes()).padStart(2, '0');
    const ss = String(now.getSeconds()).padStart(2, '0');
    return `${dd}/${mm}/${yy} ${hh}:${min}:${ss}`;
  }

  setTheme(theme: string) {
    this.activeTheme = theme;
  }
}
