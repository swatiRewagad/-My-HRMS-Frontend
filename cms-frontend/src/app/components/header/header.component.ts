import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateService, Language } from '../../services/translate.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { AccessibilityService } from '../../services/accessibility.service';
import { TtsService } from '../../services/tts.service';
import { UserSessionService } from '../../services/user-session.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  mobileMenuOpen = false;
  backOfficeOpen = false;

  constructor(
    public translate: TranslateService,
    public a11y: AccessibilityService,
    public tts: TtsService,
    public userSession: UserSessionService,
  ) {}

  get languages() {
    return this.translate.languages;
  }

  switchLanguage(lang: Language) {
    this.translate.setLanguage(lang);
    const label = this.translate.languages.find(l => l.code === lang)?.label || lang;
    this.a11y.announce(`Language changed to ${label}`);
  }

  logout() {
    this.userSession.logout();
    window.location.href = '/';
  }

  @HostListener('document:keydown.escape')
  closeMobileMenu() {
    if (this.mobileMenuOpen) {
      this.mobileMenuOpen = false;
    }
  }
}
