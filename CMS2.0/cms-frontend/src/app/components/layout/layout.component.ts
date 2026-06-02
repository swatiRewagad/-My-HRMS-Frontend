import { Component, inject } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { HeaderComponent } from '../header/header.component';
import { FooterComponent } from '../footer/footer.component';
import { AccessibilityService } from '../../services/accessibility.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, FooterComponent],
  template: `
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <app-header />
    <main id="main-content" class="main-content" role="main" tabindex="-1">
      <router-outlet />
    </main>
    <app-footer />
    <div id="aria-live-region" class="sr-only" aria-live="polite" aria-atomic="true"></div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; }
    .main-content { flex: 1; outline: none; }
    .skip-link {
      position: absolute;
      top: -100%;
      left: 16px;
      z-index: 10000;
      background: #1a237e;
      color: #fff;
      padding: 12px 24px;
      border-radius: 0 0 8px 8px;
      font-weight: 600;
      text-decoration: none;
      transition: top 0.2s;
    }
    .skip-link:focus {
      top: 0;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
  `],
})
export class LayoutComponent {
  private router = inject(Router);
  private a11y = inject(AccessibilityService);

  constructor() {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => {
        const main = document.getElementById('main-content');
        if (main) {
          main.focus();
          window.scrollTo(0, 0);
        }
      });
  }
}
