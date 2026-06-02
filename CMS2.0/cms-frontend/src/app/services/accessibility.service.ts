import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AccessibilityService {
  private _highContrast = signal(this.loadPreference('cms_high_contrast'));
  private _reducedMotion = signal(this.loadPreference('cms_reduced_motion'));
  private _fontSize = signal(this.loadFontSize());

  highContrast = this._highContrast.asReadonly();
  reducedMotion = this._reducedMotion.asReadonly();
  fontSize = this._fontSize.asReadonly();

  constructor() {
    this.applyHighContrast(this._highContrast());
    this.applyFontSize(this._fontSize());
    this.applyReducedMotion(this._reducedMotion());
  }

  toggleHighContrast() {
    const next = !this._highContrast();
    this._highContrast.set(next);
    localStorage.setItem('cms_high_contrast', String(next));
    this.applyHighContrast(next);
  }

  toggleReducedMotion() {
    const next = !this._reducedMotion();
    this._reducedMotion.set(next);
    localStorage.setItem('cms_reduced_motion', String(next));
    this.applyReducedMotion(next);
  }

  increaseFontSize() {
    const current = this._fontSize();
    if (current < 24) {
      const next = current + 2;
      this._fontSize.set(next);
      localStorage.setItem('cms_font_size', String(next));
      this.applyFontSize(next);
    }
  }

  decreaseFontSize() {
    const current = this._fontSize();
    if (current > 12) {
      const next = current - 2;
      this._fontSize.set(next);
      localStorage.setItem('cms_font_size', String(next));
      this.applyFontSize(next);
    }
  }

  resetFontSize() {
    this._fontSize.set(16);
    localStorage.setItem('cms_font_size', '16');
    this.applyFontSize(16);
  }

  announce(message: string) {
    const el = document.getElementById('aria-live-region');
    if (el) {
      el.textContent = '';
      setTimeout(() => { el.textContent = message; }, 100);
    }
  }

  private applyHighContrast(enabled: boolean) {
    document.documentElement.classList.toggle('high-contrast', enabled);
  }

  private applyReducedMotion(enabled: boolean) {
    document.documentElement.classList.toggle('reduced-motion', enabled);
  }

  private applyFontSize(size: number) {
    document.documentElement.style.fontSize = size + 'px';
  }

  private loadPreference(key: string): boolean {
    return localStorage.getItem(key) === 'true';
  }

  private loadFontSize(): number {
    const saved = localStorage.getItem('cms_font_size');
    return saved ? parseInt(saved, 10) : 16;
  }
}
