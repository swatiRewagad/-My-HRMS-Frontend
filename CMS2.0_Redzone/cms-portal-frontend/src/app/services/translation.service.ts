import { Injectable, signal, computed, inject, ApplicationRef, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LocaleInfo {
  code: string;
  name: string;
  nativeName: string;
  rtl: boolean;
}

const STORAGE_KEY = 'cms_locale';
const DEFAULT_LOCALE = 'en';

@Injectable({ providedIn: 'root' })
export class TranslationService {

  private http = inject(HttpClient);
  private appRef = inject(ApplicationRef);
  /**
   * localStorage and document do not exist during prerendering, and touching either throws before the
   * page can be rendered at all. Guarding on the platform lets the same service run in both places:
   * the prerendered HTML carries the default locale, and the browser corrects it on hydration.
   */
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private translations = signal<Record<string, string>>({});
  private _currentLocale = signal<string>(this.getStoredLocale());
  private _locales = signal<LocaleInfo[]>([]);
  private _loading = signal(false);
  private _loaded = false;
  private _loadedLocale = '';
  /**
   * Set the moment a load is kicked off, success or failure, and never cleared for the lazy path below.
   *
   * <p>Without this, a failed load leaves `_loaded` false forever, and every one of the dozens of impure
   * `translateOr` pipe calls on a screen re-checks `!this._loaded && !this._loading()` on every single
   * change-detection cycle. Each retrigger ends in `loadTranslations`'s `finally` calling `appRef.tick()`,
   * which re-runs every impure pipe again, which retriggers the load again — a self-sustaining request +
   * full-tree re-render storm that pegs the main thread on any transient failure. One attempt is enough;
   * `setLocale` bypasses this flag entirely and can always retry explicitly.
   */
  private _loadAttempted = false;

  readonly currentLocale = this._currentLocale.asReadonly();
  readonly locales = this._locales.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly isRtl = computed(() => {
    const locale = this._locales().find(l => l.code === this._currentLocale());
    return locale?.rtl ?? false;
  });

  constructor() {
    this.loadLocales();
  }

  translate(key: string, params?: Record<string, string>): string {
    if (!this._loadAttempted && !this._loading()) {
      this.loadTranslations(this._currentLocale());
    }
    return this.interpolate(this.translations()[key] || key, params);
  }

  /**
   * The translation for `key`, or `fallback` when nothing has seeded that key.
   *
   * <p>For screens whose English wording must not change when they are wired up to i18n. {@link #translate}
   * returns the key itself for an unseeded key, so a label that has not been seeded yet — a backend that has
   * not restarted since the seeder was added, a locale fetch that failed — renders `ui.cepc.foo` on screen
   * instead of its English. Passing the original English literal as `fallback` makes that case a no-op:
   * the screen reads exactly as it did before, and only a seeded locale changes it.
   *
   * <p>Absence is tested against the raw map rather than by comparing the result to `key`, so a translation
   * that legitimately equals its own key still wins over the fallback.
   */
  translateOr(key: string, fallback: string, params?: Record<string, string>): string {
    if (!this._loadAttempted && !this._loading()) {
      this.loadTranslations(this._currentLocale());
    }
    const seeded = this.translations()[key];
    return this.interpolate(seeded === undefined || seeded === '' ? fallback : seeded, params);
  }

  private interpolate(value: string, params?: Record<string, string>): string {
    if (!params) return value;
    let out = value;
    Object.entries(params).forEach(([k, v]) => {
      // Global, not String.replace with a string pattern: that substitutes only the FIRST
      // occurrence, and the RE-window prose names the window twice ("not yet been given {{days}}
      // days ... wait until {{days}} days have elapsed"), so a raw second placeholder reached the
      // citizen. The key is escaped because it is interpolated into a regex.
      out = out.replace(new RegExp(`\\{\\{${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\}\\}`, 'g'), v);
    });
    return out;
  }

  async setLocale(locale: string): Promise<void> {
    if (locale === this._currentLocale() && locale === this._loadedLocale) return;
    if (this.isBrowser) localStorage.setItem(STORAGE_KEY, locale);
    this._currentLocale.set(locale);
    await this.loadTranslations(locale);
    this.applyDirection();
  }

  private async loadTranslations(locale: string): Promise<void> {
    this._loadAttempted = true;
    this._loading.set(true);
    try {
      const data = await firstValueFrom(
        this.http.get<Record<string, string>>(`${environment.apiBaseUrl}/api/v1/i18n/translations/${locale}`)
      );
      if (data && Object.keys(data).length > 0) {
        this.translations.set(data);
        this._loaded = true;
        this._loadedLocale = locale;
      } else if (locale !== DEFAULT_LOCALE) {
        const fallback = await firstValueFrom(
          this.http.get<Record<string, string>>(`${environment.apiBaseUrl}/api/v1/i18n/translations/${DEFAULT_LOCALE}`)
        );
        this.translations.set(fallback || {});
        this._loaded = (fallback && Object.keys(fallback).length > 0) || false;
        this._loadedLocale = DEFAULT_LOCALE;
      } else {
        this._loaded = false;
        this._loadedLocale = '';
      }
    } catch {
      if (locale !== DEFAULT_LOCALE) {
        try {
          const fallback = await firstValueFrom(
            this.http.get<Record<string, string>>(`${environment.apiBaseUrl}/api/v1/i18n/translations/${DEFAULT_LOCALE}`)
          );
          this.translations.set(fallback || {});
          this._loaded = (fallback && Object.keys(fallback).length > 0) || false;
          this._loadedLocale = DEFAULT_LOCALE;
        } catch {
          this._loaded = false;
          this._loadedLocale = '';
        }
      } else {
        this._loaded = false;
        this._loadedLocale = '';
      }
    } finally {
      this._loading.set(false);
      try { this.appRef.tick(); } catch { /* ignore during bootstrap */ }
    }
  }

  private loadLocales(): void {
    this.http
      .get<LocaleInfo[]>(`${environment.apiBaseUrl}/api/v1/i18n/locales`)
      .subscribe({
        next: locales => this._locales.set(locales),
        error: () => this._locales.set([{ code: 'en', name: 'English', nativeName: 'English', rtl: false }])
      });
  }

  private getStoredLocale(): string {
    if (!this.isBrowser) return DEFAULT_LOCALE;
    return localStorage.getItem(STORAGE_KEY) || DEFAULT_LOCALE;
  }

  private applyDirection(): void {
    if (!this.isBrowser) return;
    const dir = this.isRtl() ? 'rtl' : 'ltr';
    document.documentElement.setAttribute('dir', dir);
    document.documentElement.setAttribute('lang', this._currentLocale());
  }
}
