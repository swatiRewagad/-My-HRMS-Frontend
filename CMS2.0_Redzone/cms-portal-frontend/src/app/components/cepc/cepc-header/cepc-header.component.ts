import { Component, ElementRef, inject, OnInit, OnDestroy, AfterViewInit, signal, computed } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { PopoverModule } from 'primeng/popover';

import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { CepcContextService } from '../../../services/cepc-context.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { LanguageSelectComponent } from '../../../shared/language-select/language-select.component';
import { FontSizeControlsComponent } from '../../../shared/font-size-controls/font-size-controls.component';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-cepc-header',
  standalone: true,
  imports: [
    ButtonModule,
    AvatarModule,
    PopoverModule,
    NotificationBellComponent,
    LanguageSelectComponent,
    FontSizeControlsComponent,
    TranslateOrPipe
  ],
  templateUrl: './cepc-header.component.html',
  styleUrl: './cepc-header.component.scss',
})
export class CepcHeaderComponent implements OnInit, AfterViewInit, OnDestroy {
  private auth = inject(KeycloakAuthService);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly dept = inject(CepcContextService);

  private headerResizeObserver?: ResizeObserver;

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

  /**
   * The CEPC subtree root. Dark mode toggles here rather than on documentElement so it cannot
   * repaint the rest of the portal, which has its own `body.high-contrast` accessibility theme.
   */
  private darkModeRoot(): HTMLElement | null {
    return this.host.nativeElement.closest('.cepc-scope');
  }

  ngOnInit(): void {
    this.isDarkMode.set(this.darkModeRoot()?.classList.contains('cepc-dark') ?? false);
  }

  /**
   * The sidebar aligns itself against this measured height rather than a hard-coded rem value,
   * since the header's real rendered height shifts with the font-size/zoom control.
   */
  ngAfterViewInit(): void {
    this.publishHeaderHeight();
    this.headerResizeObserver = new ResizeObserver(() => this.publishHeaderHeight());
    this.headerResizeObserver.observe(this.host.nativeElement);
    document.addEventListener('cepc-text-scale-change', this.publishHeaderHeight);
  }

  ngOnDestroy(): void {
    this.headerResizeObserver?.disconnect();
    document.removeEventListener('cepc-text-scale-change', this.publishHeaderHeight);
  }

  // Zoom-only size changes don't trigger ResizeObserver, so FontSizeControlsComponent
  // dispatches 'cepc-text-scale-change' and we re-measure here too. Kept as a bound
  // arrow property so the same reference works for both add/removeEventListener.
  private publishHeaderHeight = (): void => {
    const height = this.host.nativeElement.getBoundingClientRect().height;
    document.documentElement.style.setProperty('--cepc-header-height', `${height}px`);
  };

  toggleDarkMode(): void {
    const nextDarkState = !this.isDarkMode();
    this.darkModeRoot()?.classList.toggle('cepc-dark', nextDarkState);
    this.isDarkMode.set(nextDarkState);
  }

  logout(): void {
    this.auth.logout();
  }
}
