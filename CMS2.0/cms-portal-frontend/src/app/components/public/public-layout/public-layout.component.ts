import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, Router } from '@angular/router';
import { PublicAuthService } from '../../../services/public-auth.service';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  templateUrl: './public-layout.component.html',
  styleUrl: './public-layout.component.scss'
})
export class PublicLayoutComponent {
  private router = inject(Router);
  authService = inject(PublicAuthService);

  mobileMenuOpen = false;
  fontSize = 16;
  currentLang = 'en';

  logout() {
    this.authService.logout();
    this.router.navigate(['/public']);
  }

  // FR-G-035: Multi-language support
  changeLanguage(event: Event) {
    const select = event.target as HTMLSelectElement;
    this.currentLang = select.value;
    document.documentElement.setAttribute('lang', this.currentLang);
  }

  increaseFontSize() {
    this.fontSize = Math.min(this.fontSize + 2, 24);
    document.documentElement.style.fontSize = this.fontSize + 'px';
  }

  decreaseFontSize() {
    this.fontSize = Math.max(this.fontSize - 2, 12);
    document.documentElement.style.fontSize = this.fontSize + 'px';
  }

  resetFontSize() {
    this.fontSize = 16;
    document.documentElement.style.fontSize = '16px';
  }
}
