import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { AvatarModule } from 'primeng/avatar';
import { PopoverModule } from 'primeng/popover';
import { PrimeNG } from 'primeng/config';

import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';

export interface LoggedInUser {
  id: string;
  name: string;
  role: string;
}

@Component({
  selector: 'app-rbio-header',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    SelectModule,
    AvatarModule,
    PopoverModule,
    NotificationBellComponent
  ],
  templateUrl: './rbio-header.component.html',
  styleUrl: './rbio-header.component.scss',
})
export class RbioHeaderComponent implements OnInit {
  private auth = inject(KeycloakAuthService);
  private primeng = inject(PrimeNG);

  loggedInUser = signal<LoggedInUser | null>(null);
  isDarkMode = signal<boolean>(false);
  fontSizeIndex = signal<number>(0);
  selectedLanguage = signal<string>('en');

  userInitial = computed(() => {
    const user = this.loggedInUser();
    return user?.name ? user.name.charAt(0).toUpperCase() : 'R';
  });

  languages = [
    { label: 'English', code: 'en' },
    { label: 'हिंदी (Hindi)', code: 'hi' },
    { label: 'தமிழ் (Tamil)', code: 'ta' },
    { label: 'ಕನ್ನಡ (Kannada)', code: 'kn' },
    { label: 'తెలుగు (Telugu)', code: 'te' }
  ];

  ngOnInit(): void {
    const htmlElement = document.documentElement;
    this.isDarkMode.set(htmlElement.classList.contains('my-app-dark'));

    const stored = sessionStorage.getItem('rbio_user');
    if (stored) {
      this.loggedInUser.set(JSON.parse(stored));
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r =>
          ['RBIO_DO', 'RBIO_REVIEWER', 'RBIO_OMBUDSMAN', 'RBIO_DEPUTY_OMBUDSMAN'].includes(r)
        ) || 'RBIO_DO';

        const createdUser: LoggedInUser = {
          id: user.username,
          name: `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username,
          role
        };

        this.loggedInUser.set(createdUser);
        sessionStorage.setItem('rbio_user', JSON.stringify(createdUser));
      }
    }
  }

  increaseFontSize(): void {
    this.fontSizeIndex.update(index => Math.min(index + 1, 1));
    this.updateFontScale();
  }

  decreaseFontSize(): void {
    this.fontSizeIndex.update(index => Math.max(index - 1, -1));
    this.updateFontScale();
  }

  resetFontSize(): void {
    this.fontSizeIndex.set(0);
    this.updateFontScale();
  }

  private updateFontScale(): void {
    const scales: Record<number, string> = {
      [-1]: '0.625rem',
      [0]: '0.75rem',
      [1]: '0.875rem'
    };

    const currentScale = scales[this.fontSizeIndex()];
    document.documentElement.style.setProperty('--app-text-scale', currentScale);
  }

  toggleDarkMode(): void {
    const element = document.documentElement;
    const nextDarkState = !this.isDarkMode();

    element.classList.toggle('my-app-dark', nextDarkState);
    this.isDarkMode.set(nextDarkState);

    this.primeng.theme.set({
      options: {
        darkModeSelector: '.my-app-dark'
      }
    });
  }

  onLanguageChange(event: { value: string }): void {
    this.selectedLanguage.set(event.value);
  }

  hideCmsLogo(event: Event): void {
    (event.target as HTMLElement).style.display = 'none';
  }

  logout(): void {
    sessionStorage.removeItem('rbio_user');
    this.auth.logout();
  }
}
