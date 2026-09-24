import { Component, inject, computed, effect, signal } from '@angular/core';
import { RouterOutlet, RouterLink, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAuthService } from './services/keycloak-auth.service';
import { ToastHostComponent } from './components/shared/toast-host/toast-host.component';
import { SeoService } from './services/seo.service';
import { TranslationService } from './services/translation.service';
import { seoRouteFor } from './seo/seo-routes';
import { governmentOrganisation, webSiteWithSearch, breadcrumbs } from './seo/structured-data';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, ToastHostComponent],
  template: `
    <!-- Mounted once at the root so a single notification mechanism serves the public and staff
         routes alike. Components inject ToastService; nothing calls alert() any more. -->
    <app-toast-host/>
    @if (!isStaffRoute()) {
      <header class="app-header">
        <div class="header-content">
          <a routerLink="/" class="brand">
            <span class="brand-text">RBI CMS</span>
            <span class="brand-subtitle">Complaint Management System</span>
          </a>
          <nav class="header-nav">
            <a routerLink="/" class="nav-link">Home</a>
            <a routerLink="/track" class="nav-link">Track Complaint</a>
          </nav>
        </div>
      </header>
    }
    <main>
      <router-outlet/>
    </main>
    @if (!isStaffRoute()) {
      <footer class="app-footer">
        <p>© Reserve Bank of India | Integrated Ombudsman Scheme 2021</p>
      </footer>
    }

    <!-- Session Expiry Popup -->
    @if (auth.sessionExpiring()) {
      <div class="session-overlay">
        <div class="session-dialog">
          <div class="session-icon">&#9200;</div>
          <h3>Session Expiring</h3>
          <p>Your session will expire in <strong>{{ auth.sessionRemainingSeconds() }}</strong> seconds due to inactivity.</p>
          <p class="session-sub">Do you want to extend your session?</p>
          <div class="session-actions">
            <button class="btn-extend" (click)="auth.extendSession()">Extend Session</button>
            <button class="btn-logout" (click)="auth.logout()">Logout</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .app-header {
      background: #1a237e;
      padding: 0.8rem 2rem;
      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }
    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      max-width: 1200px;
      margin: 0 auto;
    }
    .brand {
      text-decoration: none;
      color: #fff;
      display: flex;
      flex-direction: column;
    }
    .brand-text { font-size: 1.4rem; font-weight: 700; }
    .brand-subtitle { font-size: 0.75rem; opacity: 0.8; }
    .header-nav { display: flex; gap: 1.5rem; }
    .nav-link {
      color: rgba(255,255,255,0.9);
      text-decoration: none;
      font-weight: 500;
      &:hover { color: #fff; }
    }
    main { min-height: calc(100vh - 120px); }
    .app-footer {
      background: #263238;
      color: rgba(255,255,255,0.7);
      text-align: center;
      padding: 1rem;
      font-size: 0.85rem;
    }
    .session-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
      backdrop-filter: blur(3px);
    }
    .session-dialog {
      background: white;
      border-radius: 12px;
      padding: 32px 40px;
      text-align: center;
      max-width: 420px;
      width: 90%;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
      animation: popIn 0.3s ease;
    }
    @keyframes popIn {
      from { opacity: 0; transform: scale(0.9); }
      to { opacity: 1; transform: scale(1); }
    }
    .session-icon { font-size: 48px; margin-bottom: 12px; }
    .session-dialog h3 { margin: 0 0 12px; font-size: 20px; color: #1e293b; }
    .session-dialog p { margin: 0 0 8px; font-size: 14px; color: #475569; }
    .session-dialog .session-sub { color: #64748b; font-size: 13px; margin-bottom: 20px; }
    .session-dialog strong { color: #dc2626; font-size: 18px; }
    .session-actions { display: flex; gap: 12px; justify-content: center; }
    .session-actions .btn-extend {
      padding: 10px 24px; background: #3b82f6; color: white; border: none;
      border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer;
    }
    .session-actions .btn-extend:hover { background: #2563eb; }
    .session-actions .btn-logout {
      padding: 10px 24px; background: white; color: #dc2626; border: 1px solid #fca5a5;
      border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer;
    }
    .session-actions .btn-logout:hover { background: #fef2f2; }
  `]
})
export class AppComponent {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);
  private seo = inject(SeoService);
  private translation = inject(TranslationService);

  /** Last URL navigated to, so the locale/bundle effect can re-apply metadata for it. */
  private currentUrl = signal('/');

  constructor() {
    // Per-route metadata, applied HERE rather than in each component. There were zero Title/Meta calls
    // anywhere, so all 84 routes shared one static title; doing it centrally means a new route cannot
    // silently ship without metadata, and an unlisted route defaults to noindex rather than to a
    // generic indexed page.
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(event => this.currentUrl.set(event.urlAfterRedirects));

    /**
     * Re-applies whenever the ROUTE, the LOCALE, or the BUNDLE LOAD STATE changes.
     *
     * <p>Reading `loading()` is not incidental — it is what fixes a real ordering bug. Titles and
     * descriptions are translation keys, and TranslationService returns the KEY ITSELF until its bundle
     * arrives. Applying metadata only on NavigationEnd therefore raced the bundle fetch and wrote
     * `<meta name="description" content="seo.home_description">` — a 20-character raw key — as the
     * snippet a search engine would show. Depending on the load signal means the tags are rewritten with
     * real prose the moment translations are available.
     */
    effect(() => {
      this.translation.currentLocale();
      this.translation.loading();
      this.applySeo(this.currentUrl());
    });
  }

  private applySeo(url: string): void {
    const path = url.split('?')[0].split('#')[0];
    const route = seoRouteFor(path);

    if (route) {
      this.seo.apply(route.titleKey, route.descriptionKey, route.path, route.indexable);
      this.applyStructuredData(route.path, route.indexable);
      return;
    }

    // FAIL CLOSED. Anything not in the SEO table — every staff module, and any per-record URL such as
    // /public/complaint/:id — gets noindex. A complaint detail URL in a search index would confirm that
    // a given reference number exists, so the default must be exclusion, never inclusion.
    this.seo.apply('seo.site_name', 'seo.home_description', path, false);
    this.applyStructuredData(path, false);
  }

  /**
   * Site-wide JSON-LD. Emitted only for indexable pages, and REMOVED otherwise.
   *
   * <p>Removal matters: these blocks live in <head>, so without clearing them a citizen navigating from
   * the FAQ into their own complaint history would carry FAQPage and breadcrumb markup onto a noindex
   * page, describing content that is not there.
   */
  private applyStructuredData(path: string, indexable: boolean): void {
    if (!indexable) {
      this.seo.clearJsonLd('organisation');
      this.seo.clearJsonLd('website');
      this.seo.clearJsonLd('breadcrumbs');
      return;
    }

    const origin = this.seo.canonicalOrigin();
    const siteName = this.translation.translate('seo.site_name');

    this.seo.setJsonLd('organisation', governmentOrganisation(
      origin, siteName, this.translation.translate('seo.home_description')));
    this.seo.setJsonLd('website', webSiteWithSearch(origin, siteName));

    // Home needs no breadcrumb: a single-item trail tells a crawler nothing it does not already know.
    if (path === '/public') {
      this.seo.clearJsonLd('breadcrumbs');
      return;
    }

    const route = seoRouteFor(path);
    this.seo.setJsonLd('breadcrumbs', breadcrumbs(origin, [
      { name: siteName, path: '/public' },
      { name: this.translation.translate(route?.titleKey ?? 'seo.site_name'), path },
    ]));
  }

  isStaffRoute() {
    const url = this.router.url;
    return url.startsWith('/crpc') || url.startsWith('/staff') || url.startsWith('/cepc') || url.startsWith('/public') || url.startsWith('/email-syndication') || url.startsWith('/rbio');
  }
}
