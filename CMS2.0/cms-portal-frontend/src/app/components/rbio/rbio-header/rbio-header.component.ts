import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { PopoverModule } from 'primeng/popover';
import { PrimeNG } from 'primeng/config';

import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { DepartmentContextService } from '../../../services/department-context.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { LanguageSelectComponent } from '../../../shared/language-select/language-select.component';
import { FontSizeControlsComponent } from '../../../shared/font-size-controls/font-size-controls.component';

@Component({
  selector: 'app-rbio-header',
  standalone: true,
  imports: [
    ButtonModule,
    AvatarModule,
    PopoverModule,
    NotificationBellComponent,
    LanguageSelectComponent,
    FontSizeControlsComponent
  ],
  templateUrl: './rbio-header.component.html',
  styleUrl: './rbio-header.component.scss',
})
export class RbioHeaderComponent implements OnInit {
  private auth = inject(KeycloakAuthService);
  private primeng = inject(PrimeNG);
  readonly dept = inject(DepartmentContextService);

  currentUser = this.auth.currentUser;
  isDarkMode = signal<boolean>(false);

  userInitial = computed(() => {
    const user = this.currentUser();
    return user?.firstName ? user.firstName.charAt(0).toUpperCase() : '?';
  });

  displayName = computed(() => {
    const user = this.currentUser();
    if (!user) return '';
    return `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username;
  });

  displayRole = computed(() => {
    if (!this.currentUser()) return '';
    return this.dept.primaryRole();
  });

  ngOnInit(): void {
    this.isDarkMode.set(document.documentElement.classList.contains('my-app-dark'));
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

  logout(): void {
    this.auth.logout();
  }
}
