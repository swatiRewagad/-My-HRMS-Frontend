import { Injectable, signal, computed, inject, ApplicationRef } from '@angular/core';
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

  private translations = signal<Record<string, string>>({});
  private _currentLocale = signal<string>(this.getStoredLocale());
  private _locales = signal<LocaleInfo[]>([]);
  private _loading = signal(false);
  private _loaded = false;
  private _loadedLocale = '';

  readonly currentLocale = this._currentLocale.asReadonly();
  readonly locales = this._locales.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly isRtl = computed(() => {
    const locale = this._locales().find(l => l.code === this._currentLocale());
    return locale?.rtl ?? false;
  });

  constructor() {
    this.loadLocales();
    this.loadTranslations(this._currentLocale());
  }

  translate(key: string, params?: Record<string, string>): string {
    if (!this._loaded && !this._loading()) {
      this.loadTranslations(this._currentLocale());
    }
    let value = this.translations()[key] || key;
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        value = value.replace(`{{${k}}}`, v);
      });
    }
    return value;
  }

  async setLocale(locale: string): Promise<void> {
    if (locale === this._currentLocale() && locale === this._loadedLocale) return;
    localStorage.setItem(STORAGE_KEY, locale);
    this._currentLocale.set(locale);
    await this.loadTranslations(locale);
    this.applyDirection();
  }

  private async loadTranslations(locale: string): Promise<void> {
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
    return localStorage.getItem(STORAGE_KEY) || DEFAULT_LOCALE;
  }

  private applyDirection(): void {
    const dir = this.isRtl() ? 'rtl' : 'ltr';
    document.documentElement.setAttribute('dir', dir);
    document.documentElement.setAttribute('lang', this._currentLocale());
  }
}
